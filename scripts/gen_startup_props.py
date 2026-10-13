#!/usr/bin/env python3
"""Generate startup-office isometric prop sprites (magenta-keyed) for PBA_pixel_bureau.

HQ pass: draw at 4× native resolution with denser shading/detail, then pack the
non-magenta content to fill ~92% of a 1024 canvas via nearest-neighbor (matches
desk/chair/server fill so SpriteStore inSampleSize=4 stays crisp).
"""
from __future__ import annotations

import math
import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "media" / "sprites" / "props"
PREVIEW = ROOT / ".sprite_preview" / "startup_props"
MAGENTA = (255, 0, 255, 255)
OUTLINE = (18, 14, 22, 255)

# Draw at 2× then NEAREST-pack to fill 1024 (matches desk/chair occupancy so
# SpriteStore inSampleSize=4 stays crisp). Higher HI = denser micro-detail.
HI = 2
CANVAS = 1024 * HI  # 2048
OUT_SIZE = 1024
PACK_FILL = 0.92


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def shade(rgb, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb[:3]) + (255,)


def iso_pt(gx, gy, gz, ox, oy, s):
    """Map grid (x right, y back, z up) to screen pixels. 2:1 iso."""
    sx = ox + (gx - gy) * s
    sy = oy + (gx + gy) * (s * 0.5) - gz * s
    return sx, sy


def ow(base=2):
    """Outline width scaled for HI-res canvas."""
    return max(2, int(base * HI * 0.85))


def draw_iso_box(draw, gx, gy, gz, w, d, h, ox, oy, s, top, left, right, outline=OUTLINE, lw=None):
    """Draw an isometric box. gx,gy,gz = origin of min corner; w,d,h extents."""
    if lw is None:
        lw = ow(2)

    def P(x, y, z):
        return iso_pt(gx + x, gy + y, gz + z, ox, oy, s)

    t00 = P(0, 0, h)
    t10 = P(w, 0, h)
    t11 = P(w, d, h)
    t01 = P(0, d, h)
    b10 = P(w, 0, 0)
    b11 = P(w, d, 0)
    b01 = P(0, d, 0)

    draw.polygon([t10, t11, b11, b10], fill=right)
    draw.polygon([t01, t11, b11, b01], fill=left)
    draw.polygon([t00, t10, t11, t01], fill=top)
    draw.line([t00, t10, t11, t01, t00], fill=outline, width=lw)
    draw.line([t10, b10], fill=outline, width=lw)
    draw.line([t11, b11], fill=outline, width=lw)
    draw.line([t01, b01], fill=outline, width=lw)
    draw.line([b10, b11, b01], fill=outline, width=lw)
    return {"t00": t00, "t10": t10, "t11": t11, "t01": t01, "b10": b10, "b11": b11, "b01": b01}


def stipple_top(im, poly, rgb, step=None, amp=10):
    """Sparse pixel noise on a top face for micro-texture (no blur)."""
    if step is None:
        step = max(3, HI)
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    minx, maxx = int(min(xs)), int(max(xs))
    miny, maxy = int(min(ys)), int(max(ys))
    # Use a mask via draw
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).polygon(poly, fill=255)
    px = im.load()
    mx = mask.load()
    for y in range(miny, maxy + 1, step):
        for x in range(minx, maxx + 1, step):
            if 0 <= x < im.width and 0 <= y < im.height and mx[x, y] > 128:
                # deterministic hash
                h = ((x * 374761393 + y * 668265263) ^ (x * y)) & 255
                if h > 200:
                    f = 1.0 + ((h - 200) / 55.0 - 0.5) * (amp / 40.0)
                    px[x, y] = shade(rgb, f)[:3] + (255,)


def new_canvas(size=CANVAS):
    return Image.new("RGBA", (size, size), MAGENTA)


def pack_to_out(im: Image.Image) -> Image.Image:
    """Crop non-magenta and NEAREST-pack into OUT_SIZE magenta canvas."""
    rgba = np.asarray(im)
    alpha = rgba[:, :, 3]
    rgb = rgba[:, :, :3].copy()
    # Composite onto magenta where transparent
    transparent = alpha < 128
    rgb[transparent] = (255, 0, 255)
    r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    content = ~((r > 200) & (b > 200) & (g < 80))
    if not content.any():
        return Image.new("RGB", (OUT_SIZE, OUT_SIZE), (255, 0, 255))
    ys, xs = np.where(content)
    minx, maxx = int(xs.min()), int(xs.max())
    miny, maxy = int(ys.min()), int(ys.max())
    crop = rgb[miny:maxy + 1, minx:maxx + 1]
    pad = max(2, HI)
    padded = np.full((crop.shape[0] + pad * 2, crop.shape[1] + pad * 2, 3), 255, dtype=np.uint8)
    padded[:, :, 1] = 0  # G=0 → true magenta
    padded[pad:pad + crop.shape[0], pad:pad + crop.shape[1]] = crop
    ph, pw = padded.shape[:2]
    target = int(OUT_SIZE * PACK_FILL)
    sc = target / max(pw, ph)
    nw = max(2, int(round(pw * sc)))
    nh = max(2, int(round(ph * sc)))
    nw -= nw % 2
    nh -= nh % 2
    nw = max(2, nw)
    nh = max(2, nh)
    scaled = Image.fromarray(padded, mode="RGB").resize((nw, nh), Image.NEAREST)
    out = Image.new("RGB", (OUT_SIZE, OUT_SIZE), (255, 0, 255))
    out.paste(scaled, ((OUT_SIZE - nw) // 2, (OUT_SIZE - nh) // 2))
    return out


def save(im: Image.Image, name: str):
    OUT.mkdir(parents=True, exist_ok=True)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    packed = pack_to_out(im)
    path = OUT / f"{name}.png"
    packed.save(path)

    # Preview with checker (crop content)
    px = packed.load()
    w, h = packed.size
    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            if not (r > 200 and b > 200 and g < 80):
                minx = min(minx, x)
                miny = min(miny, y)
                maxx = max(maxx, x)
                maxy = max(maxy, y)
    if maxx >= minx:
        crop = packed.crop((minx, miny, maxx + 1, maxy + 1))
        sc = 360 / max(crop.width, crop.height)
        nw, nh = max(1, int(crop.width * sc)), max(1, int(crop.height * sc))
        big = crop.resize((nw, nh), Image.NEAREST)
        bp = big.load()
        for y in range(nh):
            for x in range(nw):
                r, g, b = bp[x, y]
                if r > 200 and b > 200 and g < 80:
                    bp[x, y] = (45, 45, 52) if ((x // 12) + (y // 12)) % 2 == 0 else (60, 60, 70)
        big.save(PREVIEW / f"{name}.png")
    print(f"wrote {path} ({packed.size})")


# ---------------- individual props ----------------

def gen_whiteboard():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.62), 26 * HI
    wood = (128, 84, 52)
    wood_d = shade(wood, 0.68)
    wood_l = shade(wood, 1.18)

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    # Easel back strut
    draw_iso_box(d, -0.2, -2.5, 0.3, 0.4, 5.0, 0.25, ox, oy, s,
                 top=wood_l, left=wood_d, right=wood, lw=ow(2))
    # Main frame (tall thin)
    corners = draw_iso_box(d, -0.15, -2.5, 1.15, 0.4, 5.0, 3.9, ox, oy, s,
                           top=shade((200, 198, 190), 1.05),
                           left=shade((88, 88, 96), 0.95),
                           right=shade((62, 62, 72), 1.0), lw=ow(2))
    # White surface inset on +x face
    face = [P(0.28, -2.25, 4.85), P(0.28, 2.25, 4.85), P(0.28, 2.25, 1.45), P(0.28, -2.25, 1.45)]
    d.polygon(face, fill=(250, 250, 253, 255))
    d.line(face + [face[0]], fill=OUTLINE, width=ow(2))
    # Faint grid / scribble lines (marker strokes)
    for i, gz in enumerate([4.2, 3.6, 3.1, 2.5]):
        y0, y1 = -1.8 + (i % 3) * 0.15, 1.6 - (i % 2) * 0.2
        col = [(55, 55, 65), (40, 90, 200), (200, 50, 60), (30, 140, 80)][i]
        d.line([P(0.30, y0, gz), P(0.30, y1, gz - 0.15 * (i % 2))], fill=col + (255,), width=max(1, HI))
    # Sticky notes with slight fold shade
    notes = [
        ((-1.7, 4.35), (255, 224, 85), (230, 190, 50)),
        ((-0.7, 4.5), (115, 205, 255), (80, 165, 220)),
        ((0.4, 4.25), (255, 145, 185), (220, 100, 150)),
        ((1.35, 4.4), (155, 255, 160), (100, 210, 120)),
        ((-1.3, 3.15), (255, 205, 125), (230, 170, 90)),
        ((0.15, 2.95), (200, 185, 255), (160, 140, 220)),
        ((1.2, 3.2), (255, 255, 145), (230, 220, 100)),
    ]
    for (gy, gz), col, shade_c in notes:
        q = [P(0.32, gy, gz), P(0.32, gy + 0.58, gz), P(0.32, gy + 0.58, gz - 0.58), P(0.32, gy, gz - 0.58)]
        d.polygon(q, fill=col + (255,))
        # fold triangle
        d.polygon([q[0], q[1], ((q[0][0] + q[1][0]) / 2, (q[0][1] + q[1][1]) / 2 + 3 * HI)], fill=shade_c + (255,))
        d.line(q + [q[0]], fill=shade(col, 0.5), width=max(1, HI))
        # tiny scribble
        d.line([P(0.33, gy + 0.12, gz - 0.2), P(0.33, gy + 0.45, gz - 0.28)], fill=shade(col, 0.4), width=max(1, HI // 2))
    # Marker tray
    draw_iso_box(d, -0.05, -1.6, 1.12, 0.55, 3.2, 0.22, ox, oy, s,
                 top=shade((55, 55, 64), 1.15), left=shade((35, 35, 42), 1.0), right=shade((28, 28, 34), 1.0))
    for i, col in enumerate([(35, 35, 42), (220, 45, 55), (35, 95, 220), (25, 155, 75)]):
        draw_iso_box(d, 0.15, -1.3 + i * 0.75, 1.34, 0.15, 0.5, 0.14, ox, oy, s,
                     top=shade(col, 1.25), left=shade(col, 0.85), right=shade(col, 0.65))
        # cap tip
        draw_iso_box(d, 0.28, -1.25 + i * 0.75, 1.36, 0.08, 0.18, 0.1, ox, oy, s,
                     top=shade((240, 240, 245), 1.0), left=shade((200, 200, 210), 0.9), right=shade((170, 170, 180), 0.85))
    # Easel feet
    draw_iso_box(d, -0.75, -2.7, 0, 1.55, 0.4, 0.22, ox, oy, s, top=wood_l, left=wood_d, right=wood)
    draw_iso_box(d, -0.75, 2.3, 0, 1.55, 0.4, 0.22, ox, oy, s, top=wood_l, left=wood_d, right=wood)
    # Front support legs (thin)
    d.line([P(-0.5, -2.5, 1.2), P(-0.55, -2.5, 0.22)], fill=wood + (255,), width=ow(3))
    d.line([P(-0.5, 2.5, 1.2), P(-0.55, 2.5, 0.22)], fill=wood + (255,), width=ow(3))
    save(im, "whiteboard")


def gen_beanbag():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.70), 34 * HI
    base = (255, 118, 72)
    mid = shade(base, 0.82)
    dark = shade(base, 0.58)
    light = shade(base, 1.15)
    hi = shade(base, 1.32)

    def blob(cx, cy, cz, rx, ry, rz, col_top, col_side, n=28):
        pts = []
        for i in range(n):
            a = i / n * math.tau
            # lumpy radius
            lump = 1.0 + 0.08 * math.sin(a * 3 + 0.4) + 0.05 * math.cos(a * 5)
            pts.append(iso_pt(cx + math.cos(a) * rx * lump,
                              cy + math.sin(a) * ry * lump,
                              cz + rz * 0.12 * abs(math.cos(a * 2)), ox, oy, s))
        bot = [(p[0], p[1] + rz * s * 0.9) for p in pts]
        for i in range(n):
            j = (i + 1) % n
            # alternate side shade for fabric folds
            side = shade(col_side, 0.92 + 0.12 * ((i % 4) / 3))
            d.polygon([pts[i], pts[j], bot[j], bot[i]], fill=side)
        d.polygon(pts, fill=col_top)
        d.line(pts + [pts[0]], fill=OUTLINE, width=ow(2))
        return pts

    pts0 = blob(0, 0, 0.15, 2.35, 2.15, 1.15, light, dark)
    stipple_top(im, pts0, light, step=max(4, HI), amp=14)
    pts1 = blob(0.08, -0.08, 1.05, 1.85, 1.7, 0.95, hi, mid)
    stipple_top(im, pts1, hi, step=max(4, HI), amp=12)
    # Soft top dimple / crease
    hx, hy = iso_pt(-0.35, -0.45, 1.85, ox, oy, s)
    d.ellipse([hx - 22 * HI / 2, hy - 12 * HI / 2, hx + 34 * HI / 2, hy + 16 * HI / 2],
              fill=shade(base, 1.38))
    # Seam stitch line
    seam = [iso_pt(math.cos(a) * 1.5, math.sin(a) * 1.35, 1.15 + 0.05 * math.sin(a * 4), ox, oy, s)
            for a in [i / 20 * math.tau for i in range(12)]]
    if len(seam) > 1:
        d.line(seam, fill=shade(base, 0.45), width=max(1, HI))
    save(im, "beanbag")


def gen_watercooler():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.72), 32 * HI
    body = (238, 238, 244)
    body_l = shade(body, 0.8)
    body_r = shade(body, 0.65)

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    # Base cabinet
    box = draw_iso_box(d, -1.05, -0.95, 0, 2.1, 1.9, 2.5, ox, oy, s,
                       top=shade((205, 205, 214), 1.0), left=body_l, right=body_r)
    stipple_top(im, [box["t00"], box["t10"], box["t11"], box["t01"]], (205, 205, 214), amp=8)
    # Door panel + handle
    door = [P(1.08, -0.85, 2.3), P(1.08, 0.85, 2.3), P(1.08, 0.85, 0.2), P(1.08, -0.85, 0.2)]
    d.polygon(door, fill=shade(body, 0.92))
    d.line(door + [door[0]], fill=shade((140, 140, 155), 0.95), width=ow(1))
    draw_iso_box(d, 1.1, 0.55, 1.1, 0.1, 0.18, 0.55, ox, oy, s,
                 top=shade((210, 210, 220), 1.15), left=shade((150, 150, 160), 1), right=shade((110, 110, 120), 1))
    # Brand badge
    d.polygon([P(1.09, -0.35, 2.05), P(1.09, 0.35, 2.05), P(1.09, 0.35, 1.85), P(1.09, -0.35, 1.85)],
              fill=(90, 160, 220, 255))
    # Drip tray
    draw_iso_box(d, -0.75, -0.75, 2.5, 1.5, 1.5, 0.18, ox, oy, s,
                 top=shade((70, 70, 82), 1.15), left=shade((45, 45, 55), 1), right=shade((35, 35, 42), 1))
    # Grill slots on tray
    for i in range(5):
        gy = -0.5 + i * 0.25
        d.line([P(-0.5, gy, 2.7), P(0.5, gy, 2.7)], fill=shade((40, 40, 48), 1.1), width=max(1, HI))
    # Blue water bottle
    blue = (65, 155, 225)
    bb = draw_iso_box(d, -0.8, -0.75, 2.68, 1.6, 1.5, 2.35, ox, oy, s,
                      top=shade(blue, 1.3), left=shade(blue, 0.82), right=shade(blue, 0.62))
    # Water level / highlight bands
    d.polygon([P(-0.55, -0.55, 4.7), P(-0.15, -0.55, 4.7), P(-0.15, -0.55, 3.1), P(-0.55, -0.55, 3.1)],
              fill=shade(blue, 1.5))
    d.polygon([P(0.5, 0.5, 4.6), P(0.7, 0.5, 4.6), P(0.7, 0.5, 3.3), P(0.5, 0.5, 3.3)],
              fill=shade(blue, 0.5))
    # Bubbles
    for (gx, gy, gz, r) in [(-0.2, 0.1, 4.0, 0.12), (0.15, -0.2, 3.6, 0.09), (-0.35, 0.3, 3.3, 0.07)]:
        cx, cy = P(gx, gy, gz)
        rr = r * s
        d.ellipse([cx - rr, cy - rr * 0.7, cx + rr, cy + rr * 0.7], fill=shade(blue, 1.55), outline=shade(blue, 1.1))
    # Bottle neck + cap
    draw_iso_box(d, -0.28, -0.28, 5.03, 0.56, 0.56, 0.38, ox, oy, s,
                 top=shade((190, 225, 245), 1.15), left=shade(blue, 0.78), right=shade(blue, 0.6))
    draw_iso_box(d, -0.22, -0.22, 5.4, 0.44, 0.44, 0.18, ox, oy, s,
                 top=shade((250, 250, 255), 1.0), left=shade((180, 180, 190), 1), right=shade((140, 140, 150), 1))
    # Spigots
    for gy in (-0.4, 0.4):
        draw_iso_box(d, 1.0, gy, 2.05, 0.4, 0.28, 0.22, ox, oy, s,
                     top=shade((55, 55, 65), 1.25), left=shade((35, 35, 42), 1), right=shade((25, 25, 32), 1))
        # drip tip
        draw_iso_box(d, 1.35, gy + 0.05, 1.95, 0.12, 0.12, 0.18, ox, oy, s,
                     top=shade((90, 90, 100), 1.1), left=shade((50, 50, 58), 1), right=shade((40, 40, 48), 1))
    # Cups stack
    for i, z in enumerate([2.7, 2.95, 3.2]):
        draw_iso_box(d, 0.25, 0.35, z, 0.5, 0.5, 0.28, ox, oy, s,
                     top=(252, 252, 255, 255), left=shade((220, 220, 232), 0.92), right=shade((195, 195, 210), 0.85))
    save(im, "watercooler")


def gen_fridge():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.70), 30 * HI
    red = (225, 52, 62)
    red_l = shade(red, 0.88)
    red_r = shade(red, 0.68)

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    body = draw_iso_box(d, -1.15, -1.05, 0.12, 2.3, 2.1, 4.35, ox, oy, s,
                        top=shade((198, 198, 208), 1.08),
                        left=red_l, right=red_r)
    stipple_top(im, [body["t00"], body["t10"], body["t11"], body["t01"]], (198, 198, 208), amp=7)
    # Top vent grille
    for i in range(6):
        gy = -0.7 + i * 0.28
        d.line([P(-0.7, gy, 4.5), P(0.7, gy, 4.5)], fill=shade((120, 120, 135), 1.0), width=max(1, HI))
    # Door panel on +x
    face = [P(1.18, -0.95, 4.25), P(1.18, 0.95, 4.25), P(1.18, 0.95, 0.35), P(1.18, -0.95, 0.35)]
    d.polygon(face, fill=shade(red, 0.96))
    d.line(face + [face[0]], fill=OUTLINE, width=ow(2))
    # Door seam / rubber gasket hint
    d.line([P(1.18, -0.95, 4.25), P(1.18, -0.95, 0.35)], fill=shade((40, 20, 25), 1.0), width=max(1, HI))
    # Chrome handle
    draw_iso_box(d, 1.2, 0.58, 1.9, 0.14, 0.22, 1.35, ox, oy, s,
                 top=shade((235, 235, 245), 1.15), left=shade((165, 165, 178), 1), right=shade((125, 125, 138), 1))
    # Handle highlight
    d.line([P(1.28, 0.7, 3.15), P(1.28, 0.7, 2.05)], fill=(250, 250, 255, 255), width=max(1, HI))
    # Brand stripe
    d.polygon([P(1.19, -0.75, 3.75), P(1.19, 0.75, 3.75), P(1.19, 0.75, 3.48), P(1.19, -0.75, 3.48)],
              fill=(250, 250, 255, 255))
    # Logo dot
    cx, cy = P(1.2, 0.0, 3.62)
    d.ellipse([cx - 4 * HI, cy - 3 * HI, cx + 4 * HI, cy + 3 * HI], fill=(225, 52, 62, 255))
    # Magnets / sticky notes
    for (gy, gz), col in [((-0.55, 2.95), (255, 232, 75)), ((0.15, 2.55), (110, 215, 255)),
                          ((0.5, 1.6), (255, 160, 190))]:
        q = [P(1.2, gy, gz), P(1.2, gy + 0.42, gz), P(1.2, gy + 0.42, gz - 0.42), P(1.2, gy, gz - 0.42)]
        d.polygon(q, fill=col + (255,))
        d.line(q + [q[0]], fill=shade(col, 0.55), width=max(1, HI // 2))
    # Freezer line
    d.line([P(1.19, -0.9, 3.15), P(1.19, 0.9, 3.15)], fill=shade(red, 0.55), width=ow(1))
    # Feet
    for gy in (-0.9, 0.75):
        for gx in (-1.05, 0.8):
            draw_iso_box(d, gx, gy, 0, 0.38, 0.28, 0.16, ox, oy, s,
                         top=shade((45, 45, 55), 1.2), left=shade((28, 28, 35), 1), right=shade((20, 20, 26), 1))
    save(im, "fridge")


def gen_bookshelf():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.68), 28 * HI
    wood = (148, 100, 60)
    wood_l = shade(wood, 1.18)
    wood_d = shade(wood, 0.65)
    wood_m = shade(wood, 0.88)

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    case = draw_iso_box(d, -0.45, -2.35, 0, 1.4, 4.7, 4.7, ox, oy, s,
                        top=wood_l, left=wood_m, right=wood_d)
    stipple_top(im, [case["t00"], case["t10"], case["t11"], case["t01"]], wood_l, amp=10)
    # Back panel (darker cavity)
    back = [P(-0.3, -2.2, 4.5), P(-0.3, 2.2, 4.5), P(-0.3, 2.2, 0.15), P(-0.3, -2.2, 0.15)]
    d.polygon(back, fill=shade((55, 38, 28), 1.0))
    # Shelves
    shelf_zs = [0.95, 2.1, 3.25, 4.4]
    for z in shelf_zs:
        draw_iso_box(d, -0.3, -2.2, z, 1.15, 4.4, 0.14, ox, oy, s,
                     top=wood_l, left=shade(wood, 0.78), right=wood_d)
    book_colors = [
        (205, 55, 68), (48, 88, 185), (38, 145, 92), (225, 165, 42),
        (125, 68, 165), (28, 28, 38), (235, 105, 48), (65, 155, 205),
        (185, 48, 125), (88, 125, 58), (245, 225, 95), (58, 58, 95),
        (160, 90, 50), (90, 160, 140), (200, 80, 100),
    ]
    bi = 0
    for zi, z0 in enumerate([0.18, 1.12, 2.27, 3.42]):
        y = -2.05
        while y < 2.05:
            bw = 0.32 + (bi % 4) * 0.1
            bh = 0.62 + (bi % 5) * 0.07
            if y + bw > 2.15:
                break
            col = book_colors[bi % len(book_colors)]
            draw_iso_box(d, 0.5, y, z0, 0.6, bw, bh, ox, oy, s,
                         top=shade(col, 1.18), left=shade(col, 0.88), right=shade(col, 0.62), lw=ow(1))
            # Spine stripe / title band
            midy = y + bw * 0.35
            d.line([P(1.05, midy, z0 + bh * 0.55), P(1.05, midy + bw * 0.3, z0 + bh * 0.55)],
                   fill=shade(col, 1.4), width=max(1, HI))
            # Page edge hint on top
            if bi % 3 == 0:
                draw_iso_box(d, 0.95, y + 0.02, z0 + bh * 0.85, 0.12, bw * 0.85, 0.06, ox, oy, s,
                             top=shade((245, 240, 230), 1.0), left=shade((220, 215, 200), 0.9),
                             right=shade((200, 195, 180), 0.85), lw=max(1, HI // 2))
            y += bw + 0.06
            bi += 1
    # Plant on top
    draw_iso_box(d, -0.05, -0.35, 4.75, 0.6, 0.6, 0.4, ox, oy, s,
                 top=shade((210, 115, 72), 1.12), left=shade((155, 82, 50), 1), right=shade((120, 65, 40), 1))
    greens = [(45, 135, 60), (60, 160, 78), (35, 110, 48), (75, 175, 85)]
    for i, (gx, gy, gz, r) in enumerate([
        (0.2, -0.1, 5.45, 16), (0.4, 0.15, 5.25, 14), (0.05, 0.2, 5.55, 13),
        (0.3, -0.25, 5.7, 11), (0.15, 0.05, 5.9, 10),
    ]):
        cx, cy = iso_pt(gx, gy, gz, ox, oy, s)
        rr = r * HI / 2
        d.ellipse([cx - rr, cy - rr * 0.85, cx + rr * 1.1, cy + rr * 0.8],
                  fill=greens[i % len(greens)] + (255,), outline=OUTLINE, width=max(1, HI // 2))
    save(im, "bookshelf")


def gen_foosball():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.72), 26 * HI
    green = (38, 145, 78)
    frame = (32, 32, 40)

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    # Legs
    for gx, gy in [(-2.7, -1.35), (2.3, -1.35), (-2.7, 1.05), (2.3, 1.05)]:
        draw_iso_box(d, gx, gy, 0, 0.38, 0.38, 1.55, ox, oy, s,
                     top=shade(frame, 1.25), left=shade(frame, 0.9), right=shade(frame, 0.68))
    # Apron / cabinet
    apron = draw_iso_box(d, -3.1, -1.7, 1.55, 6.2, 3.4, 0.5, ox, oy, s,
                         top=shade((52, 52, 62), 1.12), left=shade(frame, 0.95), right=shade(frame, 0.72))
    stipple_top(im, [apron["t00"], apron["t10"], apron["t11"], apron["t01"]], (52, 52, 62), amp=8)
    # Side panels stripe
    d.polygon([P(-3.0, -1.65, 1.95), P(-3.0, 1.55, 1.95), P(-3.0, 1.55, 1.6), P(-3.0, -1.65, 1.6)],
              fill=(220, 55, 60, 255))
    d.polygon([P(3.0, -1.65, 1.95), P(3.0, 1.55, 1.95), P(3.0, 1.55, 1.6), P(3.0, -1.65, 1.6)],
              fill=(55, 100, 220, 255))
    # Playfield
    field = draw_iso_box(d, -2.8, -1.4, 2.05, 5.6, 2.8, 0.14, ox, oy, s,
                         top=shade(green, 1.18), left=shade(green, 0.78), right=shade(green, 0.6))
    stipple_top(im, [field["t00"], field["t10"], field["t11"], field["t01"]], shade(green, 1.18), amp=12)
    # Center line + circle
    d.line([P(0, -1.35, 2.2), P(0, 1.35, 2.2)], fill=(235, 235, 245, 255), width=ow(1))
    ccx, ccy = P(0, 0, 2.2)
    rr = 0.55 * s
    d.ellipse([ccx - rr, ccy - rr * 0.5, ccx + rr, ccy + rr * 0.5], outline=(235, 235, 245, 255), width=ow(1))
    # Goals
    for gx in (-2.6, 2.4):
        d.polygon([P(gx, -0.5, 2.2), P(gx + 0.22, -0.5, 2.2), P(gx + 0.22, 0.5, 2.2), P(gx, 0.5, 2.2)],
                  fill=shade((18, 18, 26), 1.0))
    # Rods + players
    for i, gy in enumerate([-0.95, -0.32, 0.32, 0.95]):
        d.line([P(-3.0, gy, 2.45), P(3.0, gy, 2.45)], fill=(195, 195, 208, 255), width=ow(2))
        # Handles
        draw_iso_box(d, -3.25, gy - 0.12, 2.28, 0.4, 0.24, 0.24, ox, oy, s,
                     top=shade((225, 55, 55), 1.25), left=shade((165, 40, 40), 1), right=shade((120, 28, 28), 1))
        draw_iso_box(d, 2.9, gy - 0.12, 2.28, 0.4, 0.24, 0.24, ox, oy, s,
                     top=shade((55, 100, 225), 1.25), left=shade((38, 70, 165), 1), right=shade((28, 50, 125), 1))
        cols = [(225, 55, 55), (55, 100, 225)]
        positions = [-1.6, -0.45, 0.7, 1.85]
        count = 3 if i in (1, 2) else 2
        for j, gx in enumerate(positions[:count]):
            # offset players per rod
            off = (j - (count - 1) / 2) * 0.15
            col = cols[i % 2]
            draw_iso_box(d, gx + off, gy - 0.14, 2.15, 0.28, 0.28, 0.62, ox, oy, s,
                         top=shade(col, 1.22), left=shade(col, 0.85), right=shade(col, 0.62))
            # head
            hx, hy = P(gx + off + 0.14, gy, 2.85)
            d.ellipse([hx - 4 * HI, hy - 3 * HI, hx + 5 * HI, hy + 4 * HI],
                      fill=shade((240, 200, 160), 1.0), outline=OUTLINE, width=max(1, HI // 2))
    # Ball
    bx, by = P(0.35, 0.12, 2.32)
    d.ellipse([bx - 7 * HI / 2, by - 6 * HI / 2, bx + 7 * HI / 2, by + 6 * HI / 2],
              fill=(252, 252, 255, 255), outline=OUTLINE, width=ow(1))
    save(im, "foosball")


def gen_bike():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = int(CANVAS * 0.51), int(CANVAS * 0.74), 30 * HI
    frame_c = (35, 175, 205)
    tire = (28, 28, 34)

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    def wheel(gx, gy, gz, r=1.0):
        pts = [iso_pt(gx + math.cos(a) * r, gy + math.sin(a) * r * 0.32, gz + math.sin(a) * r, ox, oy, s)
               for a in [i / 32 * math.tau for i in range(32)]]
        d.polygon(pts, fill=shade(tire, 1.05))
        d.line(pts + [pts[0]], fill=OUTLINE, width=ow(2))
        # tire tread ring
        pts2 = [iso_pt(gx + math.cos(a) * r * 0.82, gy + math.sin(a) * r * 0.26, gz + math.sin(a) * r * 0.82, ox, oy, s)
                for a in [i / 24 * math.tau for i in range(24)]]
        d.line(pts2 + [pts2[0]], fill=shade((70, 70, 80), 1.1), width=max(1, HI))
        cx, cy = iso_pt(gx, gy, gz, ox, oy, s)
        d.ellipse([cx - 6 * HI / 2, cy - 5 * HI / 2, cx + 6 * HI / 2, cy + 5 * HI / 2],
                  fill=(195, 195, 205, 255), outline=OUTLINE, width=max(1, HI // 2))
        for a in range(0, 360, 30):
            rad = math.radians(a)
            x2, y2 = iso_pt(gx + math.cos(rad) * r * 0.78, gy + math.sin(rad) * r * 0.25,
                            gz + math.sin(rad) * r * 0.78, ox, oy, s)
            d.line([(cx, cy), (x2, y2)], fill=(150, 150, 162, 255), width=max(1, HI // 2))

    wheel(-2.15, 0, 1.0)
    wheel(2.15, 0, 1.0)
    # Frame tubes (double-pass for thickness)
    tubes = [
        [(-2.15, 0, 1.0), (0.0, 0, 2.35)],
        [(0.0, 0, 2.35), (1.6, 0, 2.65)],
        [(0.0, 0, 2.35), (0.0, 0, 0.55)],
        [(0.0, 0, 0.55), (2.15, 0, 1.0)],
        [(1.6, 0, 2.65), (2.15, 0, 1.0)],
        [(0.0, 0, 2.35), (-0.05, 0, 3.15)],
    ]
    for a, b in tubes:
        d.line([P(*a), P(*b)], fill=OUTLINE, width=ow(4))
        d.line([P(*a), P(*b)], fill=frame_c + (255,), width=ow(3))
        d.line([P(*a), P(*b)], fill=shade(frame_c, 1.25), width=max(1, HI))
    # Handlebar
    d.line([P(1.55, -0.75, 2.85), P(1.55, 0.75, 2.85)], fill=shade((35, 35, 42), 1.2), width=ow(3))
    d.line([P(1.55, 0, 2.65), P(1.55, 0, 2.85)], fill=frame_c + (255,), width=ow(3))
    # Grips
    for gy in (-0.75, 0.55):
        draw_iso_box(d, 1.4, gy, 2.75, 0.25, 0.22, 0.18, ox, oy, s,
                     top=shade((40, 40, 48), 1.3), left=shade((25, 25, 30), 1), right=shade((18, 18, 22), 1))
    # Seat
    draw_iso_box(d, -0.5, -0.28, 3.15, 0.8, 0.55, 0.18, ox, oy, s,
                 top=shade((32, 32, 38), 1.35), left=shade((20, 20, 24), 1), right=shade((14, 14, 18), 1))
    # Pedal crank
    d.line([P(0.0, 0, 0.55), P(0.35, 0.4, 0.35)], fill=shade((160, 160, 170), 1.1), width=ow(2))
    draw_iso_box(d, 0.3, 0.35, 0.25, 0.35, 0.12, 0.1, ox, oy, s,
                 top=shade((40, 40, 48), 1.2), left=shade((25, 25, 30), 1), right=shade((18, 18, 22), 1))
    # Kickstand
    d.line([P(0.25, 0, 0.55), P(0.7, 0.55, 0.08)], fill=shade((85, 85, 95), 1.15), width=ow(2))
    save(im, "bike")


def gen_trash():
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.76), 36 * HI

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    # Recycling (blue)
    rec = draw_iso_box(d, -1.95, -0.95, 0, 1.65, 1.75, 2.15, ox, oy, s,
                       top=shade((95, 155, 220), 1.25), left=shade((48, 110, 185), 0.95),
                       right=shade((38, 88, 155), 0.9))
    stipple_top(im, [rec["t00"], rec["t10"], rec["t11"], rec["t01"]], (95, 155, 220), amp=8)
    # Trash (grey)
    tr = draw_iso_box(d, 0.05, -0.95, 0, 1.65, 1.75, 2.15, ox, oy, s,
                      top=shade((125, 125, 135), 1.18), left=shade((78, 78, 88), 0.95),
                      right=shade((55, 55, 65), 0.9))
    stipple_top(im, [tr["t00"], tr["t10"], tr["t11"], tr["t01"]], (125, 125, 135), amp=8)
    # Recycle symbol (three arrows approx as arcs/lines)
    cx, cy = P(-1.05, 0.0, 1.25)
    for a0 in (0, 120, 240):
        pts = []
        for k in range(8):
            ang = math.radians(a0 + k * 12)
            r = 12 * HI / 2
            pts.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r * 0.75))
        if len(pts) > 1:
            d.line(pts, fill=(230, 245, 255, 255), width=ow(2))
    # Liners
    d.polygon([P(-1.85, -0.85, 2.15), P(-0.4, -0.85, 2.15), P(-0.4, 0.6, 2.15), P(-1.85, 0.6, 2.15)],
              fill=(235, 235, 242, 255))
    d.polygon([P(0.15, -0.85, 2.15), P(1.6, -0.85, 2.15), P(1.6, 0.6, 2.15), P(0.15, 0.6, 2.15)],
              fill=(38, 38, 44, 255))
    # Rim lips
    draw_iso_box(d, -2.0, -1.0, 2.15, 1.75, 1.85, 0.12, ox, oy, s,
                 top=shade((120, 175, 235), 1.2), left=shade((60, 120, 190), 0.9), right=shade((45, 95, 160), 0.85))
    draw_iso_box(d, 0.0, -1.0, 2.15, 1.75, 1.85, 0.12, ox, oy, s,
                 top=shade((145, 145, 155), 1.15), left=shade((90, 90, 100), 0.9), right=shade((65, 65, 75), 0.85))
    # Crumpled paper + bottle in bins
    for (gx, gy, col) in [(0.55, -0.35, (245, 245, 250)), (1.0, 0.15, (240, 240, 248)),
                          (-1.4, -0.2, (180, 220, 255)), (-0.9, 0.25, (100, 180, 100))]:
        x, y = P(gx, gy, 2.28)
        d.ellipse([x - 7 * HI / 2, y - 5 * HI / 2, x + 8 * HI / 2, y + 6 * HI / 2],
                  fill=col + (255,), outline=OUTLINE, width=max(1, HI // 2))
    save(im, "trash")


def gen_plant2():
    """Tall leafy plant variety — monstera-ish in a ceramic pot."""
    im = new_canvas()
    d = ImageDraw.Draw(im)
    ox, oy, s = CANVAS // 2, int(CANVAS * 0.76), 32 * HI
    pot = (235, 225, 215)
    pot_d = shade(pot, 0.72)
    pot_l = shade(pot, 0.88)

    def P(x, y, z):
        return iso_pt(x, y, z, ox, oy, s)

    # Pot body
    pb = draw_iso_box(d, -1.05, -1.05, 0, 2.1, 2.1, 1.7, ox, oy, s,
                      top=shade((55, 42, 32), 1.1), left=pot_l, right=pot_d)
    # Pot stripe
    d.polygon([P(-1.0, -1.0, 0.9), P(1.0, -1.0, 0.9), P(1.0, 1.0, 0.9), P(-1.0, 1.0, 0.9)],
              fill=shade((210, 120, 90), 1.0))
    # Rim
    draw_iso_box(d, -1.2, -1.2, 1.7, 2.4, 2.4, 0.28, ox, oy, s,
                 top=shade(pot, 1.18), left=shade(pot, 0.9), right=shade(pot, 0.72))
    # Soil
    soil = [P(-0.85, -0.85, 1.98), P(0.85, -0.85, 1.98), P(0.85, 0.85, 1.98), P(-0.85, 0.85, 1.98)]
    d.polygon(soil, fill=(55, 40, 30, 255))
    stipple_top(im, soil, (55, 40, 30), amp=15)

    def leaf(gx, gy, gz, ang, size, col):
        pts = []
        for i in range(14):
            t = i / 14 * math.tau
            # heart/monstera-ish oval with notch
            lx = math.cos(t) * size * (1.0 - 0.15 * abs(math.sin(t)))
            ly = math.sin(t) * size * 0.48
            if 0.3 < t < 0.7:  # split tip
                lx *= 0.75
            ca, sa = math.cos(ang), math.sin(ang)
            rx, ry = gx + lx * ca - ly * sa, gy + lx * sa + ly * ca
            pts.append(iso_pt(rx, ry, gz + abs(math.sin(t)) * size * 0.38, ox, oy, s))
        d.polygon(pts, fill=col + (255,))
        d.line(pts + [pts[0]], fill=OUTLINE, width=ow(1))
        # vein
        mid = iso_pt(gx, gy, gz + size * 0.15, ox, oy, s)
        tip = iso_pt(gx + math.cos(ang) * size * 0.7, gy + math.sin(ang) * size * 0.7,
                     gz + size * 0.35, ox, oy, s)
        d.line([mid, tip], fill=shade(col, 0.55), width=max(1, HI // 2))

    greens = [(38, 125, 55), (55, 155, 72), (28, 105, 45), (70, 175, 82), (45, 140, 65)]
    params = [
        (0.0, 0.0, 3.4, 0.25, 1.55),
        (-0.7, 0.35, 2.95, -0.7, 1.35),
        (0.75, -0.25, 3.05, 0.85, 1.4),
        (0.25, 0.65, 3.7, 1.25, 1.2),
        (-0.35, -0.55, 3.8, -1.05, 1.25),
        (0.55, 0.25, 4.25, 0.35, 1.1),
        (-0.55, 0.05, 4.35, -0.45, 1.05),
        (0.15, -0.2, 4.7, 0.1, 0.95),
        (-0.2, 0.4, 4.55, 0.9, 0.9),
    ]
    # Stems first (under leaves)
    for gx, gy in [(0, 0), (-0.25, 0.2), (0.3, -0.18), (-0.1, -0.25)]:
        d.line([P(gx, gy, 1.95), P(gx * 0.4, gy * 0.4, 3.8)],
               fill=(42, 95, 52, 255), width=ow(2))
    for i, (gx, gy, gz, ang, sz) in enumerate(params):
        leaf(gx, gy, gz, ang, sz, greens[i % len(greens)])
    save(im, "plant2")


def main():
    gens = [
        gen_whiteboard,
        gen_beanbag,
        # watercooler.png is the supplied bottle sprite, not this generator.
        gen_fridge,
        # bookshelf.png is the supplied shelf sprite, not this generator.
        gen_foosball,
        gen_bike,
        gen_trash,
        gen_plant2,
    ]
    for g in gens:
        g()
    print("all props generated")


if __name__ == "__main__":
    main()
