#!/usr/bin/env python3
"""Generate per-character drink0/drink1 sip sprites from idle sheets.

Removes the hanging viewer-right arm, inpaints the body side, then paints a
bent sleeve + cream mug at chest (drink0) or mouth (drink1). Steam puffs are
baked into drink1. Writes 1024x1024 magenta-backed sheets alongside other
character frames in media/sprites/chars/.
"""
from __future__ import annotations

from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "media" / "sprites" / "chars"
PEOPLE = ["you", "byte", "kai", "ops", "pixel", "cursor"]

# Sleeve / skin targets — kai uses cream sleeves over the purple vest.
PALETTES = {
    "you": {
        "shirt": (40, 56, 120),
        "shirt_dark": (24, 36, 80),
        "shirt_light": (72, 96, 168),
        "skin": (248, 200, 152),
        "skin_dark": (216, 152, 112),
    },
    "byte": {
        "shirt": (245, 246, 248),
        "shirt_dark": (190, 198, 205),
        "shirt_light": (255, 255, 255),
        "skin": (248, 200, 152),
        "skin_dark": (216, 152, 112),
    },
    "kai": {
        "shirt": (240, 232, 210),
        "shirt_dark": (200, 190, 160),
        "shirt_light": (255, 248, 230),
        "skin": (248, 200, 152),
        "skin_dark": (216, 152, 112),
    },
    "ops": {
        "shirt": (184, 40, 40),
        "shirt_dark": (120, 24, 24),
        "shirt_light": (220, 70, 70),
        "skin": (248, 200, 152),
        "skin_dark": (216, 152, 112),
    },
    "pixel": {
        "shirt": (40, 88, 48),
        "shirt_dark": (24, 56, 32),
        "shirt_light": (72, 130, 80),
        "skin": (248, 200, 152),
        "skin_dark": (216, 152, 112),
    },
    "cursor": {
        "shirt": (56, 60, 64),
        "shirt_dark": (32, 36, 40),
        "shirt_light": (90, 96, 100),
        "skin": (248, 200, 152),
        "skin_dark": (216, 152, 112),
    },
}

MUG_BODY = (242, 237, 228)
MUG_SHADE = (212, 200, 184)
MUG_COFFEE = (74, 42, 24)
MUG_OUTLINE = (42, 34, 24)
OUTLINE = (18, 14, 20)
STEAM = (236, 230, 222)


def key_mask(arr: np.ndarray) -> np.ndarray:
    r = arr[:, :, 0].astype(int)
    g = arr[:, :, 1].astype(int)
    b = arr[:, :, 2].astype(int)
    return (r > 130) & (b > 130) & (g < r) & (g < b) & ((r + b - 2 * g) > 50)


def content_bbox(arr: np.ndarray) -> tuple[int, int, int, int]:
    mag = key_mask(arr)
    ys, xs = np.where(~mag)
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def sample_magenta(arr: np.ndarray) -> tuple[int, int, int]:
    mag = key_mask(arr)
    pix = arr[mag]
    return tuple(int(x) for x in np.median(pix.astype(np.float32), axis=0))


def color_dist(arr: np.ndarray, color: tuple[int, int, int]) -> np.ndarray:
    return np.sqrt(
        ((arr.astype(np.float32) - np.array(color, dtype=np.float32)) ** 2).sum(axis=-1)
    )


def remove_right_arm(arr: np.ndarray, bbox: tuple[int, int, int, int], pal: dict) -> np.ndarray:
    """Remove hanging viewer-right arm; inpaint body side; outer → magenta."""
    x0, y0, x1, y1 = bbox
    w, h = x1 - x0 + 1, y1 - y0 + 1
    mag = np.array(sample_magenta(arr), dtype=np.uint8)
    out = arr.copy()
    orig = arr

    ax0 = x0 + int(w * 0.68)
    ay0 = y0 + int(h * 0.37)
    ay1 = y0 + int(h * 0.78)

    erasable = np.zeros(arr.shape[:2], dtype=bool)
    for c, tol in [
        (pal["shirt"], 50),
        (pal["shirt_dark"], 50),
        (pal["shirt_light"], 50),
        (pal["skin"], 45),
        (pal["skin_dark"], 45),
        (OUTLINE, 35),
        ((0, 0, 0), 35),
        ((1, 1, 1), 35),
        ((2, 2, 2), 35),
    ]:
        erasable |= color_dist(arr, c) < tol
    erasable |= (arr.astype(int).sum(axis=-1) < 55) & ~key_mask(arr)

    box = np.zeros_like(erasable)
    box[ay0 : ay1 + 1, ax0 : x1 + 1] = True
    erasable &= box

    yy = np.arange(arr.shape[0])[:, None]
    xx = np.arange(arr.shape[1])[None, :]
    fy = (yy - y0) / float(h)
    min_x = x0 + (0.78 - 0.08 * np.clip((fy - 0.40) / 0.35, 0, 1)) * w
    erasable &= xx >= min_x

    seeds: list[tuple[int, int]] = []
    for y in range(ay0, ay1 + 1):
        row_nonmag = ~key_mask(arr[y : y + 1, :])[0]
        xs = np.where(row_nonmag & (np.arange(arr.shape[1]) >= ax0))[0]
        if len(xs) == 0:
            continue
        x = int(xs.max())
        for x2 in range(x, max(ax0, x - 25), -1):
            if erasable[y, x2]:
                seeds.append((y, x2))
                break

    visited = np.zeros_like(erasable)
    q: deque[tuple[int, int]] = deque()
    for y, x in seeds:
        if erasable[y, x] and not visited[y, x]:
            visited[y, x] = True
            q.append((y, x))
    while q:
        y, x = q.popleft()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (1, -1), (-1, 1), (-1, -1)):
            ny, nx = y + dy, x + dx
            if ny < ay0 or ny > ay1 or nx < ax0 or nx > x1:
                continue
            if visited[ny, nx] or not erasable[ny, nx]:
                continue
            visited[ny, nx] = True
            q.append((ny, nx))

    visited = ndimage.binary_dilation(visited, iterations=1)

    body_edge = x0 + int(w * 0.78)
    ys, xs = np.where(visited)
    for y, x in zip(ys, xs):
        if x < body_edge + int(w * 0.04):
            found = False
            for sx in range(x - int(w * 0.06), x0 + int(w * 0.45), -1):
                if sx < 0:
                    break
                if (not visited[y, sx]) and (not key_mask(orig[y : y + 1, sx : sx + 1])[0, 0]):
                    out[y, x] = orig[y, sx]
                    found = True
                    break
            if not found:
                out[y, x] = mag
        else:
            out[y, x] = mag
    return out


def paint_ellipse(px: np.ndarray, cx: int, cy: int, rx: int, ry: int, color) -> None:
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            if rx <= 0 or ry <= 0:
                continue
            if ((x - cx) / float(rx)) ** 2 + ((y - cy) / float(ry)) ** 2 <= 1.05:
                if 0 <= x < px.shape[1] and 0 <= y < px.shape[0]:
                    px[y, x] = color


def paint_line(px: np.ndarray, x0: int, y0: int, x1: int, y1: int, color, r: int) -> None:
    steps = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(steps + 1):
        t = i / steps
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        paint_ellipse(px, x, y, r, r, color)


def paint_rect(px: np.ndarray, x0: int, y0: int, x1: int, y1: int, color) -> None:
    xa, xb = sorted((max(0, x0), min(px.shape[1] - 1, x1)))
    ya, yb = sorted((max(0, y0), min(px.shape[0] - 1, y1)))
    px[ya : yb + 1, xa : xb + 1] = color


def paint_arm_mug(arr: np.ndarray, bbox: tuple[int, int, int, int], pal: dict, raised: bool) -> None:
    x0, y0, x1, y1 = bbox
    w, h = x1 - x0 + 1, y1 - y0 + 1
    sx = x0 + int(w * 0.695)
    sy = y0 + int(h * 0.40)
    if not raised:
        # drink0 — mug at upper chest / collarbone
        ex, ey = x0 + int(w * 0.84), y0 + int(h * 0.50)
        hx, hy = x0 + int(w * 0.74), y0 + int(h * 0.52)
    else:
        # drink1 — mug at mouth
        ex, ey = x0 + int(w * 0.83), y0 + int(h * 0.355)
        hx, hy = x0 + int(w * 0.66), y0 + int(h * 0.295)

    sleeve, sleeve_d, sleeve_l = pal["shirt"], pal["shirt_dark"], pal["shirt_light"]
    skin, skin_d = pal["skin"], pal["skin_dark"]
    arm_r = max(9, int(w * 0.038))

    paint_line(arr, sx, sy, ex, ey, OUTLINE, arm_r + 3)
    paint_line(arr, ex, ey, hx, hy, OUTLINE, arm_r + 2)
    paint_line(arr, sx, sy, ex, ey, sleeve_d, arm_r + 1)
    paint_line(arr, ex, ey, hx, hy, sleeve_d, arm_r)
    paint_line(arr, sx, sy, ex, ey, sleeve, max(5, arm_r - 2))
    paint_line(arr, ex, ey, hx, hy, sleeve, max(4, arm_r - 3))
    paint_line(arr, sx, sy - 2, (sx + ex) // 2, (sy + ey) // 2 - 2, sleeve_l, max(2, arm_r // 3))

    hr = max(7, int(w * 0.026))
    paint_ellipse(arr, hx, hy, hr + 2, hr + 2, OUTLINE)
    paint_ellipse(arr, hx, hy, hr, hr, skin)
    paint_ellipse(arr, hx + 2, hy + 3, max(2, hr // 3), max(2, hr // 3), skin_d)

    mw = max(16, int(w * 0.09))
    mh = max(14, int(h * 0.058))
    if not raised:
        mx = hx - int(mw * 0.55)
        my = hy - int(mh * 0.95)
    else:
        mx = x0 + int(w * 0.55)
        my = y0 + int(h * 0.24)

    paint_rect(arr, mx - 2, my - 2, mx + mw + 2, my + mh + 2, OUTLINE)
    paint_rect(arr, mx, my, mx + mw, my + mh, MUG_BODY)
    paint_rect(
        arr,
        mx + int(mw * 0.12),
        my + int(mh * 0.12),
        mx + int(mw * 0.88),
        my + int(mh * 0.38),
        MUG_COFFEE,
    )
    paint_rect(
        arr,
        mx + int(mw * 0.70),
        my + int(mh * 0.16),
        mx + int(mw * 0.90),
        my + int(mh * 0.86),
        MUG_SHADE,
    )
    hx0 = mx + mw
    hy0 = my + int(mh * 0.22)
    hw = max(5, int(mw * 0.28))
    hh = max(6, int(mh * 0.48))
    paint_rect(arr, hx0, hy0, hx0 + hw, hy0 + max(2, hh // 5), MUG_OUTLINE)
    paint_rect(arr, hx0 + hw - max(2, hw // 4), hy0, hx0 + hw, hy0 + hh, MUG_OUTLINE)
    paint_rect(arr, hx0, hy0 + hh - max(2, hh // 5), hx0 + hw, hy0 + hh, MUG_OUTLINE)
    paint_rect(arr, mx, my, mx + mw, my + max(2, mh // 8), MUG_OUTLINE)

    if raised:
        for dx, dy, rr in [(0.28, -0.35, 0.09), (0.48, -0.58, 0.07), (0.38, -0.82, 0.055)]:
            paint_ellipse(
                arr,
                mx + int(mw * dx),
                my + int(mh * dy),
                max(2, int(mw * rr)),
                max(2, int(mw * rr)),
                STEAM,
            )


def make_drink(person: str, raised: bool) -> Image.Image:
    pal = PALETTES[person]
    arr = np.array(Image.open(SRC / f"{person}_idle.png").convert("RGB"))
    bbox = content_bbox(arr)
    out = remove_right_arm(arr, bbox, pal)
    paint_arm_mug(out, bbox, pal, raised)
    return Image.fromarray(out)


def main() -> None:
    for person in PEOPLE:
        for raised, tag in [(False, "drink0"), (True, "drink1")]:
            im = make_drink(person, raised)
            path = SRC / f"{person}_{tag}.png"
            im.save(path)
            print(f"wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
