#!/usr/bin/env python3
"""Generate approximate back-facing frames from front character sprites.

v22 seated sit/type *_back: hand-crafted low-res pixel templates (seated_pixel_v21)
with walk-sheet palettes, sharp outlines, distinct typing arm frames. Upscaled
NEAREST onto the 1024 canvas. Walk/idle back: textured hair clusters + face scrub
(blond no longer misclassified as skin).
"""
from __future__ import annotations

from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "media" / "sprites" / "chars"
PEOPLE = ["byte", "cursor", "kai", "ops", "pixel", "you"]
FRAMES = ["idle", "sit", "type0", "type1", "type2", "walk0", "walk1", "walk2", "walk3"]


# Explicit seated Y bands (fractions of character bbox height).
# head_bot: hair mass ends here; torso_bot (== leg_top): pants begin.
# Tuned once per character so chin-scanning cannot crush the garment band.
SEATED_BANDS = {
    # person: (head_bot_frac, torso_bot_frac)
    "you": (0.40, 0.72),
    "byte": (0.40, 0.72),
    "kai": (0.40, 0.72),
    "ops": (0.40, 0.72),
    "pixel": (0.40, 0.72),
    "cursor": (0.40, 0.72),
}
DEFAULT_SEATED_BANDS = (0.40, 0.72)


def seated_bands(person: str, frame: str = ""):
    head_f, torso_f = SEATED_BANDS.get(person, DEFAULT_SEATED_BANDS)
    if frame.startswith("type"):
        # Typing sits slightly more upright / desk-forward; keep legs a tad higher.
        torso_f = min(0.78, torso_f + 0.04)
    return head_f, torso_f



def is_magenta(r: int, g: int, b: int) -> bool:
    return r > 130 and b > 130 and g < r and g < b and (r + b - 2 * g) > 50


def is_outline(r: int, g: int, b: int) -> bool:
    return (r + g + b) < 55


def is_skin(r: int, g: int, b: int) -> bool:
    if is_magenta(r, g, b) or is_outline(r, g, b):
        return False
    # Blond/yellow/orange hair is warm like peach skin but keeps a low blue
    # channel. Without this, Byte's hair is classified as face and walk-back
    # fills flatten into a blank tan "face".
    if (r - b) > 80 and (g - b) > 48 and b < 130:
        return False
    if r < 95 or g < 55 or b < 40:
        return False
    return (r - b) > 22 and r >= g - 18 and g >= b - 8 and 210 < (r + g + b) < 680


def near(c, o, tol=48):
    return all(abs(c[i] - o[i]) < tol for i in range(3))


def bbox(im: Image.Image):
    w, h = im.size
    px = im.load()
    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(h):
        for x in range(w):
            if not is_magenta(*px[x, y]):
                minx = min(minx, x)
                miny = min(miny, y)
                maxx = max(maxx, x)
                maxy = max(maxy, y)
    return minx, miny, maxx, maxy


def mode_color(samples):
    cnt: Counter = Counter()
    for r, g, b in samples:
        if is_magenta(r, g, b) or is_outline(r, g, b):
            continue
        if r > 230 and g > 230 and b > 230:
            continue
        cnt[(r // 8 * 8, g // 8 * 8, b // 8 * 8)] += 1
    return cnt.most_common(1)[0][0] if cnt else (90, 60, 40)


def darken(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def on_silhouette_edge(x: int, y: int, px, w: int, h: int) -> bool:
    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, -1), (-1, 1), (1, 1)):
        nx, ny = x + dx, y + dy
        if nx < 0 or ny < 0 or nx >= w or ny >= h:
            return True
        if is_magenta(*px[nx, ny]):
            return True
    return False


def cdist(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])


def quant(c):
    return (c[0] // 8 * 8, c[1] // 8 * 8, c[2] // 8 * 8)


def lumin(c) -> float:
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def colorize_luma(base, tex_rgb, mean_lum, strength=0.92, lo=0.48, hi=1.48):
    """Keep base hue; apply relative luminance from a source texture pixel."""
    tl = lumin(tex_rgb)
    ml = mean_lum if mean_lum > 1e-3 else 1.0
    ratio = tl / ml
    ratio = 1.0 + (ratio - 1.0) * strength
    ratio = max(lo, min(hi, ratio))
    return tuple(max(0, min(255, int(round(v * ratio)))) for v in base)


def _hash_jitter(x: int, y: int, n: int = 3) -> int:
    return ((x * 73856093) ^ (y * 19349663) ^ (x * y * 83492791)) % max(1, n)


def build_tex_stamp(samples, tw: int = 64, th: int = 64):
    """Pack source RGB samples into a wrapable stamp + mean luminance + step ratios."""
    if not samples:
        return None
    cleaned = []
    for c in samples:
        L = lumin(c)
        if L < 8 or L > 245:
            continue
        cleaned.append(c)
    if len(cleaned) < 24:
        cleaned = list(samples)
    n = len(cleaned)
    grid = [cleaned[i % n] for i in range(tw * th)]
    mean = sum(lumin(c) for c in cleaned) / float(n)
    # Discrete luminance ratios for posterized pixel-art shade steps.
    from collections import Counter as _Counter
    cnt = _Counter(quant(c) for c in cleaned)
    top = [c for c, k in cnt.most_common(40) if k >= 2]
    top.sort(key=lumin)
    ratios = []
    if top and mean > 1e-3:
        for c in top:
            ratios.append(round(lumin(c) / mean, 3))
        # unique-ish keep 5–9 steps spanning the distribution
        ratios = sorted(set(ratios))
        if len(ratios) > 9:
            idxs = [int(i * (len(ratios) - 1) / 8) for i in range(9)]
            ratios = [ratios[i] for i in idxs]
    if len(ratios) < 3:
        ratios = [0.72, 0.86, 1.0, 1.12, 1.22]
    return {"grid": grid, "tw": tw, "th": th, "mean": mean, "n": n, "ratios": ratios,
            "kind": "bag"}


def build_spatial_stamp(im: Image.Image, boxes, tw: int = 96, th: int = 96):
    """Stamp from real sheet crops so folds/dither keep spatial structure."""
    if im is None or not boxes:
        return None
    px = im.load()
    cells = []
    for (x0, y0, x1, y1) in boxes:
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(im.size[0], x1), min(im.size[1], y1)
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b = px[x, y]
                if is_magenta(r, g, b) or is_outline(r, g, b):
                    continue
                if is_skin(r, g, b):
                    continue
                L = lumin((r, g, b))
                if L < 8 or L > 245:
                    continue
                cells.append((r, g, b))
    if len(cells) < 40:
        return None
    # Fill tw×th by scanning crops in row-major then wrap.
    grid = [cells[i % len(cells)] for i in range(tw * th)]
    # Prefer tiling actual crop rows when a single primary box exists.
    x0, y0, x1, y1 = boxes[0]
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(im.size[0], x1), min(im.size[1], y1)
    bw = max(1, x1 - x0)
    bh = max(1, y1 - y0)
    spatial = []
    for j in range(th):
        for i in range(tw):
            sx = x0 + (i % bw)
            sy = y0 + (j % bh)
            # If primary box pixel unusable, fall back to bag.
            r, g, b = px[sx, sy]
            if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                spatial.append(grid[j * tw + i])
            else:
                spatial.append((r, g, b))
    mean = sum(lumin(c) for c in spatial) / float(len(spatial))
    # ratios from spatial
    from collections import Counter as _Counter
    cnt = _Counter(quant(c) for c in spatial)
    top = [c for c, k in cnt.most_common(40) if k >= 2]
    top.sort(key=lumin)
    ratios = []
    if top and mean > 1e-3:
        ratios = sorted(set(round(lumin(c) / mean, 3) for c in top))
        if len(ratios) > 9:
            idxs = [int(i * (len(ratios) - 1) / 8) for i in range(9)]
            ratios = [ratios[i] for i in idxs]
    if len(ratios) < 3:
        ratios = [0.72, 0.86, 1.0, 1.12, 1.22]
    return {"grid": spatial, "tw": tw, "th": th, "mean": mean, "n": len(cells),
            "ratios": ratios, "kind": "spatial"}


def stamp_tex(stamp, u: float, v: float, x: int, y: int):
    """Spatially coherent stamp sample — exact source RGB, no dither jitter."""
    if stamp is None:
        return None
    tw, th = stamp["tw"], stamp["th"]
    u = max(0.0, min(1.0, u))
    v = max(0.0, min(1.0, v))
    # Stable NN into the crop atlas (4px blocks keep cluster size chunky).
    bx, by = x // 4, y // 4
    ix = (int(u * (tw - 1)) + (bx % 2)) % tw
    iy = (int(v * (th - 1)) + (by % 2)) % th
    return stamp["grid"][iy * tw + ix]


def _stamp_palette(stamp, base):
    """Discrete quantized colors present in a stamp (plus base), sorted by luma."""
    cols = []
    seen = set()
    if stamp and stamp.get("grid"):
        from collections import Counter as _Counter
        cnt = _Counter(quant(c) for c in stamp["grid"])
        for c, n in cnt.most_common(24):
            if n < 2:
                continue
            if c not in seen:
                seen.add(c)
                cols.append(c)
    bq = quant(base)
    if bq not in seen:
        cols.append(bq)
    if not cols:
        cols = [base]
    cols.sort(key=lumin)
    return cols


def geo_pick(base, stamp, geo: float):
    """Pick a darker/lighter SOURCE palette color instead of multiplying RGB."""
    if abs(geo - 1.0) < 1e-3:
        return base
    palette = _stamp_palette(stamp, base)
    target = lumin(base) * geo
    return min(palette, key=lambda c: abs(lumin(c) - target))


def paint_tex(base, stamp, u: float, v: float, x: int, y: int,
              strength: float = 0.62, geo: float = 1.0):
    """Return exact source stamp RGB (v15). strength kept for call-site compat."""
    del strength  # no longer luma-mixes onto base
    tex = stamp_tex(stamp, u, v, x, y)
    c = tex if tex is not None else base
    return geo_pick(c, stamp, geo)


def remap_src_luma(base, src_rgb, mean_lum, strength=0.90, geo=1.0):
    """v15: keep the front-sheet pixel itself (source clusters). geo via palette."""
    del mean_lum, strength
    # Prefer the real source pixel; fall back to base only if src is unusable.
    if src_rgb is None or is_magenta(*src_rgb) or is_outline(*src_rgb):
        c = base
    else:
        c = src_rgb
    if abs(geo - 1.0) < 1e-3:
        return c
    # Discrete shade from {src, base, darkened base} — no continuous multiply.
    palette = [quant(c), quant(base), quant(darken(base, 0.82)), quant(darken(base, 0.70))]
    # also slightly lighter
    palette.append(tuple(min(255, int(v * 1.08)) for v in quant(base)))
    target = lumin(c) * geo
    return min(palette, key=lambda p: abs(lumin(p) - target))


def garment_geo(afx: float, fy: float, x: int, y: int, lum: int) -> float:
    """Light sleeve/fold/shoulder geometry (no V-neck, no speckles)."""
    del x, y
    geo = 1.0
    if lum > 600:
        if afx > 0.80:
            geo = 0.94
        elif afx < 0.10:
            geo = 0.97
    else:
        if afx > 0.80:
            geo = 0.90
        elif afx < 0.08:
            geo = 0.88
        elif fy < 0.12 and afx < 0.45:
            geo = 1.05
    return geo


_TEX_CACHE: dict = {}
_HAIR_CACHE: dict = {}
_GARMENT_CACHE: dict = {}


def _sheet_bbox_px(path: Path):
    if not path.exists():
        return None
    im = Image.open(path).convert("RGB")
    minx, miny, maxx, maxy = bbox(im)
    if maxx < minx:
        return None
    return im, minx, miny, maxx, maxy


def gather_hair_tex_samples(person: str):
    samples = []
    for name in (f"{person}_walk0.png", f"{person}_idle.png", f"{person}_sit.png"):
        path = SRC / name
        got = _sheet_bbox_px(path)
        if not got:
            continue
        im, minx, miny, maxx, maxy = got
        px = im.load()
        bh = maxy - miny + 1
        bw = maxx - minx + 1
        cx = (minx + maxx) / 2.0
        for y in range(miny, miny + max(12, int(bh * 0.22))):
            for x in range(minx, maxx + 1):
                r, g, b = px[x, y]
                if is_magenta(r, g, b) or is_outline(r, g, b):
                    continue
                if is_true_hat_red(r, g, b):
                    continue
                fx = abs(x - cx) / (bw / 2.0)
                if fx > 0.88:
                    continue
                fy = (y - miny) / float(bh)
                if fy > 0.10 and fx < 0.35 and is_skin(r, g, b) and (r - g) < 40:
                    continue
                if is_skin(r, g, b) and fx < 0.45 and fy > 0.12:
                    continue
                samples.append((r, g, b))
        if len(samples) > 6000:
            break
    return samples


def gather_garment_tex_samples(person: str):
    """Walk mid-torso sides — fabric folds/dither without pants or lap hands."""
    samples = []
    for name in (f"{person}_walk0.png", f"{person}_walk1.png", f"{person}_idle.png"):
        path = SRC / name
        got = _sheet_bbox_px(path)
        if not got:
            continue
        im, minx, miny, maxx, maxy = got
        px = im.load()
        bh = maxy - miny + 1
        bw = maxx - minx + 1
        cx = (minx + maxx) / 2.0
        for y0f, y1f in ((0.48, 0.66), (0.44, 0.58)):
            y0 = miny + int(bh * y0f)
            y1 = miny + int(bh * y1f)
            for y in range(y0, y1):
                for x in range(minx, maxx + 1):
                    r, g, b = px[x, y]
                    if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                        continue
                    fx = abs(x - cx) / (bw / 2.0)
                    if fx < 0.22 or fx > 0.90:
                        continue
                    if fx < 0.30 and r > 220 and g > 220 and b > 200:
                        continue
                    samples.append((r, g, b))
        if len(samples) > 8000:
            break
    return samples


def gather_pants_tex_samples(person: str):
    samples = []
    for name in (f"{person}_walk0.png", f"{person}_walk1.png", f"{person}_idle.png"):
        path = SRC / name
        got = _sheet_bbox_px(path)
        if not got:
            continue
        im, minx, miny, maxx, maxy = got
        px = im.load()
        bh = maxy - miny + 1
        bw = maxx - minx + 1
        cx = (minx + maxx) / 2.0
        for y0f, y1f in ((0.70, 0.84), (0.72, 0.86)):
            chunk = _gather_thigh(px, minx, miny, maxx, maxy, bh, bw, cx, y0f, y1f)
            samples.extend(chunk)
        if len(samples) > 6000:
            break
    return samples


def _spatial_boxes_for(person: str, kind: str):
    """Return (image, list of crop boxes) for spatially coherent stamps."""
    for name in (f"{person}_walk0.png", f"{person}_idle.png", f"{person}_sit.png"):
        got = _sheet_bbox_px(SRC / name)
        if not got:
            continue
        im, minx, miny, maxx, maxy = got
        bh = maxy - miny + 1
        bw = maxx - minx + 1
        cx = (minx + maxx) / 2.0
        boxes = []
        if kind == "hair":
            y0, y1 = miny, miny + max(12, int(bh * 0.20))
            x0 = int(cx - bw * 0.38)
            x1 = int(cx + bw * 0.38)
            boxes.append((x0, y0, x1, y1))
        elif kind == "shirt":
            y0 = miny + int(bh * 0.48)
            y1 = miny + int(bh * 0.66)
            # left + right side panels (back-ish fabric)
            boxes.append((minx + int(bw * 0.05), y0, int(cx - bw * 0.12), y1))
            boxes.append((int(cx + bw * 0.12), y0, maxx - int(bw * 0.05), y1))
        elif kind == "pants":
            y0 = miny + int(bh * 0.70)
            y1 = miny + int(bh * 0.84)
            boxes.append((int(cx - bw * 0.28), y0, int(cx + bw * 0.28), y1))
        if boxes:
            return im, boxes
    return None, []


def get_person_tex(person: str):
    if person in _TEX_CACHE:
        return _TEX_CACHE[person]
    # Prefer spatial crops from walk sheets; fall back to bag-of-pixels stamps.
    hair_im, hair_boxes = _spatial_boxes_for(person, "hair")
    shirt_im, shirt_boxes = _spatial_boxes_for(person, "shirt")
    pants_im, pants_boxes = _spatial_boxes_for(person, "pants")
    hair = build_spatial_stamp(hair_im, hair_boxes, 64, 64) if hair_boxes else None
    shirt = build_spatial_stamp(shirt_im, shirt_boxes, 80, 96) if shirt_boxes else None
    pants = build_spatial_stamp(pants_im, pants_boxes, 64, 80) if pants_boxes else None
    if hair is None:
        hair = build_tex_stamp(gather_hair_tex_samples(person), 56, 56)
    if shirt is None:
        shirt = build_tex_stamp(gather_garment_tex_samples(person), 72, 88)
    if pants is None:
        pants = build_tex_stamp(gather_pants_tex_samples(person), 56, 72)
    pack = {"hair": hair, "shirt": shirt, "pants": pants}
    _TEX_CACHE[person] = pack
    return pack


def is_true_hat_red(r, g, b) -> bool:
    """Ops-style baseball cap red — not blonde/orange hair."""
    return r > 150 and g < 70 and b < 70 and (r - g) > 70 and (r - b) > 70


def hair_shade(hair, fx: float, fy: float, x: int, y: int, crown_variants, stamp=None):
    """Hair from exact source stamp clusters + sparse crown variants (no sand)."""
    afx = abs(fx)
    u = (fx + 1.0) * 0.5
    v = max(0.0, min(1.0, fy))
    # Soft volume — fewer checkerboard toggles so clusters stay chunky.
    geo = 1.0
    if fy > 0.90:
        geo = 0.78
    elif fy > 0.82:
        geo = 0.90
    elif afx > 0.82:
        geo = 0.90
    elif fy < 0.16 and afx < 0.38:
        geo = 1.06
    if stamp is not None:
        c = paint_tex(hair, stamp, u, v, x, y, strength=0.0, geo=geo)
        # Sparse crown highlight from real lighter variants (blocky, not blend).
        if crown_variants and fy < 0.36 and ((x // 4) * 7 + (y // 4) * 3) % 11 == 0:
            return crown_variants[((x // 4) + (y // 4)) % len(crown_variants)]
        return c
    hd = darken(hair, 0.70)
    hm = darken(hair, 0.86)
    hl = tuple(min(255, int(v * 1.08)) for v in hair)
    if crown_variants and fy < 0.36 and ((x // 4) * 7 + (y // 4) * 3) % 9 == 0:
        return crown_variants[((x // 4) + (y // 4)) % len(crown_variants)]
    if fy > 0.90:
        return hd
    if fy > 0.82:
        return hm
    if afx > 0.82:
        return hm
    if fy < 0.16 and afx < 0.38 and ((x // 5) + (y // 5)) % 7 == 0:
        return hl
    if ((x // 5) * 3 + (y // 5)) % 8 == 0:
        return hm
    return hair


def hat_shade(hat, fx: float, fy: float, x: int, y: int):
    hd = darken(hat, 0.72)
    hm = darken(hat, 0.88)
    hl = tuple(min(255, int(v * 1.08)) for v in hat)
    if fy > 0.72:
        return hd
    if abs(fx) > 0.75:
        return hm
    if fy < 0.22 and abs(fx) < 0.35 and (x + y) % 9 == 0:
        return hl
    return hat if ((x + y) % 4) else hm


def _side_modes(samples, top_n=4):
    cnt: Counter = Counter()
    for r, g, b in samples:
        if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
            continue
        cnt[(r // 8 * 8, g // 8 * 8, b // 8 * 8)] += 1
    return cnt.most_common(top_n)


def sample_hair_from_sheets(person: str):
    """Hair (+ optional hat) from walk/idle/sit crown. Blonde not filtered as skin."""
    if person in _HAIR_CACHE:
        return _HAIR_CACHE[person]
    samples = []
    hat_samples = []
    for name in (f"{person}_walk0.png", f"{person}_idle.png", f"{person}_sit.png"):
        path = SRC / name
        if not path.exists():
            continue
        im = Image.open(path).convert("RGB")
        px = im.load()
        minx, miny, maxx, maxy = bbox(im)
        if maxx < minx:
            continue
        bh = maxy - miny + 1
        bw = maxx - minx + 1
        cx = (minx + maxx) / 2.0
        for y in range(miny, miny + max(10, int(bh * 0.18))):
            for x in range(minx, maxx + 1):
                r, g, b = px[x, y]
                if is_magenta(r, g, b) or is_outline(r, g, b):
                    continue
                fx = abs(x - cx) / (bw / 2.0)
                if fx > 0.85:
                    continue
                fy = (y - miny) / float(bh)
                # Skip pinkish face skin peeking under bangs; keep blonde hair.
                if fy > 0.10 and fx < 0.35 and is_skin(r, g, b) and (r - g) < 40:
                    continue
                if is_true_hat_red(r, g, b):
                    hat_samples.append((r, g, b))
                else:
                    samples.append((r, g, b))
    hair = mode_color(samples) if samples else (90, 60, 40)
    # Prefer a lighter blonde/highlight if it is common near the mode.
    if samples:
        cnt: Counter = Counter(quant(s) for s in samples)
        top = cnt.most_common(6)
        mode_n = top[0][1]
        for col, n in top:
            if n >= mode_n * 0.35 and sum(col) > sum(hair) + 40 and near(col, hair, 90):
                hair = col
                break
    uniq = []
    for c in samples:
        qc = quant(c)
        if near(qc, hair, 80) and qc not in uniq:
            uniq.append(qc)
        if len(uniq) >= 8:
            break
    crown = uniq or [hair, darken(hair, 0.82)]
    hat = None
    if len(hat_samples) > 200:
        hat = mode_color(hat_samples)
    _HAIR_CACHE[person] = (hair, crown, hat)
    return hair, crown, hat


def sample_garment_back(px, minx, miny, maxx, maxy, bh, bw, cx,
                         y0f=0.50, y1f=0.64):
    """Pick back-of-garment color from mid-torso side panels only.

    Walk sheets are preferred callers: mid-torso sides hold the coat/hoodie
    without pants, chair wood, or lap hands. Requires left+right agreement so
    center plackets / teal undershirts do not beat a white lab coat.
    """
    left, right, whites = [], [], []
    y0 = miny + int(bh * y0f)
    y1 = miny + int(bh * y1f)
    for y in range(y0, y1):
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                continue
            fx = (x - cx) / (bw / 2.0)
            if -0.78 <= fx <= -0.28:
                left.append((r, g, b))
                if r > 210 and g > 210 and b > 210:
                    whites.append((r, g, b))
            elif 0.28 <= fx <= 0.78:
                right.append((r, g, b))
                if r > 210 and g > 210 and b > 210:
                    whites.append((r, g, b))
    side_n = len(left) + len(right)
    if side_n and len(whites) / side_n > 0.18:
        return mode_color(whites) if whites else (240, 240, 240)

    lt = _side_modes(left)
    rt = _side_modes(right)
    if not lt and not rt:
        return (90, 60, 80)
    if not lt:
        return rt[0][0]
    if not rt:
        return lt[0][0]

    def side_frac(top, col):
        total = sum(n for _, n in top) or 1
        best = 0.0
        for c, n in top:
            if near(c, col, 40):
                best = max(best, n / total)
        return best

    candidates = [c for c, _ in lt[:3]] + [c for c, _ in rt[:3]]
    best, best_s = candidates[0], -1.0
    for col in candidates:
        s = side_frac(lt, col) + side_frac(rt, col)
        # Prefer mid-luminance garment over near-black outline bleed / pants.
        lum = sum(col)
        if lum < 90:
            s *= 0.35
        if s > best_s:
            best, best_s = col, s
    return best


def sample_garment_for_person(person: str, sit: Image.Image):
    """Sample garment from walk torso sides only (never pants / sit lap).

    Sit sheets fold the legs up so thigh/pants pixels invade the old 0.56–0.74
    torso window; walk mid-torso (0.50–0.64) is the reliable coat/shirt band.
    """
    if person in _GARMENT_CACHE:
        return _GARMENT_CACHE[person]
    for name in (f"{person}_walk0.png", f"{person}_walk1.png", f"{person}_idle.png"):
        path = SRC / name
        if not path.exists():
            continue
        im = Image.open(path).convert("RGB")
        px = im.load()
        minx, miny, maxx, maxy = bbox(im)
        if maxx < minx:
            continue
        bh = maxy - miny + 1
        bw = maxx - minx + 1
        cx = (minx + maxx) / 2.0
        for y0f, y1f in ((0.50, 0.64), (0.48, 0.60), (0.54, 0.66)):
            col = sample_garment_back(px, minx, miny, maxx, maxy, bh, bw, cx, y0f, y1f)
            if col != (90, 60, 80):
                _GARMENT_CACHE[person] = col
                return col
    # Last resort: sit mid-torso (higher / tighter than old 0.56–0.74).
    px = sit.load()
    minx, miny, maxx, maxy = bbox(sit)
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0
    col = sample_garment_back(px, minx, miny, maxx, maxy, bh, bw, cx, 0.48, 0.60)
    _GARMENT_CACHE[person] = col
    return col


def shirt_shade(shirt, afx: float, x: int, y: int, stamp=None, fy: float = 0.5):
    """Back garment with walk-torso luminance texture + light sleeve/fold shade."""
    # Soft volume only — no per-pixel checker speckles (those read as sand).
    geo = 1.0
    lum = sum(shirt)
    if lum > 600:
        if afx > 0.80:
            geo = 0.94
        elif afx < 0.10:
            geo = 0.97
    else:
        if afx > 0.80:
            geo = 0.90
        elif afx < 0.08:
            geo = 0.88
        elif fy < 0.12 and afx < 0.45:
            geo = 1.05
    if stamp is not None:
        # Map afx outward so sleeve edges pull different stamp columns.
        u = min(1.0, afx * 1.05)
        v = max(0.0, min(1.0, fy))
        return paint_tex(shirt, stamp, u, v, x, y, strength=0.0, geo=geo)
    # Fallback procedural (walk/idle soften path etc.)
    if lum > 600:
        d = darken(shirt, 0.95)
        m = darken(shirt, 0.98)
        if geo < 0.96:
            return d
        if afx > 0.70 and (x * 3 + y) % 8 == 0:
            return m
        return shirt
    shirt_d = darken(shirt, 0.86)
    shirt_m = darken(shirt, 0.93)
    shirt_l = tuple(min(255, int(v * 1.05)) for v in shirt)
    if geo <= 0.88:
        return shirt_d
    if afx > 0.72:
        return shirt_l if ((x * 3 + y) % 7 == 0) else shirt_m
    return shirt if ((x + y) % 6) else shirt_m


def rebuild_outline_band(op, minx, miny, maxx, maxy, w, h, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            if on_silhouette_edge(x, y, op, w, h):
                op[x, y] = (14, 12, 10)


def redraw_seated_head(out: Image.Image, src: Image.Image, person: str, frame: str = ""):
    """Full back-of-head redraw: textured hair (+ hat), no face, no torso paint."""
    op = out.load()
    px = src.load()
    w, h = out.size
    minx, miny, maxx, maxy = bbox(src)
    if maxx < minx:
        return miny, (90, 60, 40)
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0

    hair, crown, hat = sample_hair_from_sheets(person)
    hair_stamp = get_person_tex(person)["hair"] if person else None
    hair_mean = hair_stamp["mean"] if hair_stamp else max(1.0, lumin(hair))

    head_f, _torso_f = seated_bands(person, frame)
    head_bot = miny + int(bh * head_f)

    head_cx = cx
    head_cy = miny + bh * 0.26
    head_rx = bw * 0.52
    head_ry = bh * 0.28
    hat_bot = miny + int(bh * 0.34) if hat else -1

    for y in range(miny, head_bot + 1):
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, px, w, h):
                continue

            fx = (x - cx) / (bw / 2.0)
            fy_head = (y - miny) / max(1.0, float(head_bot - miny))
            nx = (x - head_cx) / head_rx
            ny = (y - head_cy) / head_ry
            dist = nx * nx + ny * ny
            in_mass = dist <= 1.28 or (y <= head_bot and abs(fx) < 0.95)
            if not in_mass:
                continue

            if hat and y < hat_bot and abs(fx) > 0.90 and fy_head < 0.55:
                op[x, y] = (255, 0, 255)
                continue

            head_fy = max(0.0, min(1.0, (y - (head_cy - head_ry)) / max(1.0, 2 * head_ry)))

            if hat and y <= hat_bot and fy_head < 0.72:
                hair_c = hair_shade(hair, fx, head_fy, x, y, crown, stamp=hair_stamp)
                hat_c = hat_shade(hat, fx, fy_head, x, y)
                if fy_head < 0.55 and abs(fx) < 0.72:
                    op[x, y] = hat_c
                    continue
                if fy_head < 0.68 and abs(fx) < 0.55:
                    t = (fy_head - 0.55) / 0.13
                    t = max(0.0, min(1.0, t))
                    op[x, y] = tuple(int(hat_c[i] * (1 - t) + hair_c[i] * t) for i in range(3))
                    continue

            sr, sg, sb = px[x, y]
            src_c = (sr, sg, sb)
            # v15: KEEP exact source hair clusters. Only invent fill over face/skin.
            is_facey = (
                is_skin(sr, sg, sb)
                or (sr > 195 and sg > 175 and sb > 140 and abs(fx) < 0.55)
                or (sr > 170 and sg < 150 and sb < 150 and (sr - sg) > 35)  # blush
                or (abs(sr - sg) < 28 and abs(sg - sb) < 28 and 80 < sr < 190 and abs(fx) < 0.55)
            )
            is_hairish = (
                not is_facey
                and not is_outline(sr, sg, sb)
                and not is_true_hat_red(sr, sg, sb)
                and (
                    near(src_c, hair, 90)
                    or lumin(src_c) < max(40.0, lumin(hair) * 1.60)
                )
            )
            if is_hairish:
                op[x, y] = src_c  # exact front-sheet hair pixel
                if abs(fx) > 0.82:
                    op[x, y] = geo_pick(src_c, hair_stamp, 0.90)
                elif head_fy > 0.88:
                    op[x, y] = geo_pick(src_c, hair_stamp, 0.86)
            else:
                op[x, y] = hair_shade(hair, fx, head_fy, x, y, crown, stamp=hair_stamp)

    # Scrub remaining face / glass / skin inside the head mass.
    for y in range(miny, head_bot + 1):
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, op, w, h):
                continue
            fx = (x - cx) / (bw / 2.0)
            if abs(fx) > 0.96:
                continue
            skinish = is_skin(r, g, b)
            bright = r > 195 and g > 175 and b > 140 and abs(fx) < 0.75
            blush = r > 170 and g < 150 and b < 150 and (r - g) > 35
            glass = abs(r - g) < 28 and abs(g - b) < 28 and 80 < r < 190 and abs(fx) < 0.72
            if not (skinish or bright or blush or glass):
                continue
            head_fy = max(0.0, min(1.0, (y - (head_cy - head_ry)) / max(1.0, 2 * head_ry)))
            fy_head = (y - miny) / max(1.0, float(head_bot - miny))
            if hat and y <= hat_bot and fy_head < 0.62:
                op[x, y] = hat_shade(hat, fx, fy_head, x, y)
            else:
                op[x, y] = hair_shade(hair, fx, head_fy, x, y, crown, stamp=hair_stamp)

    # Tiny nape tip only (1–2 px of darker hair) — never a tall helmet bar or
    # hair-colored yoke spilling into the shirt band.
    nape1 = head_bot + max(1, int(bh * 0.006))
    for y in range(head_bot - 1, nape1 + 1):
        for x in range(int(cx - bw * 0.18), int(cx + bw * 0.18) + 1):
            if x < minx or x > maxx:
                continue
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, op, w, h):
                continue
            afx = abs(x - cx) / (bw / 2.0)
            if afx > 0.18:
                continue
            if y <= head_bot:
                op[x, y] = darken(hair, 0.70)
            # below head_bot: leave for torso fill (no hair paint)

    rebuild_outline_band(op, minx, miny, maxx, maxy, w, h, miny, head_bot)
    return head_bot, hair


def redraw_seated_torso(out: Image.Image, src: Image.Image, head_bot: int, frame: str, person: str, hair) -> None:
    """Textured back-of-garment panel (luma stamp). No hair→shirt yoke."""
    op = out.load()
    px = src.load()
    w, h = out.size
    minx, miny, maxx, maxy = bbox(src)
    if maxx < minx:
        return
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0

    shirt = sample_garment_for_person(person, src)
    shirt_stamp = get_person_tex(person)["shirt"] if person else None
    shirt_mean = shirt_stamp["mean"] if shirt_stamp else max(1.0, lumin(shirt))
    shirt_d = darken(shirt, 0.90 if sum(shirt) > 600 else 0.84)
    shirt_m = darken(shirt, 0.95 if sum(shirt) > 600 else 0.92)

    _hf, torso_f = seated_bands(person, frame)
    torso_bot = miny + int(bh * torso_f)

    # Fill garment from the first pixel below hair — pure shirt, no hair mix.
    # Prefer remapping front-sheet luminance (folds/dither) onto the v13 shirt
    # hue; invent stamp texture only where front had skin / face / accents.
    for y in range(head_bot + 1, torso_bot + 1):
        fy = (y - head_bot) / max(1.0, float(torso_bot - head_bot))
        if fy < 0.12:
            max_afx = 0.76
        elif fy < 0.45:
            max_afx = 0.72
        elif fy < 0.75:
            max_afx = 0.66
        else:
            max_afx = 0.56
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, op, w, h):
                continue
            fx = (x - cx) / (bw / 2.0)
            afx = abs(fx)
            sr, sg, sb = px[x, y]
            src_c = (sr, sg, sb)
            geo = garment_geo(afx, fy, x, y, sum(shirt))
            usable = (
                not is_magenta(*src_c)
                and not is_outline(*src_c)
                and not is_skin(*src_c)
                and near(src_c, shirt, 78)  # keep shirt/coat family
                and not near(src_c, hair, 40)
            )
            if usable:
                # v15: keep exact front coat/shirt pixel; geo via discrete palette.
                fill = remap_src_luma(shirt, src_c, shirt_mean, strength=0.0, geo=geo)
            else:
                fill = None  # exact stamp RGB path below

            if afx > max_afx:
                if frame.startswith("type") and fy > 0.35 and afx < max_afx + 0.28:
                    if is_skin(r, g, b) or (r > 200 and g > 190 and b > 150):
                        if fill is None:
                            fill = shirt_shade(
                                shirt, afx, x, y, stamp=shirt_stamp, fy=fy
                            )
                        op[x, y] = fill
                        if afx >= max_afx + 0.14:
                            op[x, y] = darken(op[x, y], 0.93)
                elif is_skin(r, g, b) and afx < max_afx + 0.20 and fy < 0.90:
                    if fill is None:
                        fill = shirt_shade(
                            shirt, min(0.85, afx), x, y, stamp=shirt_stamp, fy=fy
                        )
                    op[x, y] = fill
                continue

            if fill is None:
                fill = shirt_shade(shirt, afx, x, y, stamp=shirt_stamp, fy=fy)
            op[x, y] = fill

    # Erase chair armrest / side-rail blobs left from front sit sheets.
    # Dark vertical stubs outside the garment silhouette read as wrong color.
    for y in range(head_bot + 1, torso_bot + 1):
        fy = (y - head_bot) / max(1.0, float(torso_bot - head_bot))
        max_afx = 0.76 if fy < 0.12 else (0.72 if fy < 0.45 else (0.66 if fy < 0.75 else 0.56))
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            afx = abs(x - cx) / (bw / 2.0)
            if afx < max_afx + 0.02:
                continue
            # Dark chair wood / metal outside the body → cut to magenta.
            if (r + g + b) < 90 and not near((r, g, b), shirt, 55):
                op[x, y] = (255, 0, 255)
                continue
            # Grey chair plastic outside body → cut.
            if afx > max_afx + 0.08 and abs(r - g) < 18 and abs(g - b) < 18 and 40 < r < 160:
                op[x, y] = (255, 0, 255)

    # One-pixel collar seam in darkened shirt (not hair) under the nape.
    collar_y = head_bot + 1
    if collar_y <= torso_bot:
        for x in range(int(cx - bw * 0.32), int(cx + bw * 0.32) + 1):
            if x < minx or x > maxx:
                continue
            r, g, b = op[x, collar_y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, collar_y, op, w, h):
                continue
            afx = abs(x - cx) / (bw / 2.0)
            if afx > 0.55:
                continue
            op[x, collar_y] = darken(
                shirt_shade(shirt, afx, x, collar_y, stamp=shirt_stamp, fy=0.0), 0.90
            )

    # Scrub skin / bright collar / drawstring / leftover hair in torso band.
    for y in range(head_bot + 1, torso_bot + 1):
        fy = (y - head_bot) / max(1.0, float(torso_bot - head_bot))
        for x in range(int(cx - bw * 0.52), int(cx + bw * 0.52) + 1):
            if x < minx or x > maxx:
                continue
            r, g, b = op[x, y]
            if is_magenta(r, g, b) or (is_outline(r, g, b) and on_silhouette_edge(x, y, op, w, h)):
                continue
            afx = abs(x - cx) / (bw / 2.0)
            if is_skin(r, g, b):
                op[x, y] = shirt_shade(shirt, afx, x, y, stamp=shirt_stamp, fy=fy)
            elif near((r, g, b), hair, 45) and not near((r, g, b), shirt, 50):
                # Kill hair bleed that survived into the garment band.
                op[x, y] = shirt_shade(shirt, afx, x, y, stamp=shirt_stamp, fy=fy)
            elif afx < 0.35 and r > 205 and g > 195 and b > 160 and not near((r, g, b), shirt, 55):
                if sum(shirt) < 550:
                    op[x, y] = shirt_shade(shirt, afx, x, y, stamp=shirt_stamp, fy=fy)

    rebuild_outline_band(op, minx, miny, maxx, maxy, w, h, head_bot, torso_bot)


def _gather_band(px, minx, miny, maxx, maxy, bh, bw, cx, y0f, y1f):
    out = []
    y0 = miny + int(bh * y0f)
    y1 = min(maxy + 1, miny + int(bh * y1f))
    for y in range(y0, y1):
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                continue
            if r > 235 and g > 235 and b > 235:
                continue
            fx = abs(x - cx) / (bw / 2.0)
            if fx < 0.12 or fx > 0.90:
                continue
            out.append((r, g, b))
    return out


def _gather_thigh(px, minx, miny, maxx, maxy, bh, bw, cx, y0f, y1f):
    """Thigh/pants pixels including center (shorts are often central).

    Do NOT use full is_skin() — khaki / tan pants (r≈g, muted) match the
    loose skin heuristic and would be dropped, leaving only shoe-brown.
    Only skip bright peach hand/face skin.
    """
    out = []
    y0 = miny + int(bh * y0f)
    y1 = min(maxy + 1, miny + int(bh * y1f))
    for y in range(y0, y1):
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b):
                continue
            # Bright peach only (hands / knees), keep khaki & grey pants.
            peach = r > 190 and g > 140 and b > 100 and (r - g) > 25
            if peach:
                continue
            if r > 235 and g > 235 and b > 235:
                continue
            fx = abs(x - cx) / (bw / 2.0)
            if fx > 0.72:
                continue
            out.append((r, g, b))
    return out


def sample_seated_leg_colors(person: str, sit: Image.Image):
    """Pants from walk thigh (incl. center); shoes from sit toe tips.

    Never reuse the shirt/coat color — that produced navy/teal 'pants' bands.
    """
    sp = sit.load()
    sminx, sminy, smaxx, smaxy = bbox(sit)
    sbh = smaxy - sminy + 1

    tip = []
    for y in range(smaxy - max(5, int(sbh * 0.045)), smaxy + 1):
        for x in range(sminx, smaxx + 1):
            r, g, b = sp[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                continue
            tip.append((r, g, b))
    tip_cnt: Counter = Counter(quant(s) for s in tip)
    shoes = tip_cnt.most_common(1)[0][0] if tip_cnt else (48, 32, 24)
    shoe_lum = sum(shoes)

    shirt = sample_garment_for_person(person, sit)

    walk_path = SRC / f"{person}_walk0.png"
    candidates = []
    if walk_path.exists():
        walk = Image.open(walk_path).convert("RGB")
        wp = walk.load()
        wminx, wminy, wmaxx, wmaxy = bbox(walk)
        wbh = wmaxy - wminy + 1
        wbw = wmaxx - wminx + 1
        wcx = (wminx + wmaxx) / 2.0
        for y0f, y1f, wmul in ((0.74, 0.82, 1.2), (0.72, 0.80, 1.05), (0.70, 0.78, 0.9)):
            samples = _gather_thigh(wp, wminx, wminy, wmaxx, wmaxy, wbh, wbw, wcx, y0f, y1f)
            if len(samples) < 40:
                continue
            cnt: Counter = Counter(quant(s) for s in samples)
            for col, n in cnt.most_common(10):
                if near(col, shirt, 50):
                    continue
                # Reject near-white coat leftovers and near-shoe clumps.
                lum = sum(col)
                if lum > 600:
                    continue
                d_shoe = cdist(col, shoes)
                if d_shoe < 30 and n < len(samples) * 0.20:
                    continue
                score = n * wmul
                # Prefer khaki / grey / olive pants (mid luminance, not neon).
                if 140 < lum < 520:
                    score *= 1.45
                if d_shoe > 50:
                    score *= 1.15
                # Slight preference for warmer khaki over cold navy bleed.
                r, g, b = col
                if r > g - 10 and g > b + 15 and r > 100:
                    score *= 1.2
                candidates.append((score, col))

    if candidates:
        candidates.sort(reverse=True)
        pants = candidates[0][1]
    else:
        sbw = smaxx - sminx + 1
        scx = (sminx + smaxx) / 2.0
        sit_thigh = _gather_thigh(sp, sminx, sminy, smaxx, smaxy, sbh, sbw, scx, 0.76, 0.88)
        filtered = [s for s in sit_thigh if not near(quant(s), shirt, 50)]
        cnt = Counter(quant(s) for s in (filtered or sit_thigh)) if sit_thigh else Counter()
        pants = cnt.most_common(1)[0][0] if cnt else (80, 80, 80)
        if near(pants, shirt, 50):
            pants = darken(shirt, 0.55)
    return pants, shoes


def pants_leg_shade(pants, fx: float, fy: float, x: int, y: int, stamp=None):
    afx = abs(fx)
    geo = 1.0
    if afx < 0.10 and fy > 0.20:
        geo = 0.84
    elif afx > 0.78:
        geo = 0.88
    elif fy > 0.82:
        geo = 0.88
    if stamp is not None:
        u = (fx + 1.0) * 0.5
        v = max(0.0, min(1.0, fy))
        return paint_tex(pants, stamp, u, v, x, y, strength=0.0, geo=geo)
    pd = darken(pants, 0.78)
    pm = darken(pants, 0.90)
    if geo <= 0.85:
        return pd
    if geo <= 0.91:
        return pm
    return pants


def shoe_heel_shade(shoes, nfx: float, t: float, x: int, y: int):
    lum = sum(shoes)
    shoe_d = darken(shoes, 0.42 if lum > 480 else 0.65)
    shoe_m = darken(shoes, 0.78 if lum > 480 else 0.86)
    shoe_l = tuple(min(255, int(v * 1.10)) for v in shoes)
    if t > 0.80:
        return shoe_d
    if nfx < 0.22:
        return shoe_d if ((x + y) % 3 == 0) else shoe_m
    if nfx > 0.82:
        return shoe_l if lum > 400 else shoes
    return shoes if ((x + y) % 4) else shoe_m


def redraw_seated_legs(out: Image.Image, src: Image.Image, frame: str, person: str) -> None:
    """Replace front sit/type legs with back-of-legs + heel silhouettes."""
    op = out.load()
    px = src.load()
    w, h = out.size
    minx, miny, maxx, maxy = bbox(src)
    if maxx < minx:
        return
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0
    pants, shoes = sample_seated_leg_colors(person, src)
    pants_stamp = get_person_tex(person)["pants"] if person else None
    pants_mean = pants_stamp["mean"] if pants_stamp else max(1.0, lumin(pants))
    mag = (255, 0, 255)

    _hf, torso_f = seated_bands(person, frame)
    leg_top = miny + int(bh * torso_f)

    shoe_top = miny + int(bh * 0.905)
    heel_bot = maxy - max(2, int(bh * 0.035))
    if heel_bot <= shoe_top + 4:
        shoe_top = heel_bot - max(6, int(bh * 0.04))

    for y in range(leg_top, shoe_top + 1):
        fy = (y - leg_top) / max(1.0, float(shoe_top - leg_top))
        max_afx = 0.82 - 0.22 * (fy ** 1.05)
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            fx = (x - cx) / (bw / 2.0)
            afx = abs(fx)
            if afx > max_afx:
                op[x, y] = mag
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, op, w, h):
                continue
            sr, sg, sb = px[x, y]
            src_c = (sr, sg, sb)
            if (
                not is_magenta(*src_c)
                and not is_outline(*src_c)
                and not (sr > 190 and sg > 140 and sb > 100 and (sr - sg) > 25)
                and not near(src_c, shoes, 35)
                and near(src_c, pants, 85)
            ):
                # v15: keep exact thigh/pants pixel; soft geo via palette only.
                afx = abs(fx)
                geo = 0.82 if (afx < 0.10 and fy > 0.20) else (
                    0.90 if afx > 0.75 else (0.88 if fy > 0.82 else 1.0)
                )
                op[x, y] = remap_src_luma(pants, src_c, pants_mean, strength=0.0, geo=geo)
            else:
                op[x, y] = pants_leg_shade(pants, fx, fy, x, y, stamp=pants_stamp)

    for y in range(leg_top, shoe_top + 1):
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            if on_silhouette_edge(x, y, op, w, h):
                op[x, y] = (14, 12, 10)

    seam_x0 = int(cx - max(1, bw * 0.02))
    seam_x1 = int(cx + max(1, bw * 0.02))
    for y in range(leg_top + int((shoe_top - leg_top) * 0.25), shoe_top - 2):
        for x in range(seam_x0, seam_x1 + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b):
                continue
            op[x, y] = darken(pants, 0.62)

    for y in range(shoe_top, maxy + 1):
        for x in range(minx, maxx + 1):
            if not is_magenta(*op[x, y]):
                op[x, y] = mag

    left_xs, right_xs = [], []
    for y in range(miny + int(bh * 0.88), maxy + 1):
        for x in range(minx, maxx + 1):
            if is_magenta(*px[x, y]):
                continue
            (left_xs if x < cx else right_xs).append(x)

    def paint_heel(xs, side):
        if not xs:
            return
        x0, x1 = min(xs), max(xs)
        raw_cx = (x0 + x1) / 2.0
        foot_cx = cx + (raw_cx - cx) * 0.55
        foot_hw = max(5.0, min(bw * 0.11, (x1 - x0) / 2.0 * 0.42))
        for y in range(shoe_top, heel_bot + 1):
            t = (y - shoe_top) / max(1.0, float(heel_bot - shoe_top))
            half = foot_hw * (0.95 - 0.55 * (t ** 1.1))
            painted = []
            for x in range(int(foot_cx - half - 1), int(foot_cx + half + 2)):
                if x < minx or x > maxx:
                    continue
                dx = abs(x - foot_cx)
                if dx > half:
                    continue
                op[x, y] = shoe_heel_shade(shoes, dx / max(1.0, half), t, x, y)
                painted.append(x)
            if painted:
                op[min(painted), y] = (12, 10, 8)
                op[max(painted), y] = (12, 10, 8)
            if t > 0.88 and painted:
                for x in painted:
                    op[x, y] = (12, 10, 8)
        cuff0 = shoe_top - max(2, int(bh * 0.012))
        for y in range(cuff0, shoe_top + 1):
            for x in range(int(foot_cx - foot_hw * 1.15), int(foot_cx + foot_hw * 1.15) + 1):
                if x < minx or x > maxx:
                    continue
                r, g, b = op[x, y]
                if is_magenta(r, g, b):
                    if y < shoe_top and abs(x - foot_cx) <= foot_hw * 0.95:
                        op[x, y] = darken(pants, 0.72)
                    continue
                if is_outline(r, g, b) and on_silhouette_edge(x, y, op, w, h):
                    continue
                if near((r, g, b), pants, 55) or y >= shoe_top:
                    op[x, y] = darken(pants, 0.68)

    paint_heel(left_xs, "L")
    paint_heel(right_xs, "R")


def soften_front_accents(op, minx, miny, maxx, maxy, bh, bw, cx, head_bot, shirt):
    """Walk/idle: gently lift bright drawstrings / logos toward shirt (no full redraw)."""
    torso_top = head_bot
    torso_bot = miny + int(bh * 0.68)
    for y in range(torso_top, torso_bot + 1):
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b):
                continue
            if is_skin(r, g, b):
                continue
            fx = abs(x - cx) / (bw / 2.0)
            if fx > 0.28:
                continue
            bright = r > 210 and g > 210 and b > 190
            very_contrast = abs(r - shirt[0]) + abs(g - shirt[1]) + abs(b - shirt[2]) > 140
            if bright and fx < 0.20:
                mix = 0.55
                op[x, y] = tuple(
                    int(r * (1 - mix) + shirt[i] * mix) for i, r in enumerate((r, g, b))
                )
            elif very_contrast and bright:
                op[x, y] = darken(shirt, 0.95)







def _hair_palette(px, minx, miny, maxx, maxy, bh, bw, cx, hair_mode):
    """Dominant quantized hair colors from the true crown (no face band)."""
    cnt = Counter()
    y1 = miny + max(10, int(bh * 0.18))
    for y in range(miny, y1):
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b):
                continue
            if is_true_hat_red(r, g, b):
                continue
            fx = abs(x - cx) / (bw / 2.0)
            if fx > 0.88:
                continue
            # Reject face skin unless it is actually the hair mode (blonde).
            if is_skin(r, g, b) and not near((r, g, b), hair_mode, 70):
                continue
            # Reject purple/magenta bleed and pure white.
            if r > 100 and b > 100 and g < r - 25 and g < b - 15:
                continue
            if r > 235 and g > 235 and b > 230:
                continue
            if near((r, g, b), hair_mode, 110) or (sum(hair_mode) < 120 and (r + g + b) < 140):
                cnt[quant((r, g, b))] += 1
    top = [c for c, n in cnt.most_common(12) if n >= 8]
    if not top:
        top = [quant(hair_mode), quant(darken(hair_mode, 0.82))]
    return top


def _nearest_palette(c, palette):
    return min(palette, key=lambda p: cdist(c, p))


def _build_crown_rows(px, minx, miny, maxx, hair_mode, atlas_h: int, palette):
    """Crown atlas using only palette-snapped hair pixels."""
    rows = []
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0
    for y in range(miny, miny + atlas_h):
        row = []
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b):
                row.append(None)
                continue
            if is_true_hat_red(r, g, b):
                row.append(None)
                continue
            fx = abs(x - cx) / max(1.0, bw / 2.0)
            if fx > 0.90:
                row.append(None)
                continue
            if is_skin(r, g, b) and not near((r, g, b), hair_mode, 70):
                row.append(None)
                continue
            if r > 100 and b > 100 and g < r - 25 and g < b - 15:
                row.append(None)
                continue
            if not near((r, g, b), hair_mode, 110) and not (
                sum(hair_mode) < 120 and (r + g + b) < 150
            ):
                row.append(None)
                continue
            row.append(_nearest_palette((r, g, b), palette))
        rows.append(row)
    return rows


def _atlas_nn(rows, u: float, v: float, fallback):
    if not rows:
        return fallback
    ah, aw = len(rows), len(rows[0])
    ix = int(max(0.0, min(0.999, u)) * aw)
    iy = int(max(0.0, min(0.999, v)) * ah)
    for rad in range(0, 18):
        for dy in range(-rad, rad + 1):
            for dx in range(-rad, rad + 1):
                if rad and abs(dx) != rad and abs(dy) != rad:
                    continue
                x, y = ix + dx, iy + dy
                if 0 <= y < ah and 0 <= x < aw and rows[y][x] is not None:
                    return rows[y][x]
    return fallback



# ---------------------------------------------------------------------------
# v16 seated back: NEAREST-scale front walk crops into seated-from-behind geo
# ---------------------------------------------------------------------------

_BACK_TEX: dict = {}


def _extract_crop(im: Image.Image, box, keep_fn, fill):
    """Crop box → RGB image; fill rejected cells from nearest kept / mode."""
    x0, y0, x1, y1 = box
    out = Image.new("RGB", (max(1, x1 - x0), max(1, y1 - y0)), (255, 0, 255))
    op = out.load()
    px = im.load()
    samples = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b) or not keep_fn((r, g, b), x, y):
                continue
            op[x - x0, y - y0] = (r, g, b)
            samples.append((r, g, b))
    mode = mode_color(samples) if samples else fill
    w, h = out.size
    for y in range(h):
        for x in range(w):
            if not is_magenta(*op[x, y]):
                continue
            found = None
            for d in range(1, max(w, h)):
                for xx in (x - d, x + d):
                    if 0 <= xx < w and not is_magenta(*op[xx, y]):
                        found = op[xx, y]
                        break
                if found:
                    break
                for yy in (y - d, y + d):
                    if 0 <= yy < h and not is_magenta(*op[x, yy]):
                        found = op[x, yy]
                        break
                if found:
                    break
            op[x, y] = found or mode
    return out, mode, samples


def _make_back_panel(side_crop: Image.Image) -> Image.Image:
    """Left side crop | mirrored left — back-of-coat without V-neck."""
    w, h = side_crop.size
    panel = Image.new("RGB", (w * 2, h), (255, 0, 255))
    panel.paste(side_crop, (0, 0))
    panel.paste(side_crop.transpose(Image.FLIP_LEFT_RIGHT), (w, 0))
    return panel


def _gather_seated_back_tex(person: str):
    """Contiguous FRONT walk0 crops for hair / coat-back / pants.

    Garment mode comes from sample_garment_for_person (walk side consensus).
    Crops are seeded with that mode so skin-hole flood cannot paint hair/purple
    into the coat panel.
    """
    walk_path = None
    for name in (f"{person}_walk0.png", f"{person}_idle.png", f"{person}_sit.png"):
        cand = SRC / name
        if cand.exists():
            walk_path = cand
            break
    if walk_path is None:
        return None
    walk = Image.open(walk_path).convert("RGB")
    sit_path = SRC / f"{person}_sit.png"
    sit = Image.open(sit_path).convert("RGB") if sit_path.exists() else walk
    minx, miny, maxx, maxy = bbox(walk)
    if maxx < minx:
        return None
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0

    shirt = sample_garment_for_person(person, sit)

    # --- hair crown ---
    hy1 = miny + max(18, int(bh * 0.22))
    hair_box = (int(cx - bw * 0.40), miny, int(cx + bw * 0.40), hy1)

    def hair_keep(c, x, y):
        return not is_skin(*c)

    hair_crop, hair, _ = _extract_crop(walk, hair_box, hair_keep, (90, 60, 40))

    def hair_keep2(c, x, y):
        if is_skin(*c) and not near(c, hair, 70):
            return False
        # keep hair family; drop shirt-colored crown leftovers
        if near(c, shirt, 40) and not near(c, hair, 55):
            return False
        return near(c, hair, 115) or (sum(c) < sum(hair) + 80 and not is_skin(*c))

    hair_crop, hair, _ = _extract_crop(walk, hair_box, hair_keep2, hair)
    # re-seed hair crop holes with hair mode (extract already fills; ensure no shirt)
    hp = hair_crop.load()
    for y in range(hair_crop.size[1]):
        for x in range(hair_crop.size[0]):
            if near(hp[x, y], shirt, 35) and not near(hp[x, y], hair, 50):
                hp[x, y] = hair

    # --- side garment: keep only near shirt mode; seed with shirt ---
    sy0 = miny + int(bh * 0.44)
    sy1 = miny + int(bh * 0.68)
    left_box = (minx + int(bw * 0.05), sy0, int(cx - bw * 0.12), sy1)
    x0, y0, x1, y1 = left_box
    side_crop = Image.new("RGB", (max(1, x1 - x0), max(1, y1 - y0)), shirt)
    sp = side_crop.load()
    wp = walk.load()
    kept = 0
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = wp[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                continue
            if near((r, g, b), hair, 45) and not near((r, g, b), shirt, 55):
                continue
            if near((r, g, b), shirt, 95) or (
                # bright coat / white lab also counts when shirt is light
                sum(shirt) > 500 and r > 190 and g > 190 and b > 190
            ):
                sp[x - x0, y - y0] = (r, g, b)
                kept += 1
    if kept < 40:
        # fall back to broader keep near shirt luma family
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b = wp[x, y]
                if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                    continue
                if abs(lumin((r, g, b)) - lumin(shirt)) < 55 and not near((r, g, b), hair, 40):
                    sp[x - x0, y - y0] = (r, g, b)
    # scrub leftover bright non-shirt accents toward shirt
    for y in range(side_crop.size[1]):
        for x in range(side_crop.size[0]):
            c = sp[x, y]
            if c[0] > 210 and c[1] > 200 and c[2] > 180 and not near(c, shirt, 70) and sum(shirt) < 500:
                sp[x, y] = shirt
    panel = _make_back_panel(side_crop)

    # --- pants from walk thighs; reject shirt/hair ---
    pants, shoes = sample_seated_leg_colors(person, sit)
    py0 = miny + int(bh * 0.70)
    py1 = miny + int(bh * 0.86)
    pants_box = (int(cx - bw * 0.28), py0, int(cx + bw * 0.28), py1)
    px0, py0b, px1, py1b = pants_box
    pants_crop = Image.new("RGB", (max(1, px1 - px0), max(1, py1b - py0b)), pants)
    pp = pants_crop.load()
    for y in range(py0b, py1b):
        for x in range(px0, px1):
            r, g, b = wp[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b) or is_skin(r, g, b):
                continue
            if near((r, g, b), shirt, 48) or near((r, g, b), shoes, 30):
                continue
            if sum((r, g, b)) > 600:
                continue
            if near((r, g, b), pants, 90) or abs(lumin((r, g, b)) - lumin(pants)) < 40:
                pp[x - px0, y - py0b] = (r, g, b)

    return {
        "hair_crop": hair_crop,
        "hair": hair,
        "panel": panel,
        "shirt": shirt,
        "pants_crop": pants_crop,
        "pants": pants,
        "shoes": shoes,
    }



def seated_back_tex(person: str):
    if person in _BACK_TEX:
        return _BACK_TEX[person]
    pack = _gather_seated_back_tex(person)
    _BACK_TEX[person] = pack
    return pack


def _blit_scaled(src_im, dest_op, mask_pts, dx0, dy0, dw, dh, fallback):
    """Fill mask with src. Prefer native-res TILING (no wide stretch bands).

    If the crop is already roughly as wide/tall as the dest, NEAREST-scale once.
    Otherwise tile at native resolution so side-panel clusters stay chunky.
    """
    if dw < 1 or dh < 1 or not mask_pts:
        return
    sw, sh = src_im.size
    # Stretch only when crop is already a sizable fraction of the dest (hair often is).
    stretch = (sw >= max(12, dw * 0.35) and sh >= max(12, dh * 0.35))
    if stretch:
        scaled = src_im.resize((dw, dh), Image.NEAREST)
        sp = scaled.load()
        for (x, y) in mask_pts:
            lx = x - dx0
            ly = y - dy0
            if 0 <= lx < dw and 0 <= ly < dh:
                c = sp[lx, ly]
                dest_op[x, y] = c if not is_magenta(*c) else fallback
        return
    # Tile native pixels — preserves walk-sheet cluster size.
    sp = src_im.load()
    for (x, y) in mask_pts:
        lx = (x - dx0) % sw
        ly = (y - dy0) % sh
        c = sp[lx, ly]
        dest_op[x, y] = c if not is_magenta(*c) else fallback


def seated_back_from_front(src: Image.Image, frame: str, person: str) -> Image.Image:
    """v16: seated facing desk — back of head/coat + heels toward camera.

    Texture from FRONT walk0 contiguous crops (NEAREST-scaled), not sandy stamps.
    """
    im = src.convert("RGB")
    out = im.copy()
    op = out.load()
    px = im.load()
    w, h = out.size
    minx, miny, maxx, maxy = bbox(im)
    if maxx < minx:
        return out
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0

    pack = seated_back_tex(person) if person else None
    if pack is None:
        # Fallback: old head scrub only if no walk sheet.
        hair, crown, hat = sample_hair_from_sheets(person) if person else ((90, 60, 40), [(90, 60, 40)], None)
        del crown, hat
        return out

    hair = pack["hair"]
    shirt = pack["shirt"]
    pants = pack["pants"]
    shoes = pack["shoes"]
    head_f, torso_f = seated_bands(person, frame)
    head_bot = miny + int(bh * head_f)
    torso_bot = miny + int(bh * torso_f)
    mag = (255, 0, 255)

    head_cx = cx
    head_cy = miny + bh * 0.26
    head_rx = bw * 0.50
    head_ry = bh * 0.27
    head_pts = []
    hx0, hy0, hx1, hy1 = maxx, maxy, minx, miny
    for y in range(miny, head_bot + 1):
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, px, w, h):
                continue
            fx = (x - cx) / (bw / 2.0)
            nx = (x - head_cx) / head_rx
            ny = (y - head_cy) / head_ry
            if nx * nx + ny * ny > 1.28 and not (abs(fx) < 0.94):
                continue
            head_pts.append((x, y))
            hx0 = min(hx0, x)
            hy0 = min(hy0, y)
            hx1 = max(hx1, x)
            hy1 = max(hy1, y)
    if head_pts:
        _blit_scaled(pack["hair_crop"], op, head_pts, hx0, hy0, hx1 - hx0 + 1, hy1 - hy0 + 1, hair)
        for (x, y) in head_pts:
            fy = (y - miny) / max(1.0, float(head_bot - miny))
            if fy > 0.34:
                continue
            r, g, b = px[x, y]
            if near((r, g, b), hair, 100) and not (
                is_skin(r, g, b) and abs(x - cx) / (bw / 2.0) < 0.38 and fy > 0.16
            ):
                op[x, y] = (r, g, b)
        for (x, y) in head_pts:
            r, g, b = op[x, y]
            fx = abs(x - cx) / (bw / 2.0)
            fy = (y - miny) / max(1.0, float(head_bot - miny))
            # Anything not hair-family inside the head mass → hair (kills face/shirt).
            if not near((r, g, b), hair, 95) and not (
                # allow true hat red for ops
                is_true_hat_red(r, g, b)
            ):
                op[x, y] = hair
                r, g, b = hair
            if fx > 0.80 or fy > 0.90:
                op[x, y] = darken(op[x, y], 0.88)
    for x in range(int(cx - bw * 0.14), int(cx + bw * 0.14) + 1):
        if minx <= x <= maxx and not is_magenta(*op[x, head_bot]):
            op[x, head_bot] = darken(hair, 0.72)

    torso_pts = []
    tx0, ty0, tx1, ty1 = maxx, maxy, minx, miny
    for y in range(head_bot + 1, torso_bot + 1):
        fy = (y - head_bot) / max(1.0, float(torso_bot - head_bot))
        max_afx = 0.74 if fy < 0.12 else (0.68 if fy < 0.45 else (0.60 if fy < 0.75 else 0.50))
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            afx = abs(x - cx) / (bw / 2.0)
            if afx > max_afx + 0.10:
                if (r + g + b) < 100 or (abs(r - g) < 18 and abs(g - b) < 18 and 40 < r < 160):
                    op[x, y] = mag
                continue
            if afx > max_afx:
                if (
                    is_skin(r, g, b)
                    or near((r, g, b), shirt, 90)
                    or near((r, g, b), hair, 55)
                    or near((r, g, b), pants, 90)
                ):
                    op[x, y] = darken(shirt, 0.92)
                continue
            torso_pts.append((x, y))
            tx0 = min(tx0, x)
            ty0 = min(ty0, y)
            tx1 = max(tx1, x)
            ty1 = max(ty1, y)
    if torso_pts:
        _blit_scaled(pack["panel"], op, torso_pts, tx0, ty0, tx1 - tx0 + 1, ty1 - ty0 + 1, shirt)
        for (x, y) in torso_pts:
            r, g, b = op[x, y]
            if near((r, g, b), hair, 40) or is_skin(r, g, b):
                op[x, y] = shirt
                r, g, b = shirt
            afx = abs(x - cx) / (bw / 2.0)
            if afx < 0.05:
                op[x, y] = darken((r, g, b), 0.86)
            elif afx > 0.70:
                op[x, y] = darken((r, g, b), 0.90)
            if afx < 0.22 and r > 205 and g > 195 and b > 170 and not near((r, g, b), shirt, 50):
                op[x, y] = shirt
    for x in range(int(cx - bw * 0.28), int(cx + bw * 0.28) + 1):
        if minx <= x <= maxx and not is_magenta(*op[x, head_bot + 1]):
            op[x, head_bot + 1] = darken(shirt, 0.88)

    # Typing frames often leave front lap hands / sleeves outside the back panel.
    # Scrub skin and far-side outliers in the seated torso band.
    scrub_bot = min(torso_bot + int(bh * 0.08), maxy)
    for y in range(head_bot + 1, scrub_bot + 1):
        fy = (y - head_bot) / max(1.0, float(torso_bot - head_bot))
        max_afx = 0.74 if fy < 0.12 else (0.68 if fy < 0.45 else (0.60 if fy < 0.75 else 0.50))
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            afx = abs(x - cx) / (bw / 2.0)
            if is_skin(r, g, b) and not near((r, g, b), hair, 70):
                if afx <= max_afx + 0.05:
                    op[x, y] = shirt
                else:
                    op[x, y] = mag
            elif afx > max_afx + 0.08 and not near((r, g, b), shirt, 70) and not near((r, g, b), pants, 70):
                op[x, y] = mag

    shoe_top = miny + int(bh * 0.900)
    heel_bot = maxy - max(3, int(bh * 0.04))
    if heel_bot <= shoe_top + 5:
        shoe_top = heel_bot - max(7, int(bh * 0.045))
    leg_pts = []
    lx0, ly0, lx1, ly1 = maxx, maxy, minx, miny
    for y in range(torso_bot + 1, shoe_top + 1):
        fy = (y - torso_bot) / max(1.0, float(shoe_top - torso_bot))
        max_afx = 0.78 - 0.30 * (fy ** 1.05)
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            afx = abs(x - cx) / (bw / 2.0)
            if afx > max_afx:
                op[x, y] = mag
                continue
            leg_pts.append((x, y))
            lx0 = min(lx0, x)
            ly0 = min(ly0, y)
            lx1 = max(lx1, x)
            ly1 = max(ly1, y)
    if leg_pts:
        _blit_scaled(pack["pants_crop"], op, leg_pts, lx0, ly0, lx1 - lx0 + 1, ly1 - ly0 + 1, pants)
        for (x, y) in leg_pts:
            r, g, b = op[x, y]
            if near((r, g, b), shirt, 40) or is_skin(r, g, b) or near((r, g, b), shoes, 28):
                op[x, y] = pants
                r, g, b = pants
            afx = abs(x - cx) / (bw / 2.0)
            fy = (y - torso_bot) / max(1.0, float(shoe_top - torso_bot))
            if afx < 0.06 and fy > 0.15:
                op[x, y] = darken((r, g, b), 0.75)
            elif afx > 0.68:
                op[x, y] = darken((r, g, b), 0.90)
    for y in range(shoe_top, maxy + 1):
        for x in range(minx, maxx + 1):
            if not is_magenta(*op[x, y]):
                op[x, y] = mag

    left_xs, right_xs = [], []
    for y in range(miny + int(bh * 0.86), maxy + 1):
        for x in range(minx, maxx + 1):
            if is_magenta(*px[x, y]):
                continue
            (left_xs if x < cx else right_xs).append(x)
    shoe_d = darken(shoes, 0.70)
    shoe_m = darken(shoes, 0.86)

    def paint_heel(xs):
        if not xs:
            return
        x0, x1 = min(xs), max(xs)
        foot_cx = cx + ((x0 + x1) / 2.0 - cx) * 0.48
        foot_hw = max(7.0, min(bw * 0.105, (x1 - x0) * 0.17))
        for y in range(shoe_top, heel_bot + 1):
            t = (y - shoe_top) / max(1.0, float(heel_bot - shoe_top))
            half = foot_hw * (1.02 - 0.15 * t)
            if t > 0.78:
                half *= 1.0 - (t - 0.78) / 0.22 * 0.40
            painted = []
            for x in range(int(foot_cx - half - 1), int(foot_cx + half + 2)):
                if x < minx or x > maxx:
                    continue
                dx = abs(x - foot_cx)
                if dx > half:
                    continue
                if t > 0.72 and dx > half * (1.20 - t * 0.4):
                    continue
                nfx = dx / max(1.0, half)
                if t > 0.90:
                    c = shoe_d
                elif nfx < 0.22:
                    c = shoe_d if ((x + y) % 2 == 0) else shoe_m
                else:
                    c = shoes if ((x // 2 + y // 2) % 2 == 0) else shoe_m
                op[x, y] = c
                painted.append(x)
            if painted:
                op[min(painted), y] = (12, 10, 8)
                op[max(painted), y] = (12, 10, 8)
            if t > 0.93 and painted:
                for x in painted:
                    op[x, y] = (12, 10, 8)
        cuff0 = shoe_top - max(2, int(bh * 0.014))
        for y in range(cuff0, shoe_top):
            for x in range(int(foot_cx - foot_hw * 1.05), int(foot_cx + foot_hw * 1.05) + 1):
                if x < minx or x > maxx:
                    continue
                if is_magenta(*op[x, y]) or near(op[x, y], pants, 60):
                    op[x, y] = darken(pants, 0.78)

    paint_heel(left_xs)
    paint_heel(right_xs)
    rebuild_outline_band(op, minx, miny, maxx, maxy, w, h, miny, heel_bot)
    return out



def sample_skin_tone(person: str, src: Image.Image):
    """Peach hand/face tone from sit sheet (fallback warm peach)."""
    samples = []
    for name in (f"{person}_sit.png", f"{person}_type0.png", f"{person}_walk0.png"):
        path = SRC / name
        if not path.exists():
            continue
        im = Image.open(path).convert("RGB")
        px = im.load()
        minx, miny, maxx, maxy = bbox(im)
        if maxx < minx:
            continue
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                r, g, b = px[x, y]
                if is_skin(r, g, b):
                    samples.append((r, g, b))
        if len(samples) > 200:
            break
    if not samples:
        px = src.load()
        minx, miny, maxx, maxy = bbox(src)
        for y in range(miny, maxy + 1):
            for x in range(minx, maxx + 1):
                r, g, b = px[x, y]
                if is_skin(r, g, b):
                    samples.append((r, g, b))
    return mode_color(samples) if samples else (232, 184, 144)


def sample_accent_collar(person: str, shirt):
    """Optional undershirt / inner collar distinct from outer garment."""
    # Character accents known from front sheets (lab coat → teal, etc.).
    accents = {
        "byte": (32, 120, 112),
        "kai": (88, 48, 120),
        "pixel": (40, 96, 56),
        "you": (48, 96, 168),
        "cursor": (200, 120, 72),
        "ops": (40, 36, 32),
    }
    acc = accents.get(person)
    if acc is None:
        return darken(shirt, 0.78)
    # If outer garment already matches accent family, deepen it instead.
    if near(acc, shirt, 55):
        return darken(shirt, 0.72)
    return acc


def typing_pose(frame: str):
    """Per-frame shoulder/hand offsets for back-facing typing.

    Returns (typing, left_shoulder_dy, right_shoulder_dy,
             left_hand_dy, right_hand_dy, shoulder_widen, lean_x).
    dy positive = down the sprite (toward heels).
    """
    if not frame.startswith("type"):
        return False, 0, 0, 0, 0, 0.0, 0.0
    # type0: left presses, right lifts; type1: opposite; type2: both mid press
    table = {
        "type0": (True, -1, 2, 3, -2, 0.04, -0.01),
        "type1": (True, 2, -1, -2, 3, 0.04, 0.01),
        "type2": (True, 0, 0, 1, 1, 0.05, 0.0),
    }
    return table.get(frame, (True, 0, 0, 0, 0, 0.04, 0.0))


def clean_seated_back(src: Image.Image, frame: str, person: str) -> Image.Image:
    """v21: desk-facing pixel template via seated_pixel_v21."""
    from seated_pixel_v21 import make_seated_back
    return make_seated_back(src, frame, person)


def make_back(src: Image.Image, frame: str = "", person: str = "") -> Image.Image:
    im = src.convert("RGB")
    w, h = im.size
    px = im.load()
    out = im.copy()
    op = out.load()
    minx, miny, maxx, maxy = bbox(im)
    if maxx < minx:
        return out
    bh = maxy - miny + 1
    bw = maxx - minx + 1
    cx = (minx + maxx) / 2.0

    seated = frame.startswith("sit") or frame.startswith("type")

    if seated:
        return clean_seated_back(src, frame, person)

    # --- Walk/idle v22: textured back-of-head (keep hair clusters; scrub face). ---
    # Prior light fill treated Byte blond as skin → flat tan "blank face" oval.
    head_depth = 0.44
    head_rx_mul = 0.46
    head_ry_mul = 0.24

    hair, crown, hat = sample_hair_from_sheets(person) if person else ((90, 60, 40), [(90, 60, 40)], None)
    hair_stamp = get_person_tex(person)["hair"] if person else None

    ss = []
    side = max(14, bw // 5)
    for y in range(miny + int(bh * 0.50), miny + int(bh * 0.60)):
        for x in list(range(minx, minx + side)) + list(range(maxx - side + 1, maxx + 1)):
            ss.append(px[x, y])
    shirt = mode_color(ss)

    head_cx = cx
    head_cy = miny + bh * 0.23
    head_rx = bw * head_rx_mul
    head_ry = bh * head_ry_mul
    head_bot = miny + int(bh * head_depth)
    hat_bot = miny + int(bh * 0.30) if hat else -1

    chin_y = head_bot
    for y in range(miny + int(bh * 0.20), miny + int(bh * 0.55)):
        for x in range(int(cx - bw * 0.18), int(cx + bw * 0.18) + 1):
            r, g, b = px[x, y]
            if is_skin(r, g, b):
                chin_y = max(chin_y, y + int(bh * 0.02))
    head_bot = max(head_bot, chin_y)

    for y in range(miny, head_bot + 1):
        for x in range(minx, maxx + 1):
            r, g, b = px[x, y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, px, w, h):
                continue
            nx = (x - head_cx) / head_rx
            ny = (y - head_cy) / head_ry
            dist = nx * nx + ny * ny
            fy = (y - miny) / float(bh)
            fx = (x - cx) / (bw / 2.0)
            in_mass = dist <= 1.28 or (y <= head_bot and abs(fx) < 0.95)
            if not in_mass:
                continue
            head_fy = max(0.0, min(1.0, (y - (head_cy - head_ry)) / max(1.0, 2 * head_ry)))
            fy_head = (y - miny) / max(1.0, float(head_bot - miny))

            if hat and y <= hat_bot and fy_head < 0.72 and abs(fx) < 0.72:
                op[x, y] = hat_shade(hat, fx, fy_head, x, y)
                continue

            src_c = (r, g, b)
            is_facey = (
                is_skin(r, g, b)
                or (r > 195 and g > 175 and b > 140 and abs(fx) < 0.55)
                or (r > 170 and g < 150 and b < 150 and (r - g) > 35)
                or (abs(r - g) < 28 and abs(g - b) < 28 and 80 < r < 190 and abs(fx) < 0.55)
            )
            is_hairish = (
                not is_facey
                and not is_outline(r, g, b)
                and not is_true_hat_red(r, g, b)
                and (
                    near(src_c, hair, 95)
                    or lumin(src_c) < max(40.0, lumin(hair) * 1.60)
                )
            )
            if is_hairish:
                # Keep exact front-sheet hair (bun / bangs / crown texture).
                op[x, y] = src_c
                if abs(fx) > 0.82:
                    op[x, y] = geo_pick(src_c, hair_stamp, 0.90) if hair_stamp else darken(src_c, 0.90)
                elif head_fy > 0.88:
                    op[x, y] = geo_pick(src_c, hair_stamp, 0.86) if hair_stamp else darken(src_c, 0.86)
            else:
                op[x, y] = hair_shade(hair, fx, head_fy, x, y, crown, stamp=hair_stamp)

    # Scrub any remaining face / glass / skin inside the head mass.
    for y in range(miny, head_bot + 1):
        for x in range(minx, maxx + 1):
            r, g, b = op[x, y]
            if is_magenta(r, g, b):
                continue
            if is_outline(r, g, b) and on_silhouette_edge(x, y, op, w, h):
                continue
            fx = (x - cx) / (bw / 2.0)
            if abs(fx) > 0.96:
                continue
            skinish = is_skin(r, g, b)
            bright = r > 195 and g > 175 and b > 140 and abs(fx) < 0.75
            blush = r > 170 and g < 150 and b < 150 and (r - g) > 35
            glass = abs(r - g) < 28 and abs(g - b) < 28 and 80 < r < 190 and abs(fx) < 0.72
            if not (skinish or bright or blush or glass):
                continue
            head_fy = max(0.0, min(1.0, (y - (head_cy - head_ry)) / max(1.0, 2 * head_ry)))
            fy_head = (y - miny) / max(1.0, float(head_bot - miny))
            if hat and y <= hat_bot and fy_head < 0.62:
                op[x, y] = hat_shade(hat, fx, fy_head, x, y)
            else:
                op[x, y] = hair_shade(hair, fx, head_fy, x, y, crown, stamp=hair_stamp)

    # Dark nape seam under hair mass (reads as back-of-head, not blank face).
    for y in range(head_bot, head_bot + max(2, int(bh * 0.025))):
        for x in range(int(cx - bw * 0.20), int(cx + bw * 0.20) + 1):
            if x < minx or x > maxx:
                continue
            r, g, b = op[x, y]
            if is_magenta(r, g, b) or is_outline(r, g, b):
                continue
            if is_skin(r, g, b) or near((r, g, b), hair, 60):
                op[x, y] = darken(hair, 0.62)

    soften_front_accents(op, minx, miny, maxx, maxy, bh, bw, cx, head_bot, shirt)
    return out


def main() -> None:
    written = 0
    for person in PEOPLE:
        for frame in FRAMES:
            path = SRC / f"{person}_{frame}.png"
            if not path.exists() and frame == "type2":
                # type2 is generator-only (no front sheet); reuse type0 palette.
                path = SRC / f"{person}_type0.png"
            if not path.exists():
                print("missing", path)
                continue
            back = make_back(Image.open(path), frame=frame, person=person)
            out = SRC / f"{person}_{frame}_back.png"
            back.save(out)
            written += 1
    print(f"wrote {written} back frames into {SRC}")


if __name__ == "__main__":
    main()
