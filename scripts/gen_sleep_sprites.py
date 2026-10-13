#!/usr/bin/env python3
"""Generate per-character sofa sleep/doze sprites from the curled pose template.

Reads media/sprites/chars/sleep_sofa.png (side-curled doze with Z), segments
hair / skin / sweater / pants / socks / outline / Z, then luminance-remaps each
region to that agent's idle palette so the pose stays identical while hair and
clothes match. Writes {person}_sleep.png as 1024x1024 magenta-backed sheets
alongside the other character frames.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "media" / "sprites" / "chars"
TEMPLATE = SRC / "sleep_sofa.png"
OUT_SIZE = 1024
MAGENTA = (255, 0, 255)

PEOPLE = ["you", "byte", "kai", "ops", "pixel", "cursor"]

# Target region colors sampled from each agent's idle/walk art.
# shirt = outer garment on the sleep sweater mass; pants = lower body.
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
        "sock": (245, 245, 248),
        "hat": None,
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
        "sock": (250, 250, 252),
        "hat": None,
        "accent": (40, 152, 144),  # teal shirt peek
    },
    "kai": {
        "hair": (184, 96, 32),
        "hair_dark": (128, 56, 24),
        "hair_light": (210, 130, 60),
        "shirt": (128, 56, 96),  # purple vest mass
        "shirt_dark": (88, 36, 64),
        "shirt_light": (168, 90, 130),
        "pants": (112, 112, 112),
        "pants_dark": (72, 72, 72),
        "pants_light": (150, 150, 150),
        "sock": (245, 245, 248),
        "hat": None,
        "accent": (240, 232, 210),  # cream sleeves
    },
    "ops": {
        "hair": (96, 56, 40),
        "hair_dark": (64, 36, 24),
        "hair_light": (130, 80, 55),
        "shirt": (184, 40, 40),  # red jacket
        "shirt_dark": (120, 24, 24),
        "shirt_light": (220, 70, 70),
        "pants": (104, 112, 64),  # olive
        "pants_dark": (72, 80, 40),
        "pants_light": (140, 150, 90),
        "sock": (245, 245, 248),
        "hat": (192, 40, 40),  # red cap over bun
        "accent": (40, 32, 32),
    },
    "pixel": {
        "hair": (24, 24, 32),
        "hair_dark": (12, 12, 16),
        "hair_light": (56, 56, 64),
        "shirt": (40, 88, 48),  # forest hoodie
        "shirt_dark": (24, 56, 32),
        "shirt_light": (72, 130, 80),
        "pants": (48, 52, 64),
        "pants_dark": (28, 32, 40),
        "pants_light": (80, 86, 100),
        "sock": (245, 245, 248),
        "hat": None,
        "accent": (230, 230, 235),  # drawstring hint
    },
    "cursor": {
        "hair": (112, 184, 152),
        "hair_dark": (48, 120, 104),
        "hair_light": (150, 210, 180),
        "shirt": (56, 60, 64),  # charcoal jacket
        "shirt_dark": (32, 36, 40),
        "shirt_light": (90, 96, 100),
        "pants": (24, 24, 32),
        "pants_dark": (12, 12, 16),
        "pants_light": (56, 56, 64),
        "sock": (250, 250, 252),
        "hat": None,
        "accent": (160, 220, 80),  # cursor badge
    },
}

SKIN = (248, 200, 152)
SKIN_DARK = (216, 152, 112)
SKIN_LIGHT = (255, 228, 200)
BLUSH = (232, 140, 140)
Z_COLOR = (120, 180, 240)
Z_DARK = (80, 140, 200)
OUTLINE = (20, 16, 24)


def lumin(c) -> float:
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def colorize(base_rgb, target, mean_lum, lo=0.55, hi=1.35):
    """Preserve template shading by scaling target toward luminance ratio."""
    lum = lumin(base_rgb)
    if mean_lum < 1:
        mean_lum = 1.0
    ratio = max(lo, min(hi, lum / mean_lum))
    return tuple(max(0, min(255, int(target[i] * ratio))) for i in range(3))


def classify(arr: np.ndarray) -> np.ndarray:
    """Region codes: 0 trans, 1 outline, 2 hair, 3 skin, 4 blush,
    5 sweater, 6 pants, 7 sock, 8 z, 9 other."""
    h, w = arr.shape[:2]
    alpha = arr[:, :, 3]
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
            if r > 170 and g < 160 and b < 150 and (r - g) > 35 and fy < 0.55:
                out[y, x] = 4
                continue
            skin = (
                r > 190
                and 130 < g < 210
                and 90 < b < 180
                and (r - b) > 35
                and (r - g) < 55
                and abs(g - b) < 60
            )
            if skin and not (r > 230 and g > 210 and b > 180):
                out[y, x] = 3
                continue
            if r > 210 and g > 210 and b > 210 and fy > 0.55 and fx > 0.55:
                out[y, x] = 7
                continue
            if r < 110 and g < 110 and b < 120 and (r + g + b) > 55:
                if fy > 0.35 or fx > 0.45:
                    out[y, x] = 6
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
    return out, (minx, miny, maxx, maxy)


def region_mean_lum(arr, mask, code) -> float:
    sel = mask == code
    if not np.any(sel):
        return 128.0
    pix = arr[sel][:, :3].astype(np.float32)
    return float(0.2126 * pix[:, 0].mean() + 0.7152 * pix[:, 1].mean() + 0.0722 * pix[:, 2].mean())


def paint_hat(out_rgb: Image.Image, mask: np.ndarray, bbox, hat_color):
    """Stamp a compact baseball cap over the bun for Ops."""
    if hat_color is None:
        return
    hair = np.argwhere(mask == 2)
    if hair.size == 0:
        return
    y_cut = np.percentile(hair[:, 0], 35)
    top = hair[hair[:, 0] <= y_cut]
    cy = int(top[:, 0].mean())
    cx = int(top[:, 1].mean())
    px = out_rgb.load()
    w, h = out_rgb.size
    minx, miny, maxx, maxy = bbox
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    wr = max(12, int(bw * 0.09))
    hr = max(7, int(bh * 0.06))
    dark = tuple(max(0, int(v * 0.7)) for v in hat_color)
    light = tuple(min(255, int(v * 1.1)) for v in hat_color)
    # Crown (low dome over bun)
    for dy in range(-hr, hr // 2 + 1):
        for dx in range(-wr, wr + 1):
            nx = dx / float(wr)
            ny = dy / float(max(1, hr))
            if (nx * nx) + (ny * ny) * 1.8 > 1.0:
                continue
            x, y = cx + dx, cy + dy - 2
            if not (0 <= x < w and 0 <= y < h):
                continue
            if (nx * nx) + (ny * ny) * 1.8 > 0.82:
                px[x, y] = OUTLINE
            elif ny < -0.15:
                px[x, y] = light
            else:
                px[x, y] = hat_color
    # Flat bill toward the face (left)
    bill_w = int(wr * 1.1)
    for dy in range(0, 5):
        for dx in range(-wr - bill_w, -wr // 4):
            x, y = cx + dx, cy + dy + 1
            if not (0 <= x < w and 0 <= y < h):
                continue
            if dy == 0 or dy == 4 or dx == -wr - bill_w:
                px[x, y] = OUTLINE
            else:
                px[x, y] = dark if dy > 2 else hat_color



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
        # Teal peek near collar (upper sweater)
        ys = sweater[sweater[:, 0] < cy - 10]
        if ys.size:
            for row in ys[:: max(1, len(ys) // 40)]:
                y, x = int(row[0]), int(row[1])
                if abs(x - cx) < 25:
                    px[x, y] = accent
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
        # Drawstring dots near upper sweater
        for dx in (-6, 6):
            x, y = cx + dx, cy - 15
            if 0 <= x < w and 0 <= y < h:
                px[x, y] = accent
                if y + 1 < h:
                    px[x, y + 1] = accent
    elif person == "kai":
        # Cream sleeve cuffs at outer sweater edges
        for y, x in sweater:
            if mask[y, x] != 5:
                continue
            # near left/right extremes of row
            pass
        left = sweater[sweater[:, 1] < cx - 40]
        right = sweater[sweater[:, 1] > cx + 40]
        for cluster in (left, right):
            if cluster.size == 0:
                continue
            for row in cluster[:: max(1, len(cluster) // 30)]:
                y, x = int(row[0]), int(row[1])
                px[x, y] = accent


def remap_person(arr: np.ndarray, mask: np.ndarray, bbox, person: str) -> Image.Image:
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
            elif code == 3:
                lum = lumin(base)
                m = means[3]
                if lum < m * 0.9:
                    tgt = SKIN_DARK
                elif lum > m * 1.1:
                    tgt = SKIN_LIGHT
                else:
                    tgt = SKIN
                px[x, y] = colorize(base, tgt, m, lo=0.7, hi=1.2)
            elif code == 4:
                px[x, y] = BLUSH
            elif code == 5:
                lum = lumin(base)
                m = means[5]
                if lum < m * 0.88:
                    tgt = pal["shirt_dark"]
                elif lum > m * 1.1:
                    tgt = pal["shirt_light"]
                else:
                    tgt = pal["shirt"]
                px[x, y] = colorize(base, tgt, m)
            elif code == 6:
                lum = lumin(base)
                m = means[6]
                if lum < m * 0.9:
                    tgt = pal["pants_dark"]
                elif lum > m * 1.15:
                    tgt = pal["pants_light"]
                else:
                    tgt = pal["pants"]
                px[x, y] = colorize(base, tgt, m)
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
    paint_hat(out, mask, bbox, pal.get("hat"))
    return out


def fit_to_sheet(sprite: Image.Image, bbox) -> Image.Image:
    """Crop to content, scale to fit ~78% of 1024 sheet, center on magenta."""
    minx, miny, maxx, maxy = bbox
    # Expand bbox slightly for hat/Z that we may have painted
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
    mask, bbox = classify(arr)
    written = 0
    for person in PEOPLE:
        remapped = remap_person(arr, mask, bbox, person)
        sheet = fit_to_sheet(remapped, bbox)
        out = SRC / f"{person}_sleep.png"
        sheet.save(out)
        written += 1
        print(f"wrote {out.name}")
    print(f"done: {written} sleep frames in {SRC}")


if __name__ == "__main__":
    main()
