#!/usr/bin/env python3
"""Generate per-character sofa sleep/doze sprites from the curled pose template.

Reads media/sprites/chars/sleep_sofa.png (side-curled doze with Z), excises
its head, remaps the lying body to each agent's idle palette, then pastes
that agent's OWN idle head (eyes closed) at the neck anchor -- so hair,
glasses, caps and faces always match the idle original. Writes
{person}_sleep.png as 1024x1024 magenta-backed sheets alongside the other
character frames.
"""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image
import numpy as np
from scipy import ndimage as _nd

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "media" / "sprites" / "chars"
TEMPLATE = SRC / "sleep_sofa.png"
OUT_SIZE = 1024
MAGENTA = (255, 0, 255)

PEOPLE = ["you", "byte", "kai", "ops", "pixel", "cursor"]

# Target region colors sampled from each agent's idle art.
# shirt = outer garment on the sleep sweater mass; pants = lower body.
# skin = face/hands; the old global SKIN triple ran too orange.
PALETTES = {
    "you": {
        "hair": (104, 56, 32),
        "hair_dark": (64, 32, 16),
        "hair_light": (140, 90, 50),
        "shirt": (40, 56, 120),
        "shirt_dark": (24, 36, 80),
        "shirt_light": (72, 96, 168),
        "pants": (176, 152, 96),
        "pants_dark": (120, 100, 60),
        "pants_light": (200, 176, 120),
        "skin": (236, 195, 162),
        "skin_dark": (211, 175, 146),
        "skin_light": (246, 208, 180),
        "sock": (245, 245, 248),
        "accent": (240, 240, 245),  # collar stripe hint
    },
    "byte": {
        "hair": (224, 176, 80),
        "hair_dark": (168, 120, 40),
        "hair_light": (240, 210, 130),
        "shirt": (245, 246, 248),  # lab coat
        "shirt_dark": (190, 198, 205),
        "shirt_light": (255, 255, 255),
        "pants": (168, 140, 100),
        "pants_dark": (120, 100, 70),
        "pants_light": (200, 175, 130),
        "skin": (236, 190, 156),
        "skin_dark": (224, 182, 150),
        "skin_light": (244, 204, 176),
        "sock": (250, 250, 252),
        "accent": (40, 152, 144),  # teal shirt peek
    },
    "kai": {
        "hair": (52, 50, 56),  # black-grey bob
        "hair_dark": (30, 30, 36),
        "hair_light": (85, 85, 95),
        "shirt": (68, 113, 58),  # green jacket
        "shirt_dark": (40, 73, 34),
        "shirt_light": (86, 130, 72),
        "pants": (117, 117, 117),  # neutral grey
        "pants_dark": (83, 80, 80),
        "pants_light": (150, 150, 150),
        "skin": (254, 218, 193),  # very pale
        "skin_dark": (250, 210, 182),
        "skin_light": (255, 228, 205),
        "sock": (245, 245, 248),
        "accent": (241, 231, 203),  # cream shirt tab
    },
    "ops": {
        "hair": (96, 56, 40),
        "hair_dark": (64, 36, 24),
        "hair_light": (130, 80, 55),
        "shirt": (184, 40, 40),  # red plaid shirt
        "shirt_dark": (120, 24, 24),
        "shirt_light": (220, 70, 70),
        "pants": (104, 112, 64),  # olive
        "pants_dark": (72, 80, 40),
        "pants_light": (140, 150, 90),
        "skin": (238, 192, 155),
        "skin_dark": (226, 184, 148),
        "skin_light": (246, 206, 178),
        "sock": (245, 245, 248),
        "accent": (40, 32, 32),
    },
    "pixel": {
        "hair": (24, 24, 32),
        "hair_dark": (12, 12, 16),
        "hair_light": (56, 56, 64),
        "shirt": (63, 110, 64),  # hoodie green
        "shirt_dark": (34, 73, 43),
        "shirt_light": (80, 128, 80),
        "pants": (48, 52, 64),
        "pants_dark": (28, 32, 40),
        "pants_light": (80, 86, 100),
        "skin": (234, 190, 157),
        "skin_dark": (222, 183, 150),
        "skin_light": (242, 202, 175),
        "sock": (245, 245, 248),
        "accent": (230, 230, 235),  # drawstring hint
    },
    "cursor": {
        "hair": (112, 184, 152),
        "hair_dark": (48, 120, 104),
        "hair_light": (150, 210, 180),
        "shirt": (48, 52, 58),  # near-black jacket
        "shirt_dark": (30, 32, 36),
        "shirt_light": (72, 76, 82),
        "pants": (24, 24, 32),
        "pants_dark": (12, 12, 16),
        "pants_light": (56, 56, 64),
        "skin": (232, 189, 155),
        "skin_dark": (218, 183, 151),
        "skin_light": (240, 200, 168),
        "sock": (250, 250, 252),
        "accent": (160, 220, 80),  # cursor badge
    },
}
Z_COLOR = (120, 180, 240)
Z_DARK = (80, 140, 200)
OUTLINE = (20, 16, 24)


def lumin(c) -> float:
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def colorize(base_rgb, target, mean_lum, lo=0.55, hi=1.35, lum_override=None):
    """Preserve template shading by scaling target toward luminance ratio."""
    lum = lumin(base_rgb) if lum_override is None else lum_override
    if mean_lum < 1:
        mean_lum = 1.0
    ratio = max(lo, min(hi, lum / mean_lum))
    return tuple(max(0, min(255, int(target[i] * ratio))) for i in range(3))


# Fixed template bbox (x156-1118, y370-901): haircut variants must share
# zone fractions, scale and sheet placement with the uncut template.
FIXED_BBOX = (156, 370, 1118, 901)

# Idle-head transplant: each agent sleeps with their OWN head. The template
# head is excised (garments + Z survive the gates), then the agent's idle
# head -- eyes closed, scaled to the template's 343px crown-to-chin -- is
# pasted at the neck anchor. Template-space coords; template is fixed.
HEAD_H = 343
HEAD_ANCHOR = (359, 782)  # chin landing (x, y)

# Idle-space measurements: chin (x, y), crown-to-chin height, eye paint
# boxes for closing (x0, y0, x1, y1), neck half-width, hair droop below chin.
HEADS = {
    "pixel": {"chin": (531, 529), "headh": 474, "droop": 14, "neck": 60,
              "eyes": [(516, 338, 632, 432)]},
    "kai": {"chin": (556, 540), "headh": 483, "droop": 55, "neck": 60,
            "eyes": [(444, 390, 511, 443), (611, 392, 677, 445)]},
    "byte": {"chin": (531, 529), "headh": 494, "droop": 50, "neck": 60,
             "eyes": [(345, 338, 412, 432), (516, 338, 632, 432)]},
    "ops": {"chin": (530, 529), "headh": 455, "droop": 14, "neck": 60,
            "eyes": [(345, 338, 412, 432), (516, 338, 632, 432)]},
    "cursor": {"chin": (531, 529), "headh": 474, "droop": 14, "neck": 60,
               "eyes": [(345, 338, 412, 432), (516, 338, 632, 432)]},
    "you": {"chin": (531, 529), "headh": 454, "droop": 14, "neck": 60,
            "eyes": [(345, 338, 412, 432), (516, 338, 632, 432)]},
}
# Per-agent paste nudge (template px) after the anchor placement.
HEAD_DXDY = {
    "pixel": (0, 0), "kai": (0, 0), "byte": (40, 0),
    "ops": (0, 0), "cursor": (0, 0), "you": (0, 0),
}


def _fill_small_holes(out: np.ndarray, alpha: np.ndarray, max_size=200):
    opq = alpha > 30
    lab, n = _nd.label(~opq)
    border = set(np.unique(np.concatenate(
        [lab[0, :], lab[-1, :], lab[:, 0], lab[:, -1]])))
    for i in range(1, n + 1):
        if i in border:
            continue
        hole = lab == i
        if hole.sum() < max_size:
            out[hole] = (10, 10, 14, 255)
            alpha[hole] = 255


def excise_head(arr: np.ndarray) -> np.ndarray:
    """Remove the template's head so the idle head can take its place.

    Zone A takes the head bulk (skin/hair/dark, but Z + knit + whites
    survive); the mid zone keeps side hair as back-hair behind the new
    cheek while ear skin/lines go; zone B takes neck skin only; zone P
    takes the warm pool hair (neutral cuff outlines stay). Exposed
    boundaries are re-outlined.
    """
    out = arr.copy()
    rgb = out[:, :, :3].astype(int)
    alpha = out[:, :, 3]
    sm = rgb.sum(axis=2)
    skinfam = (
        (rgb[:, :, 0] > 190)
        & ((rgb[:, :, 0] - rgb[:, :, 1]) > 28)
        & ((rgb[:, :, 0] - rgb[:, :, 2]) > 50)
    )
    bright = rgb.min(axis=2) > 200
    zblue = (
        (rgb[:, :, 2] > 140)
        & (rgb[:, :, 2] > rgb[:, :, 1] + 25)
        & (rgb[:, :, 2] > rgb[:, :, 0] + 25)
    )
    knit = (
        (rgb[:, :, 0] > 160)
        & (rgb[:, :, 1] > 145)
        & (rgb[:, :, 2] > 120)
        & (np.abs(rgb[:, :, 0] - rgb[:, :, 1]) < 50)
    )
    warmdark = (sm < 450) & ((rgb[:, :, 0] - rgb[:, :, 2]) > 8) & ~skinfam & ~bright
    neutraldark = (sm < 200) & (np.abs(rgb[:, :, 0] - rgb[:, :, 2]) < 12)
    opq = alpha > 30
    H, W = alpha.shape
    XX, YY = np.meshgrid(np.arange(W), np.arange(H))
    zbox = (XX >= 435) & (XX <= 525) & (YY >= 365) & (YY <= 505)
    takeA = (XX < 500) & (YY < 760) & opq & (skinfam | warmdark | neutraldark)
    takeA &= ~zblue & ~knit & ~bright & ~zbox
    # Mid zone: behind the new cheek the side hair stays (fills the notch
    # as back-hair) while ear skin/lines go.
    takeM = (XX >= 500) & (XX < 560) & (YY >= 600) & (YY < 760) & opq
    takeM &= (skinfam | neutraldark) & ~zblue & ~knit & ~bright & ~zbox
    takeB = (XX < 545) & (YY >= 760) & (YY < 820) & opq & skinfam
    takeP = (XX < 416) & (YY >= 820) & (YY < 890) & opq & warmdark
    take = takeA | takeM | takeB | takeP
    removed = take.copy()
    alpha[take] = 0
    for _ in range(2):
        opq = alpha > 30
        nbr = _nd.convolve(opq.astype(np.int32), np.ones((3, 3), np.int32),
                           mode="constant") - opq.astype(np.int32)
        crumb = opq & (nbr < 2)
        removed |= crumb
        alpha[crumb] = 0
    _fill_small_holes(out, alpha)
    opq = alpha > 30
    adj = (
        np.roll(removed, 1, 0) | np.roll(removed, -1, 0)
        | np.roll(removed, 1, 1) | np.roll(removed, -1, 1)
    )
    edge = opq & adj
    out[edge] = (8, 8, 12, 255)
    return out


def _close_eyes(head: np.ndarray, alpha: np.ndarray, boxes):
    """Paint closed-eye lash arcs over open eyes (head-space coords)."""
    rgb = head[:, :, :3].astype(int)
    sm = rgb.sum(axis=2)
    for (x0, y0, x1, y1) in boxes:
        ring = np.zeros_like(alpha, dtype=bool)
        ring[max(0, y0 - 18): y1 + 18, max(0, x0 - 18): x1 + 18] = True
        ring[y0: y1 + 1, x0: x1 + 1] = False
        skin = (
            (rgb[:, :, 0] > 190)
            & ((rgb[:, :, 0] - rgb[:, :, 1]) > 28)
            & ((rgb[:, :, 0] - rgb[:, :, 2]) > 50)
        )
        samp = skin & ring & (alpha > 30)
        fill = tuple(int(np.median(rgb[:, :, i][samp])) for i in range(3)) if np.any(samp) else (236, 192, 156)
        box = np.zeros_like(alpha, dtype=bool)
        box[y0: y1 + 1, x0: x1 + 1] = True
        white = rgb.min(axis=2) > 215
        gray = ((rgb.max(axis=2) - rgb.min(axis=2)) < 45) & (rgb.min(axis=2) > 165)
        dark = sm < 250
        brown = (rgb[:, :, 0] < 200) & ((rgb[:, :, 0] - rgb[:, :, 2]) > 30) & (sm < 450)
        head[box & (alpha > 30) & (white | gray | dark | brown)] = (*fill, 255)
        # Lash arc: shallow bowl across the box middle.
        ym = (y0 + y1) // 2 + 2
        dip = max(4, int((x1 - x0) * 0.22))
        pts = []
        for t in (i / 32 for i in range(33)):
            x = (1 - t) ** 2 * (x0 + 5) + 2 * (1 - t) * t * ((x0 + x1) / 2) + t ** 2 * (x1 - 5)
            y = (1 - t) ** 2 * ym + 2 * (1 - t) * t * (ym + dip) + t ** 2 * ym
            pts.append((int(round(x)), int(round(y))))
        h, w = alpha.shape
        for (x, y) in pts:
            for dy in range(-1, 2):
                for dx in range(-1, 2):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h and alpha[yy, xx] > 30:
                        head[yy, xx] = (*OUTLINE, 255)


def extract_head(person: str):
    """Crop the agent's idle head, scale to HEAD_H, close eyes.

    Returns (head_rgba, chin_xy) in scaled head space.
    """
    spec = HEADS[person]
    im = np.array(Image.open(SRC / f"{person}_idle.png").convert("RGB")).astype(int)
    R, G, B = im[:, :, 0], im[:, :, 1], im[:, :, 2]
    opq = ~((R > 200) & (G < 100) & (B > 150))
    chx, chy = spec["chin"]
    bottom = min(1023, chy + spec["droop"] + 30)
    H, W = opq.shape
    XX = np.tile(np.arange(W), (H, 1))
    skinfam = (R > 190) & ((R - G) > 28) & ((R - B) > 50)
    rng = np.maximum(np.maximum(R, G), B) - np.minimum(np.minimum(R, G), B)
    sm = R + G + B
    midgray = (R > 120) & (R < 215) & (rng < 40)
    hairlike = opq & ~skinfam & (sm < 620) & ~((np.minimum(np.minimum(R, G), B)) > 200)
    hairlike &= (rng > 20) | (sm < 350) | midgray
    keep = np.zeros_like(opq)
    keep[:chy] = opq[:chy]
    below = np.zeros_like(opq)
    below[chy: bottom + 1] = True
    center = below & (np.abs(XX - chx) < spec["neck"])
    keep |= below & hairlike
    keep |= center & skinfam
    keep &= opq
    rgba = np.zeros((H, W, 4), dtype=np.uint8)
    rgba[keep] = np.concatenate([im[keep], np.full((keep.sum(), 1), 255, np.uint8)], axis=1)
    # Outline the cut bottom (lowest kept pixel per column).
    cols = np.nonzero(keep.any(axis=0))[0]
    for x in cols:
        ys = np.nonzero(keep[:, x])[0]
        y = int(ys.max())
        if y >= chy:
            rgba[y, x] = (*OUTLINE, 255)
    ys, xs = np.nonzero(keep)
    crop = rgba[ys.min(): ys.max() + 1, xs.min(): xs.max() + 1].copy()
    s = HEAD_H / spec["headh"]
    nw, nh = max(1, int(round(crop.shape[1] * s))), max(1, int(round(crop.shape[0] * s)))
    small = np.array(Image.fromarray(crop).resize((nw, nh), Image.NEAREST))
    boxes = [
        (int(round((x0 - xs.min()) * s)), int(round((y0 - ys.min()) * s)),
         int(round((x1 - xs.min()) * s)), int(round((y1 - ys.min()) * s)))
        for (x0, y0, x1, y1) in spec["eyes"]
    ]
    _close_eyes(small, small[:, :, 3], boxes)
    chin_s = (int(round((chx - xs.min()) * s)), int(round((chy - ys.min()) * s)))
    return small, chin_s


def classify(arr: np.ndarray, fixed_bbox=None) -> np.ndarray:
    """Region codes: 0 trans, 1 outline, 2 hair, 3 skin, 4 blush,
    5 sweater, 6 pants, 7 sock, 8 z, 9 other."""
    h, w = arr.shape[:2]
    alpha = arr[:, :, 3]
    if fixed_bbox is not None:
        minx, miny, maxx, maxy = fixed_bbox
    else:
        ys, xs = np.where(alpha > 30)
        miny, maxy = int(ys.min()), int(ys.max())
        minx, maxx = int(xs.min()), int(xs.max())
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    out = np.zeros((h, w), dtype=np.uint8)

    for y in range(miny, maxy + 1):
        for x in range(minx, maxx + 1):
            r = int(arr[y, x, 0])
            g = int(arr[y, x, 1])
            b = int(arr[y, x, 2])
            a = int(arr[y, x, 3])
            if a < 30:
                continue
            fy = (y - miny) / float(bh)
            fx = (x - minx) / float(bw)
            if r + g + b < 55:
                out[y, x] = 1
                continue
            if b > 140 and b > g + 25 and b > r + 25 and fy < 0.28 and fx < 0.45:
                out[y, x] = 8
                continue
            hairish = (
                40 <= r <= 170
                and 20 <= g <= 110
                and b <= 95
                and r >= g
                and r > b + 5
                and (r + g + b) < 340
            )
            if hairish and fy < 0.55 and fx < 0.55:
                out[y, x] = 2
                continue
            # No flat-blush code: the old blush test only caught the warm
            # forehead shadow (a pink headband artifact). Cheek warmth remaps
            # through the skin triple instead. The (r-g)/(r-b) floor keeps
            # warm sweater-knit highlights out of the skin class.
            skin = (
                r > 190
                and 130 < g < 210
                and 90 < b < 180
                and (r - g) > 28
                and (r - b) > 50
                and abs(g - b) < 60
            )
            if skin:
                out[y, x] = 3
                continue
            if r > 210 and g > 210 and b > 210 and fy > 0.55 and fx > 0.55:
                out[y, x] = 7
                continue
            # Shaded sock grey (cool-neutral, feet zone) so it is not eaten
            # by the sweater test and tinted shirt-color (red feet on Ops).
            if (
                min(r, g, b) > 140
                and (max(r, g, b) - min(r, g, b)) < 32
                and b > g - 8
                and fx > 0.70
                and fy > 0.50
            ):
                out[y, x] = 7
                continue
            if r < 110 and g < 110 and b < 120 and (r + g + b) > 55:
                # True pants live on the right (fx > 0.55). Dark crumbs on
                # the left are lash/hair AA (face zone -> outline), hair
                # shadow (upper -> hair) or sleeve shadow (lower -> shirt).
                if fx > 0.52:
                    out[y, x] = 6
                    continue
                if fx < 0.48 and 0.30 < fy < 0.90:
                    out[y, x] = 1
                    continue
                if fy < 0.62:
                    out[y, x] = 2
                    continue
            if r > 160 and g > 145 and b > 120 and abs(r - g) < 50:
                out[y, x] = 5
                continue
            if hairish:
                out[y, x] = 2
                continue
            if skin:
                out[y, x] = 3
                continue
            out[y, x] = 9
    # Keep only the face-connected skin component: stray warm knit crumbs
    # elsewhere on the sweater must remap as garment, not skin.
    skinmask = out == 3
    if np.any(skinmask):
        lab, _ = _nd.label(skinmask)
        # Cheek seed; fall back to the most common skin label nearby so a
        # seed landing on a lash line never wipes the whole face.
        sy, sx = int(miny + bh * 0.66), int(minx + bw * 0.26)
        seed = int(lab[sy, sx])
        if not seed:
            window = lab[max(0, sy - 10): sy + 11, max(0, sx - 10): sx + 11]
            nz = window[window != 0]
            seed = int(np.bincount(nz).argmax()) if nz.size else 0
        stray = skinmask & (lab != seed) if seed else np.zeros_like(skinmask)
        if np.any(stray):
            r = arr[:, :, 0].astype(int)
            g = arr[:, :, 1].astype(int)
            b = arr[:, :, 2].astype(int)
            sweaterish = (r > 160) & (g > 145) & (b > 120) & (np.abs(r - g) < 50)
            out[stray & sweaterish] = 5
            out[stray & ~sweaterish] = 9
    return out, (minx, miny, maxx, maxy)


def region_mean_lum(arr, mask, code) -> float:
    sel = mask == code
    if not np.any(sel):
        return 128.0
    pix = arr[sel][:, :3].astype(np.float32)
    return float(0.2126 * pix[:, 0].mean() + 0.7152 * pix[:, 1].mean() + 0.0722 * pix[:, 2].mean())


def paint_accent_stripe(out_rgb: Image.Image, mask: np.ndarray, accent, person: str):
    """Tiny recognizable accent on the sweater mass (stripe / badge / teal peek)."""
    if accent is None:
        return
    sweater = np.argwhere(mask == 5)
    if sweater.size == 0:
        return
    # Centroid of sweater
    cy = int(sweater[:, 0].mean())
    cx = int(sweater[:, 1].mean())
    px = out_rgb.load()
    w, h = out_rgb.size
    if person == "you":
        # Vertical white stripe down chest-ish
        for dy in range(-18, 30):
            for dx in range(-2, 3):
                x, y = cx + dx - 8, cy + dy
                if 0 <= x < w and 0 <= y < h and mask[y, x] == 5:
                    px[x, y] = accent
    elif person == "byte":
        # Teal shirt V at the collar below the chin (template space).
        # Solid fill over the cuff ribs with an outlined coat opening.
        for y in range(825, 866):
            half = 4 + int((y - 825) * 0.3)
            for x in range(400 - half, 400 + half + 1):
                if 0 <= x < w and 0 <= y < h and int(mask[y, x]) != 0:
                    edge = x - (400 - half) < 2 or (400 + half) - x < 2
                    px[x, y] = OUTLINE if edge else accent
    elif person == "cursor":
        # Small lime badge on chest
        for dy in range(-4, 5):
            for dx in range(-4, 5):
                if dx * dx + dy * dy > 16:
                    continue
                x, y = cx + 12 + dx, cy - 5 + dy
                if 0 <= x < w and 0 <= y < h and mask[y, x] in (5, 9):
                    px[x, y] = accent if dx * dx + dy * dy > 9 else (20, 20, 20)
    elif person == "pixel":
        # Drawstring tabs on the shoulder by the collar (template space).
        for x0 in (548, 562):
            for y in range(718, 731):
                for x in range(x0, x0 + 4):
                    if 0 <= x < w and 0 <= y < h and int(mask[y, x]) != 0:
                        px[x, y] = accent
    elif person == "kai":
        # Kai's cream shirt tab is painted by paint_kai_tab; no speckle here.
        pass


def _rounded_rect_border(x0, y0, x1, y1, radius, thick):
    """Yield (x, y, is_border) for a rounded-rect outline + fill scan."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            # Distance to the rect, rounded at corners.
            cx = min(max(x, x0 + radius), x1 - radius)
            cy = min(max(y, y0 + radius), y1 - radius)
            dd = (x - cx) ** 2 + (y - cy) ** 2
            inside = (
                (x0 <= x <= x1 and y0 + radius <= y <= y1 - radius)
                or (y0 <= y <= y1 and x0 + radius <= x <= x1 - radius)
                or dd <= radius * radius
            )
            if not inside:
                continue
            on_edge = (
                x - x0 < thick
                or x1 - x < thick
                or y - y0 < thick
                or y1 - y < thick
            )
            if not on_edge and (x < x0 + radius or x > x1 - radius) and (
                y < y0 + radius or y > y1 - radius
            ):
                # Corner ring check.
                dist = math.sqrt(dd)
                on_edge = (radius - thick) <= dist <= radius
            yield x, y, on_edge


def paint_kai_tab(out_rgb: Image.Image, mask: np.ndarray):
    """Kai's cream shirt tab (glasses/streak come with her idle head now)."""
    px = out_rgb.load()
    w, h = out_rgb.size

    def inside(x, y):
        return 0 <= x < w and 0 <= y < h and int(mask[y, x]) != 0

    # Cream shirt tab at the jacket front, on the sweater mass just
    # left of the pants corner. Fills over knit lines so it reads flat.
    cream, cream_hi, cream_lo = (241, 231, 203), (250, 242, 222), (222, 208, 178)
    for x, y, border in _rounded_rect_border(590, 740, 690, 830, 6, 3):
        if not inside(x, y):
            continue
        if border:
            px[x, y] = OUTLINE
        else:
            frac = (y - 740) / 90.0
            px[x, y] = cream_hi if frac < 0.4 else (cream_lo if frac > 0.75 else cream)


def remap_person(
    arr: np.ndarray,
    mask: np.ndarray,
    bbox,
    person: str,
    blurlum: np.ndarray,
) -> Image.Image:
    pal = PALETTES[person]
    h, w = arr.shape[:2]
    out = Image.new("RGB", (w, h), MAGENTA)
    px = out.load()

    means = {
        2: region_mean_lum(arr, mask, 2),
        3: region_mean_lum(arr, mask, 3),
        5: region_mean_lum(arr, mask, 5),
        6: region_mean_lum(arr, mask, 6),
        7: region_mean_lum(arr, mask, 7),
        8: region_mean_lum(arr, mask, 8),
    }

    for y in range(h):
        for x in range(w):
            code = int(mask[y, x])
            if code == 0:
                continue
            base = (int(arr[y, x, 0]), int(arr[y, x, 1]), int(arr[y, x, 2]))
            if code == 1:
                px[x, y] = OUTLINE
            elif code == 2:
                # Shade across hair_dark / hair / hair_light by luminance
                lum = lumin(base)
                m = means[2]
                if lum < m * 0.85:
                    tgt = pal["hair_dark"]
                elif lum > m * 1.12:
                    tgt = pal["hair_light"]
                else:
                    tgt = pal["hair"]
                px[x, y] = colorize(base, tgt, m)
            elif code in (3, 4):
                lum = lumin(base)
                m = means[3]
                if lum < m * 0.9:
                    tgt = pal["skin_dark"]
                elif lum > m * 1.1:
                    tgt = pal["skin_light"]
                else:
                    tgt = pal["skin"]
                px[x, y] = colorize(base, tgt, m, lo=0.7, hi=1.2)
            elif code == 5:
                m = means[5]
                # Flat-garment agents read tone from blurred luminance so the
                # fine knit ribs remap to one clean garment while the broad
                # drape folds keep shading. Ops keeps per-pixel contrast:
                # it reads as plaid.
                if person == "ops":
                    lum = lumin(base)
                    dark_at, light_at, lo, hi = 0.88, 1.10, 0.55, 1.35
                    tone_lum, ratio_lum = lum, None
                else:
                    lum = float(blurlum[y, x])
                    dark_at, light_at, lo, hi = 0.82, 1.15, 0.88, 1.12
                    tone_lum, ratio_lum = lum, lum
                if tone_lum < m * dark_at:
                    tgt = pal["shirt_dark"]
                elif tone_lum > m * light_at:
                    tgt = pal["shirt_light"]
                else:
                    tgt = pal["shirt"]
                px[x, y] = colorize(base, tgt, m, lo=lo, hi=hi, lum_override=ratio_lum)
            elif code == 6:
                lum = float(blurlum[y, x])
                m = means[6]
                if lum < m * 0.9:
                    tgt = pal["pants_dark"]
                elif lum > m * 1.15:
                    tgt = pal["pants_light"]
                else:
                    tgt = pal["pants"]
                px[x, y] = colorize(base, tgt, m, lum_override=lum)
            elif code == 7:
                px[x, y] = colorize(base, pal["sock"], means[7], lo=0.85, hi=1.1)
            elif code == 8:
                lum = lumin(base)
                m = means[8]
                tgt = Z_DARK if lum < m else Z_COLOR
                px[x, y] = colorize(base, tgt, m, lo=0.75, hi=1.2)
            else:
                # other → shirt mass
                px[x, y] = colorize(base, pal["shirt"], means[5] if means[5] else 128.0)

    paint_accent_stripe(out, mask, pal.get("accent"), person)
    if person == "kai":
        paint_kai_tab(out, mask)
    return out


def paste_head(body: Image.Image, person: str):
    """Paste the agent's idle head (eyes closed) at the neck anchor."""
    head, (chx, chy) = extract_head(person)
    dx, dy = HEAD_DXDY[person]
    ox = HEAD_ANCHOR[0] + dx - chx
    oy = HEAD_ANCHOR[1] + dy - chy
    px = body.load()
    hh, hw = head.shape[:2]
    for y in range(hh):
        for x in range(hw):
            if head[y, x, 3] <= 30:
                continue
            bx, by = ox + x, oy + y
            if 0 <= bx < body.size[0] and 0 <= by < body.size[1]:
                px[bx, by] = (int(head[y, x, 0]), int(head[y, x, 1]), int(head[y, x, 2]))


def fit_to_sheet(sprite: Image.Image, bbox) -> Image.Image:
    """Crop to content, scale to fit ~78% of 1024 sheet, center on magenta."""
    minx, miny, maxx, maxy = bbox
    # Expand bbox slightly for the pasted head / Z at the edges.
    pad = 8
    minx = max(0, minx - pad)
    miny = max(0, miny - pad)
    maxx = min(sprite.size[0] - 1, maxx + pad)
    maxy = min(sprite.size[1] - 1, maxy + pad)
    crop = sprite.crop((minx, miny, maxx + 1, maxy + 1))
    # Scale so longest side fits target
    tw = int(OUT_SIZE * 0.82)
    th = int(OUT_SIZE * 0.55)
    scale = min(tw / crop.size[0], th / crop.size[1])
    nw = max(1, int(crop.size[0] * scale))
    nh = max(1, int(crop.size[1] * scale))
    resized = crop.resize((nw, nh), Image.NEAREST)
    sheet = Image.new("RGB", (OUT_SIZE, OUT_SIZE), MAGENTA)
    # Bias slightly low-center so feet sit near tile anchor like upright sprites.
    ox = (OUT_SIZE - nw) // 2
    oy = (OUT_SIZE - nh) // 2 + int(OUT_SIZE * 0.06)
    # Manual paste skipping magenta (crop has magenta bg already)
    sp = resized.load()
    dp = sheet.load()
    for y in range(nh):
        for x in range(nw):
            c = sp[x, y]
            if c == MAGENTA:
                continue
            dp[ox + x, oy + y] = c
    return sheet


def main() -> None:
    if not TEMPLATE.exists():
        raise SystemExit(f"missing template {TEMPLATE}")
    arr = np.array(Image.open(TEMPLATE).convert("RGBA"))
    body_arr = excise_head(arr)
    mask, _ = classify(body_arr, FIXED_BBOX)
    rgb = body_arr[:, :, :3].astype(np.float32)
    lum_full = 0.2126 * rgb[:, :, 0] + 0.7152 * rgb[:, :, 1] + 0.0722 * rgb[:, :, 2]
    blurlum = _nd.uniform_filter(lum_full, size=15)
    written = 0
    for person in PEOPLE:
        remapped = remap_person(body_arr, mask, FIXED_BBOX, person, blurlum)
        paste_head(remapped, person)
        sheet = fit_to_sheet(remapped, FIXED_BBOX)
        out = SRC / f"{person}_sleep.png"
        sheet.save(out)
        written += 1
        print(f"wrote {out.name}")
    print(f"done: {written} sleep frames in {SRC}")


if __name__ == "__main__":
    main()
