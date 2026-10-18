#!/usr/bin/env python3
"""Extract endless-scroll parallax layers from the Pixegle hero banner.

portal_hero.png (2172x724) -> portal_hero_{base,clouds,city}.png

- base: sky + Pixegle letters + distant haze (static).
- clouds: white puffs above the skyline, tileable full-width strip.
- city: near buildings + trees, tileable full-width strip.

Only fully-opaque layer pixels are inpainted (per-row haze tones);
everything else keeps original pixels, so the rest frame composites back
EXACTLY (asserted). Deterministic; no arguments.
"""

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

DRAWABLE = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"
SRC = DRAWABLE / "portal_hero.png"

CITY_TOP = 300
CLOUD_MAX_Y = 360
CLOUD_MIN_PX = 2500


def flood(seed, ok):
    """Pixels connected to seed through ok==True (8-connectivity)."""
    h, w = ok.shape
    f = seed & ok
    prev = -1
    while f.sum() != prev:
        prev = f.sum()
        p = np.zeros((h + 2, w + 2), bool)
        p[1:-1, 1:-1] = f
        grown = (
            p[1:-1, 1:-1] | p[:-2, :-2] | p[:-2, 1:-1] | p[:-2, 2:]
            | p[1:-1, :-2] | p[1:-1, 2:] | p[2:, :-2] | p[2:, 1:-1] | p[2:, 2:]
        )
        f = grown & ok
    return f


def label_sizes(mask):
    """8-conn BFS component sizes."""
    h, w = mask.shape
    seen = np.zeros_like(mask)
    sizes = []
    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or seen[sy, sx]:
                continue
            q = deque([(sy, sx)])
            seen[sy, sx] = True
            n = 0
            while q:
                y, x = q.popleft()
                n += 1
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            q.append((ny, nx))
            sizes.append(n)
    return sizes


def filter_and_fill(mask, min_size):
    """Drop components below min_size; fill enclosed holes per component."""
    h, w = mask.shape
    seen = np.zeros_like(mask)
    out = np.zeros_like(mask)
    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or seen[sy, sx]:
                continue
            q = deque([(sy, sx)])
            seen[sy, sx] = True
            cells = []
            while q:
                y, x = q.popleft()
                cells.append((y, x))
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            q.append((ny, nx))
            if len(cells) < min_size:
                continue
            for y, x in cells:
                out[y, x] = True
            ys = [c[0] for c in cells]
            xs = [c[1] for c in cells]
            y0, y1 = max(0, min(ys) - 1), min(h, max(ys) + 2)
            x0, x1 = max(0, min(xs) - 1), min(w, max(xs) + 2)
            sub = out[y0:y1, x0:x1]
            bg_seed = np.zeros_like(sub)
            bg_seed[0, :] = bg_seed[-1, :] = True
            bg_seed[:, 0] = bg_seed[:, -1] = True
            out[y0:y1, x0:x1] |= ~sub & ~flood(bg_seed, ~sub)
    return out


def main():
    src = Image.open(SRC).convert("RGB")
    W, H = src.size
    a = np.asarray(src).astype(np.int16)
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
    ssum = r + g + b
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    yy, _ = np.mgrid[0:H, 0:W]

    # Sky reachable from the top edge; letters are detached and stay in base.
    clearly_sky = (b > 235) & (ssum > 505) & ((mx - mn) > 40)
    seed = np.zeros_like(clearly_sky)
    seed[0] = clearly_sky[0]
    sky = flood(seed, clearly_sky)

    # City: non-sky below CITY_TOP, ground-connected. Distant haze touching
    # near buildings moves with them (coherent); detached haze stays static.
    # Letters are detached and stay in base automatically.
    solid = ~sky & (yy >= CITY_TOP)
    gseed = np.zeros_like(solid)
    gseed[-1] = solid[-1]
    assert gseed.sum() > 0, "no ground seeds"
    city = flood(gseed, solid)
    print(f"city px: {int(city.sum())}")

    col_top = np.full(W, H)
    ys_idx, xs_idx = np.where(city)
    for x in range(W):
        col = ys_idx[xs_idx == x]
        if len(col):
            col_top[x] = col.min()

    # Clouds: bright cores above their column's roofline, plus adjacent
    # blue-tinted fringe bodies (letter highlights/glow die by size filter).
    xx = np.mgrid[0:H, 0:W][1]
    key = (mn > 170) & ((mx - mn) < 70) & (yy < CLOUD_MAX_Y)
    key = key & (yy < col_top[xx] - 3)
    print(f"cloud component sizes: {sorted(label_sizes(key))}")
    key = filter_and_fill(key, CLOUD_MIN_PX)
    # Grow cores through non-sky (dark outlines) and bright skirt, bounded to
    # a halo around the cores; alpha follows whiteness outside the region.
    fringe_zone = np.asarray(Image.fromarray(key).filter(ImageFilter.MaxFilter(129)))
    letters_hi = (xx >= 540) & (xx < 1620) & (yy >= 195) & (yy < 360)
    bounds = fringe_zone & (yy < CLOUD_MAX_Y) & (yy < col_top[xx] - 3) & ~letters_hi
    bright = (ssum > 600) & ((mx - mn) < 95) & (b > 200)
    region = flood(key, (~clearly_sky | bright) & bounds)
    # Close dithered edges: absorb near-cloud speckles that flood can't reach.
    closed = np.asarray(
        Image.fromarray(
            np.asarray(Image.fromarray(region).filter(ImageFilter.MaxFilter(17)))
        ).filter(ImageFilter.MinFilter(17))
    )
    region = region | (closed & ~region & ~clearly_sky & bounds)
    wide = np.asarray(Image.fromarray(region).filter(ImageFilter.MaxFilter(7)))
    wide = wide & (yy < col_top[xx]) & ~letters_hi
    ramp = np.clip((mn - 130) / (215 - 130), 0, 1)
    cloud_alpha = np.where(region, 255, (np.where(wide, ramp * 255, 0))).astype(np.uint8)
    key = region
    print(f"cloud px: {int(key.sum())}")

    eroded = np.asarray(Image.fromarray(city).filter(ImageFilter.MinFilter(3)))
    city_alpha = np.where(eroded, 255, np.where(city, 128, 0)).astype(np.uint8)

    # Per-row haze tones from clear sky; inpaint opaque-only (rest-exact).
    clear = sky & ~wide
    tones = np.zeros((H, 3), np.uint8)
    have = np.zeros(H, bool)
    for y in range(H):
        sel = clear[y]
        if sel.any():
            tones[y] = np.median(a[y][sel], axis=0)
            have[y] = True
    assert have.any(), "no clear sky for tones"
    last = tones[have][0]
    for y in range(H):
        if have[y]:
            last = tones[y]
        else:
            tones[y] = last
    def interior_weight(opaque, depth=8):
        """0 at the opaque boundary -> 1 deep inside (for seamless inpaint)."""
        w = np.zeros(opaque.shape, np.float32)
        m = opaque.copy()
        for i in range(1, depth + 1):
            m = np.asarray(Image.fromarray(m).filter(ImageFilter.MinFilter(3)))
            w[m] = i / depth
        w[opaque & (w == 0)] = 1 / depth
        return w

    base = a.copy().astype(np.float32)
    city_opaque = city_alpha == 255
    w = interior_weight(city_opaque)
    ys, xs = np.where(city_opaque)
    base[ys, xs] = (1 - w[ys, xs])[:, None] * a[ys, xs] + w[ys, xs][:, None] * tones[ys]
    cloud_opaque = cloud_alpha == 255
    w = interior_weight(cloud_opaque)
    ys, xs = np.where(cloud_opaque)
    base[ys, xs] = (1 - w[ys, xs])[:, None] * a[ys, xs] + w[ys, xs][:, None] * tones[ys]
    base = np.round(base).astype(np.uint8)

    Image.fromarray(base.astype(np.uint8)).save(DRAWABLE / "portal_hero_base.png")
    Image.fromarray(np.dstack([a, cloud_alpha]).astype(np.uint8)).save(DRAWABLE / "portal_hero_clouds.png")
    Image.fromarray(np.dstack([a, city_alpha]).astype(np.uint8)).save(DRAWABLE / "portal_hero_city.png")

    comp = Image.fromarray(base.astype(np.uint8)).convert("RGBA")
    comp.alpha_composite(Image.open(DRAWABLE / "portal_hero_city.png"))
    comp.alpha_composite(Image.open(DRAWABLE / "portal_hero_clouds.png"))
    diff = sum(1 for x, y in zip(comp.convert("RGB").tobytes(), src.tobytes()) if x != y)
    print(f"rest diff bytes: {diff}")
    assert diff == 0, "rest frame must equal source exactly"


main()
