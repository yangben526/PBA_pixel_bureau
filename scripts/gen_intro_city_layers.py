#!/usr/bin/env python3
"""Cut the startup-cutscene plate into animated element sprites.

Reads media/sprites/cutscene/intro_city.jpg (1672x941 coastal aerial)
and regenerates every animated matter as a clean sprite:

  intro_city_base.jpg  full plate with the ships/wheel/wakes inpainted
  intro_ship_cruise.png / intro_ship_ferrya.png / intro_ship_ferryb.png
  intro_wheel_rim.png  rotating rim + spokes + cabins (hub = measured center)
  intro_wheel_legs.png static A-frame legs
  intro_cloud_puff0/1/2.png  soft foreground cloud blobs

All rects are fixed template-space coords (the plate never changes), the
fill is deterministic patch-match sea, and every output is also mirrored
under media/sprites/cutscene/ as the art source of truth. Prints the
measured hull bboxes / lane centers / wheel hub for the Kotlin side.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image
import numpy as np
from scipy import ndimage as _nd

ROOT = Path(__file__).resolve().parents[1]
MEDIA = ROOT / "media" / "sprites" / "cutscene"
PLATE = MEDIA / "intro_city.jpg"
RES = ROOT / "app/src/main/res/drawable-nodpi"

# (name, hull bbox, wake inpaint rect, hull length cap, min row pixels).
# Bboxes are generous; sea matting finds the exact hull. All ships sail
# left; wakes trail right.
SHIPS = [
    ("cruise", (1088, 562, 1200, 652), (1178, 605, 1242, 672), 100, 8, (1176, 604)),
    ("ferrya", (994, 494, 1070, 544), (1066, 505, 1104, 548), 82, 8),
    ("ferryb", (938, 550, 990, 594), (986, 560, 1040, 625), 36, 5),
]

WHEEL_BBOX = (1050, 283, 1105, 352)  # rim + legs + margin
WHEEL_HUB_GUESS = (1077, 313)

PUFFS = [
    (30, 690, 290, 880),
    (300, 700, 470, 900),
    (0, 845, 450, 941),
]


def _largest(blob: np.ndarray) -> np.ndarray:
    lab, n = _nd.label(blob)
    if n == 0:
        return blob
    sizes = _nd.sum(blob, lab, range(1, n + 1))
    return lab == (int(np.argmax(sizes)) + 1)


def sea_mask(rgb: np.ndarray) -> np.ndarray:
    """Blue-dominant water (glitter and foam excluded by brightness)."""
    r = rgb[:, :, 0].astype(int)
    g = rgb[:, :, 1].astype(int)
    b = rgb[:, :, 2].astype(int)
    mx = np.maximum(np.maximum(r, g), b)
    return (b - r > 12) & (b - g > -6) & (mx < 190)


def matt_ship(rgb: np.ndarray, bbox, hull_len: int, min_row: int, stern_cut=None):
    """Alpha matting for a hull: not-sea mass shaped like a hull.

    Dark waterlines joined by adjacency; foam discarded by row rules
    (hull rows are long and solid, foam rows thin and trailing).
    [stern_cut] is an optional plate-space (x, y): nothing right of x
    and below y survives (stern wash cut). Returns (alpha, tight_bbox,
    waterline_rows).
    """
    from PIL.ImageFilter import GaussianBlur
    x0, y0, x1, y1 = bbox
    crop = rgb[y0:y1, x0:x1].astype(int)
    mx = crop.max(axis=2)
    ship = ~sea_mask(crop)
    if stern_cut is not None:
        sx, sy = stern_cut[0] - x0, stern_cut[1] - y0
        ship[sy:, sx:] = False
    ship |= (mx < 110) & _nd.binary_dilation(ship, iterations=4)
    ship = _nd.binary_closing(ship, structure=np.ones((3, 3)))
    main = _largest(ship)
    keep = ship & _nd.binary_dilation(main, structure=np.ones((7, 7)))
    # Row rules: solid hull rows only (foam streaks are sparse),
    # capped at hull length from the bow side.
    h, w = keep.shape
    rows = np.zeros_like(keep)
    for yy in range(h):
        xs = np.nonzero(keep[yy])[0]
        if len(xs) < min_row:
            continue
        lo, hi = int(xs.min()), int(xs.max())
        if len(xs) / max(1, hi - lo + 1) < 0.5:
            continue
        rows[yy, lo:lo + hull_len] = keep[yy, lo:lo + hull_len]
    keep = _nd.binary_fill_holes(rows)
    ys, xs = np.nonzero(keep)
    tight = (x0 + int(xs.min()), y0 + int(ys.min()), x0 + int(xs.max()) + 1, y0 + int(ys.max()) + 1)
    waterline = {}
    for xx in range(w):
        col = np.nonzero(keep[:, xx])[0]
        if len(col):
            waterline[x0 + xx] = y0 + int(col.max())
    alpha = np.zeros((y1 - y0, x1 - x0), dtype=np.uint8)
    alpha[keep] = 255
    alpha = np.array(Image.fromarray(alpha).filter(GaussianBlur(0.8)))
    return alpha, tight, waterline


def patch_inpaint(rgb: np.ndarray, mask: np.ndarray, window: int = 64, step: int = 4) -> np.ndarray:
    """Deterministic patch-match fill for masked pixels.

    Scores donor offsets by SSD over the mask's border ring so the fill
    matches the surrounding water, then pastes with a feathered blend.
    """
    out = rgb.copy()
    ring = _nd.binary_dilation(mask, iterations=5) & ~_nd.binary_dilation(mask, iterations=1)
    if not np.any(ring):
        return out
    h, w = mask.shape
    cand = range(-window, window + 1, step)
    scored = []
    ry, rx = np.nonzero(ring)
    for dy in cand:
        for dx in cand:
            if dx == 0 and dy == 0:
                continue
            sy, sx = ry + dy, rx + dx
            valid = (sy >= 0) & (sy < h) & (sx >= 0) & (sx < w)
            if valid.sum() < 40:
                continue
            # Donor pixels must be mostly unmasked clean sea.
            dirty = mask[sy[valid], sx[valid]]
            if dirty.mean() > 0.25:
                continue
            ok = valid.copy()
            ok[valid] = ~dirty
            if ok.sum() < 40:
                continue
            d = rgb[ry[ok], rx[ok]].astype(float) - rgb[sy[ok], sx[ok]].astype(float)
            scored.append((float((d * d).sum() / ok.sum()), dy, dx))
    if not scored:
        return out
    # The donor block itself must be clean too, or ship pixels get
    # copied back over the hole.
    scored.sort()
    ys, xs = np.nonzero(mask)
    best = None
    for _, dy, dx in scored:
        sy = np.clip(ys + dy, 0, h - 1)
        sx = np.clip(xs + dx, 0, w - 1)
        if mask[sy, sx].mean() < 0.05:
            best = (dy, dx)
            break
    if best is None:
        return out
    dy, dx = best
    ys, xs = np.nonzero(mask)
    sy = np.clip(ys + dy, 0, h - 1)
    sx = np.clip(xs + dx, 0, w - 1)
    dist = _nd.distance_transform_edt(mask)
    blend = np.clip(dist[ys, xs] / 5.0, 0.0, 1.0)[:, None]
    src = rgb[sy, sx].astype(float)
    dst = out[ys, xs].astype(float)
    out[ys, xs] = (dst * (1 - blend) + src * blend).astype(np.uint8)
    return out


def extract_wheel(rgb: np.ndarray):
    """Split the ferris wheel into rotating rim and static legs.

    Thin bright structures (rim, spokes, legs) come from a white
    top-hat so broad pale buildings and island green stay behind;
    the rim ring itself is rebuilt per angle for a clean spin.
    Returns (rim_rgba, legs_rgba, hub_xy, clean_bg_rgb_for_bbox).
    """
    from PIL.ImageFilter import GaussianBlur
    x0, y0, x1, y1 = WHEEL_BBOX
    crop = rgb[y0:y1, x0:x1].astype(int)
    ch, cw = crop.shape[:2]
    lum = (0.299 * crop[:, :, 0] + 0.587 * crop[:, :, 1] + 0.114 * crop[:, :, 2])
    opened = _nd.grey_opening(lum, size=(5, 5))
    thin = (lum - opened) > 16
    mx = crop.max(axis=2)
    mn = crop.min(axis=2)
    bright = (mx > 150) & (mx - mn < 90)
    # Fit the rim circle from thin ring pixels for an exact hub.
    yy, xx = np.nonzero(thin)
    gx, gy = WHEEL_HUB_GUESS[0] - x0, WHEEL_HUB_GUESS[1] - y0
    d0 = np.hypot(xx - gx, yy - gy)
    ring = (d0 > 12) & (d0 < 30)
    px, py = xx[ring].astype(float), yy[ring].astype(float)
    # Algebraic circle fit (Kasa).
    xym = np.stack([px, py, np.ones_like(px)], axis=1)
    b = -(px * px + py * py)
    sol, *_ = np.linalg.lstsq(xym, b, rcond=None)
    hx, hy = -sol[0] / 2.0, -sol[1] / 2.0
    radius = float(np.sqrt((sol[0] ** 2 + sol[1] ** 2) / 4 - sol[2]))
    hub = (x0 + int(round(hx)), y0 + int(round(hy)))
    YY, XX = np.meshgrid(np.arange(ch), np.arange(cw), indexing="ij")
    dist = np.hypot(XX - hx, YY - hy)
    ang = (np.degrees(np.arctan2(YY - hy, XX - hx)) + 360) % 360
    # Rebuilt ring: per-angle median of bright band pixels, gaps bridged.
    band = (np.abs(dist - radius) < 3.0) & (bright | thin)
    ring_col = np.zeros((180, 3), float)
    ring_has = np.zeros(180, bool)
    for i in range(180):
        sel = band & (ang >= i * 2) & (ang < (i + 1) * 2)
        thinsel = sel & thin
        # Thin structure first (the rim itself); pale buildings and
        # island green behind it only as a fallback.
        use = thinsel if np.any(thinsel) else sel
        if np.any(use):
            ring_col[i] = np.median(crop[use].reshape(-1, 3), axis=0)
            ring_has[i] = True
    for i in range(180):
        if not ring_has[i]:
            # Nearest sampled color on each side, however far: the ring is
            # continuous by construction.
            jm = next((j for j in range(1, 180) if ring_has[(i - j) % 180]), None)
            jp = next((j for j in range(1, 180) if ring_has[(i + j) % 180]), None)
            if jm is not None and jp is not None:
                wm, wp = jp / (jm + jp), jm / (jm + jp)
                ring_col[i] = ring_col[(i - jm) % 180] * wm + ring_col[(i + jp) % 180] * wp
                ring_has[i] = True
    rim_rgb = np.zeros((ch, cw, 3), np.uint8)
    rim_a = np.zeros((ch, cw), np.uint8)
    bandfull = np.abs(dist - radius) < 2.2
    ai = ((ang[bandfull] / 2).astype(int) % 180)
    rim_rgb[bandfull] = ring_col[ai].astype(np.uint8)
    rim_a[bandfull] = np.where(ring_has[ai], 255, 0).astype(np.uint8)
    # Spokes + hub + cabins keep original pixels.
    spokes = thin & (dist > 4) & (dist < radius - 2)
    hubm = dist < 5
    if np.any(bright & hubm):
        hubcol = np.median(crop[bright & hubm].reshape(-1, 3), axis=0)
    else:
        hubcol = np.median(crop[hubm].reshape(-1, 3), axis=0)
    rim_rgb[hubm] = hubcol.astype(np.uint8)
    rim_a[hubm] = 255
    rim_rgb[spokes] = np.clip(crop[spokes], 0, 255).astype(np.uint8)
    rim_a[spokes] = 255
    cablab, n = _nd.label(bright)
    cabmask = np.zeros((ch, cw), bool)
    for i in range(1, n + 1):
        cab = cablab == i
        if 6 <= cab.sum() <= 90:
            dd = dist[cab]
            if np.any((dd > radius - 7) & (dd < radius + 11)):
                rim_rgb[cab] = np.clip(crop[cab], 0, 255).astype(np.uint8)
                rim_a[cab] = 255
                cabmask |= cab
    rim = np.zeros((ch, cw, 4), np.uint8)
    rim[:, :, :3] = rim_rgb
    rim[:, :, 3] = np.array(Image.fromarray(rim_a).filter(GaussianBlur(0.5)))
    # Legs: thin structure in the A-frame below the rim. Only tall
    # strokes reaching up toward the hub survive (island speckle and
    # the shore line are short or disconnected).
    legs = thin & (YY > hy + 2) & (dist > radius + 1)
    legs &= np.abs(XX - hx) < (YY - hy) * 0.42 + 3
    leglab, ln = _nd.label(legs)
    keep_legs = np.zeros_like(legs)
    for i in range(1, ln + 1):
        comp = leglab == i
        ys, xs = np.nonzero(comp)
        tall = ys.max() - ys.min() >= 9
        near = np.hypot(xs - hx, ys - hy).min() < radius + 5
        if tall and near:
            keep_legs |= comp
    legs = _nd.binary_dilation(keep_legs, iterations=1)
    legs_rgba = np.zeros((ch, cw, 4), np.uint8)
    legs_rgba[:, :, :3] = np.clip(crop, 0, 255).astype(np.uint8)
    a = np.zeros((ch, cw), np.uint8)
    a[legs] = 255
    legs_rgba[:, :, 3] = np.array(Image.fromarray(a).filter(GaussianBlur(0.5)))

    def to_rgba(sel):
        a = np.zeros((ch, cw), np.uint8)
        a[sel] = 255
        rgba = np.zeros((ch, cw, 4), np.uint8)
        rgba[:, :, :3] = np.clip(crop, 0, 255).astype(np.uint8)
        rgba[:, :, 3] = a
        return rgba

    # Clean backdrop: row-wise blend of the flanking strips.
    left = rgb[y0:y1, x0 - 8:x0].astype(float).mean(axis=1)
    right = rgb[y0:y1, x1:x1 + 8].astype(float).mean(axis=1)
    t = np.linspace(0, 1, cw)[None, :, None]
    bg = (left[:, None, :] * (1 - t) + right[:, None, :] * t).astype(np.uint8)
    # Only the swept disc + legs + cabins need the clean backdrop; the
    # rest of the bbox keeps the original buildings and island.
    inmask = (dist < radius + 4) | legs | cabmask
    inmask = _nd.binary_dilation(inmask, iterations=2)
    return rim, legs_rgba, hub, bg, float(radius), inmask


def extract_puff(rgb: np.ndarray, bbox) -> Image.Image:
    x0, y0, x1, y1 = bbox
    crop = rgb[y0:y1, x0:x1].astype(int)
    lum = 0.299 * crop[:, :, 0] + 0.587 * crop[:, :, 1] + 0.114 * crop[:, :, 2]
    alpha = np.clip((lum - 135) / 55.0, 0.0, 1.0)
    # Clouds are near-neutral; saturated rooftops, hills and cranes drop out.
    sat = crop.max(axis=2) - crop.min(axis=2)
    alpha *= np.clip((70 - sat) / 40.0, 0.0, 1.0)
    # Border feather so any rect reads as a soft blob.
    h, w = alpha.shape
    fy = np.minimum(np.arange(h)[:, None], np.arange(h)[::-1][:, None]) / 15.0
    fx = np.minimum(np.arange(w)[None, :], np.arange(w)[::-1][None, :]) / 15.0
    alpha *= np.clip(np.minimum(fy, fx), 0.0, 1.0)
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[:, :, :3] = np.clip(crop, 0, 255).astype(np.uint8)
    rgba[:, :, 3] = (alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba)


def save_both(im: Image.Image, name: str, **kw):
    im.save(RES / name, **kw)
    im.save(MEDIA / name, **kw)


def main() -> None:
    MEDIA.mkdir(parents=True, exist_ok=True)
    plate = Image.open(PLATE).convert("RGB")
    rgb = np.array(plate)
    h, w = rgb.shape[:2]
    assert (w, h) == (1672, 941), plate.size
    base = rgb.copy()

    print("ships:")
    for entry in SHIPS:
        name, bbox, wake, hull_len, min_row = entry[:5]
        stern_cut = entry[5] if len(entry) > 5 else None
        alpha, tight, waterline = matt_ship(rgb, bbox, hull_len, min_row, stern_cut)
        x0, y0, x1, y1 = bbox
        rgba = np.zeros((y1 - y0, x1 - x0, 4), np.uint8)
        rgba[:, :, :3] = rgb[y0:y1, x0:x1]
        rgba[:, :, 3] = alpha
        # Tighten to content + 2px margin.
        tx0, ty0, tx1, ty1 = tight
        m = 2
        tx0, ty0 = max(x0, tx0 - m), max(y0, ty0 - m)
        tx1, ty1 = min(x1, tx1 + m), min(y1, ty1 + m)
        sprite = Image.fromarray(rgba[ty0 - y0:ty1 - y0, tx0 - x0:tx1 - x0])
        save_both(sprite, f"intro_ship_{name}.png")
        cx, cy = (tx0 + tx1) / 2.0, (ty0 + ty1) / 2.0
        print(f"  {name}: tight=({tx0},{ty0},{tx1},{ty1}) center=({cx:.1f},{cy:.1f})")
        # Inpaint hull (+shadow band below the waterline) and the wake.
        hull_m = np.zeros((h, w), bool)
        hull_m[y0:y1, x0:x1] = alpha > 128
        hull_m = _nd.binary_dilation(hull_m, iterations=3)
        for sx, wl in waterline.items():
            hull_m[wl:min(h, wl + 15), sx] = True
        hull_m = _nd.binary_dilation(hull_m, iterations=2)
        wx0, wy0, wx1, wy1 = wake
        wake_m = np.zeros((h, w), bool)
        wake_m[wy0:wy1, wx0:wx1] = True
        # Never eat island green or foreground cloud inside wake rects.
        g = base[:, :, 1].astype(int)
        r = base[:, :, 0].astype(int)
        b = base[:, :, 2].astype(int)
        island = (g - r > 12) & (g - b > -10)
        cloud = np.minimum(np.minimum(r, g), b) > 195
        wake_m &= ~island & ~cloud
        # Hull and wake filled separately so donors stay small and clean.
        base = patch_inpaint(base, hull_m)
        base = patch_inpaint(base, wake_m)

    rim, legs, hub, wheel_bg, radius, wheel_inmask = extract_wheel(rgb)
    save_both(Image.fromarray(rim), "intro_wheel_rim.png")
    save_both(Image.fromarray(legs), "intro_wheel_legs.png")
    print(f"wheel: hub={hub} radius={radius:.1f}")
    x0, y0, x1, y1 = WHEEL_BBOX
    sub = base[y0:y1, x0:x1]
    sub[wheel_inmask] = wheel_bg[wheel_inmask]

    for i, bbox in enumerate(PUFFS):
        x0, y0, x1, y1 = bbox
        save_both(extract_puff(rgb, bbox), f"intro_cloud_puff{i}.png")
        print(f"  puff{i}: size=({x1 - x0},{y1 - y0})")

    save_both(Image.fromarray(base), "intro_city_base.jpg", quality=92)

    # QA previews: before/after around every edit, side by side.
    rows = [
        (938, 494, 1245, 700),
        (WHEEL_BBOX[0] - 40, WHEEL_BBOX[1] - 40, WHEEL_BBOX[0] + 250, WHEEL_BBOX[1] + 180),
    ]
    strips = []
    for x0, y0, x1, y1 in rows:
        before = plate.crop((x0, y0, x1, y1))
        after = Image.fromarray(base[y0:y1, x0:x1])
        pair = Image.new("RGB", (before.width * 2 + 8, before.height), (255, 0, 255))
        pair.paste(before, (0, 0))
        pair.paste(after, (before.width + 8, 0))
        sc = 1600 / pair.width
        strips.append(pair.resize((1600, int(pair.height * sc)), Image.LANCZOS))
    qa = Image.new("RGB", (1600, sum(s.height for s in strips) + 8), (20, 20, 24))
    y = 0
    for s in strips:
        qa.paste(s, (0, y))
        y += s.height + 8
    qa.save("/tmp/cut_qa_base.png")
    print("wrote sprites + /tmp/cut_qa_base.png")


if __name__ == "__main__":
    main()
