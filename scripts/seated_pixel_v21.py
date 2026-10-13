#!/usr/bin/env python3
"""v21: hand-crafted low-res back-facing sit/type pixel sprites.

Draws on a small logical canvas (W=48, H=56) with sharp 1px outlines and a
limited per-agent palette sampled from walk sheets, then NEAREST-upscales onto
the 1024 magenta canvas so inSampleSize=4 still reads as clean chunky pixels.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "media" / "sprites" / "chars"
PEOPLE = ["byte", "cursor", "kai", "ops", "pixel", "you"]

# Logical pixel grid (keep even for clean upscale).
LW, LH = 48, 56
MAG = (255, 0, 255)
OUTLINE = (14, 12, 10)


def darken(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def lighten(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


# ---------------------------------------------------------------------------
# Palette sampling (local copies so this module can run standalone)
# ---------------------------------------------------------------------------

def is_magenta(r, g, b):
    return r > 130 and b > 130 and g < r and g < b and (r + b - 2 * g) > 50


def is_outline(r, g, b):
    return (r + g + b) < 55


def is_skin(r, g, b):
    if is_magenta(r, g, b) or is_outline(r, g, b):
        return False
    # Blond/yellow hair ≠ peach skin (see gen_back_sprites.is_skin).
    if (r - b) > 80 and (g - b) > 48 and b < 130:
        return False
    if r < 95 or g < 55 or b < 40:
        return False
    return (r - b) > 22 and r >= g - 18 and g >= b - 8 and 210 < (r + g + b) < 680


def is_true_hat_red(r, g, b):
    return r > 150 and g < 70 and b < 70 and (r - g) > 70 and (r - b) > 70


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
    from collections import Counter
    cnt = Counter()
    for r, g, b in samples:
        if is_magenta(r, g, b) or is_outline(r, g, b):
            continue
        if r > 230 and g > 230 and b > 230:
            continue
        cnt[(r // 8 * 8, g // 8 * 8, b // 8 * 8)] += 1
    return cnt.most_common(1)[0][0] if cnt else (90, 60, 40)


def near(c, o, tol=48):
    return all(abs(c[i] - o[i]) < tol for i in range(3))


def quant(c):
    return (c[0] // 8 * 8, c[1] // 8 * 8, c[2] // 8 * 8)


def cdist(a, b):
    return abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2])


# Import shared samplers from gen_back_sprites when available (preferred).
def _load_palette(person: str):
    try:
        from gen_back_sprites import (
            sample_hair_from_sheets,
            sample_garment_for_person,
            sample_seated_leg_colors,
            sample_skin_tone,
            sample_accent_collar,
        )
        sit = Image.open(SRC / f"{person}_sit.png").convert("RGB")
        hair, crown, hat = sample_hair_from_sheets(person)
        shirt = sample_garment_for_person(person, sit)
        pants, shoes = sample_seated_leg_colors(person, sit)
        skin = sample_skin_tone(person, sit)
        collar = sample_accent_collar(person, shirt)
        return {
            "hair": hair,
            "hair_hi": next((c for c in (crown or []) if sum(c) > sum(hair) - 10 and not (c[0] > 80 and c[2] > 80 and c[1] < c[0] - 20)), lighten(hair, 1.10)),
            "hair_lo": darken(hair, 0.72),
            "hat": hat,
            "shirt": shirt,
            "shirt_lo": darken(shirt, 0.82),
            "shirt_md": darken(shirt, 0.92),
            "pants": pants,
            "pants_lo": darken(pants, 0.72),
            "shoes": shoes,
            "shoes_lo": darken(shoes, 0.78),
            "skin": skin,
            "skin_lo": darken(skin, 0.88),
            "collar": collar,
        }
    except Exception as e:
        raise RuntimeError(f"palette load failed for {person}: {e}") from e


# ---------------------------------------------------------------------------
# Pixel canvas helpers
# ---------------------------------------------------------------------------

class Pix:
    def __init__(self, w=LW, h=LH):
        self.w, self.h = w, h
        self.im = Image.new("RGB", (w, h), MAG)
        self.px = self.im.load()

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[x, y]
        return MAG

    def fill_rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def fill_ellipse(self, cx, cy, rx, ry, c):
        rx = max(1, rx)
        ry = max(1, ry)
        for y in range(cy - ry, cy + ry + 1):
            for x in range(cx - rx, cx + rx + 1):
                nx = (x - cx) / float(rx)
                ny = (y - cy) / float(ry)
                if nx * nx + ny * ny <= 1.05:
                    self.put(x, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.put(x, y, c)

    def outline_silhouette(self, color=OUTLINE):
        """Grow a 1px outline INTO magenta cells (preserve interior colors)."""
        to_paint = []
        for y in range(self.h):
            for x in range(self.w):
                if not is_magenta(*self.px[x, y]):
                    continue
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h and not is_magenta(*self.px[nx, ny]):
                        to_paint.append((x, y))
                        break
        for x, y in to_paint:
            self.px[x, y] = color


# ---------------------------------------------------------------------------
# Pose geometry (fractions of LW/LH) — back-facing seated at desk
# ---------------------------------------------------------------------------

def typing_offsets(frame: str):
    """Returns (typing, L_hand_dy, R_hand_dy, L_shoulder_dy, R_shoulder_dy, widen).

    dy in logical pixels (positive = down toward heels).
    """
    if not frame.startswith("type"):
        return False, 0, 0, 0, 0, 0
    table = {
        # left presses (hand lower), right lifts; larger deltas so cycle reads
        "type0": (True, 3, -3, -1, 1, 1),
        "type1": (True, -3, 3, 1, -1, 1),
        "type2": (True, 1, 1, 0, 0, 2),
    }
    return table.get(frame, (True, 0, 0, 0, 0, 1))


def paint_hair(p: Pix, pal: dict, person: str, cx: int):
    """Back-of-head hair mass with agent-specific silhouette."""
    hair, hi, lo = pal["hair"], pal["hair_hi"], pal["hair_lo"]
    hat = pal["hat"]

    # Base oval head mass
    if person == "byte":
        # Blonde bun: round crown + tall top knot
        p.fill_ellipse(cx, 14, 11, 10, hair)
        p.fill_ellipse(cx, 6, 6, 5, hair)  # bun
        p.fill_ellipse(cx - 2, 10, 4, 2, hi)  # highlight
        p.fill_rect(cx - 2, 18, cx + 2, 21, lo)  # nape
        # bun ring shade
        p.fill_rect(cx - 4, 4, cx + 4, 5, lo)
        p.fill_rect(cx - 3, 7, cx + 3, 7, hi)
    elif person == "ops":
        # Cap + short dark hair underneath
        p.fill_ellipse(cx, 15, 11, 9, hair)
        p.fill_rect(cx - 10, 6, cx + 10, 12, hat or hair)
        p.fill_rect(cx - 11, 11, cx + 11, 12, darken(hat or hair, 0.78))  # brim
        p.fill_rect(cx - 6, 7, cx + 6, 8, lighten(hat or hair, 1.08))
        p.fill_rect(cx - 3, 20, cx + 3, 22, lo)
    elif person == "pixel":
        # Short black / slight spikes
        p.fill_ellipse(cx, 14, 10, 10, hair)
        for sx in (-7, -3, 1, 5):
            p.fill_rect(cx + sx, 4, cx + sx + 2, 7, hair)
        p.fill_rect(cx - 2, 8, cx + 1, 9, hi if sum(hi) > 40 else lighten(hair, 1.4))
        p.fill_rect(cx - 3, 20, cx + 3, 22, lo)
    elif person == "cursor":
        # Teal bob / soft rounded
        p.fill_ellipse(cx, 14, 12, 11, hair)
        p.fill_rect(cx - 5, 6, cx + 2, 8, hi)
        p.fill_rect(cx - 9, 16, cx - 7, 20, lo)  # side lock
        p.fill_rect(cx + 7, 16, cx + 9, 20, lo)
        p.fill_rect(cx - 3, 21, cx + 3, 23, lo)
    elif person == "kai":
        # Orange/brown medium, slight volume
        p.fill_ellipse(cx, 14, 11, 11, hair)
        p.fill_rect(cx - 4, 5, cx + 3, 7, hi)
        p.fill_rect(cx - 8, 12, cx - 6, 18, lo)
        p.fill_rect(cx + 6, 12, cx + 8, 18, lo)
        p.fill_rect(cx - 3, 21, cx + 3, 23, lo)
    else:  # you — brown medium
        p.fill_ellipse(cx, 14, 11, 10, hair)
        p.fill_rect(cx - 5, 6, cx + 1, 8, hi)
        p.fill_rect(cx - 2, 10, cx + 1, 18, lo)  # part
        p.fill_rect(cx - 3, 20, cx + 3, 22, lo)



def paint_torso(p: Pix, pal: dict, cx: int, typing: bool, widen: int,
                ls_dy: int, rs_dy: int):
    shirt, slo, smd = pal["shirt"], pal["shirt_lo"], pal["shirt_md"]
    collar = pal["collar"]
    sw = 11 if not typing else 10
    top, bot = 22, 36
    for y in range(top, bot + 1):
        t = (y - top) / max(1.0, bot - top)
        half = int(round(sw * (1.0 - 0.15 * t)))
        p.fill_rect(cx - half, y, cx + half, y, shirt)
    for sign, sdy in ((-1, ls_dy if typing else 0), (1, rs_dy if typing else 0)):
        ox = cx + sign * sw
        sy = top + sdy
        p.fill_rect(ox - (1 if sign > 0 else 2), sy,
                    ox + (2 if sign > 0 else 1), sy + 2, shirt)
    p.vline(cx, top + 2, bot - 2, slo)
    p.vline(cx - sw + 1, top + 3, bot - 3, smd)
    p.vline(cx + sw - 1, top + 3, bot - 3, smd)
    p.fill_rect(cx - 3, top, cx + 3, top + 1, collar)


def paint_arms_sit(p: Pix, pal: dict, cx: int):
    shirt, smd = pal["shirt"], pal["shirt_md"]
    for sign in (-1, 1):
        ax = cx + sign * 12
        # 3px-wide sleeve
        for dx in (-1, 0, 1):
            p.fill_rect(ax + dx, 24, ax + dx, 35, shirt)
        p.vline(ax, 25, 34, smd)


def paint_arms_type(p: Pix, pal: dict, cx: int, lh_dy: int, rh_dy: int, widen: int):
    """Thick L-arms: elbow out, forearm down-in, skin hands at keyboard."""
    del widen
    shirt, smd = pal["shirt"], pal["shirt_md"]
    skin, skin_lo = pal["skin"], pal["skin_lo"]

    def thick_line(x0, y0, x1, y1, fill, shade=None):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            t = i / steps
            x = int(round(x0 + (x1 - x0) * t))
            y = int(round(y0 + (y1 - y0) * t))
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if abs(dx) + abs(dy) > 1:
                        continue  # plus shape = ~3px
                    p.put(x + dx, y + dy, fill)
            if shade is not None and i % 2 == 0:
                p.put(x, y, shade)

    # Left — elbow y follows opposite of hand so press looks like a hinge
    shoulder_l = (cx - 10, 24)
    elbow_l = (cx - 15, 29 - lh_dy // 2)
    hand_l = (cx - 6, 40 + lh_dy)
    thick_line(*shoulder_l, *elbow_l, shirt, smd)
    thick_line(*elbow_l, *hand_l, shirt, smd)
    p.fill_ellipse(elbow_l[0], elbow_l[1], 2, 2, shirt)
    p.fill_ellipse(hand_l[0], hand_l[1], 3, 2, skin)
    p.put(hand_l[0], hand_l[1] + 1, skin_lo)
    p.put(hand_l[0] - 1, hand_l[1] + 1, skin_lo)

    # Right
    shoulder_r = (cx + 10, 24)
    elbow_r = (cx + 15, 29 - rh_dy // 2)
    hand_r = (cx + 6, 40 + rh_dy)
    thick_line(*shoulder_r, *elbow_r, shirt, smd)
    thick_line(*elbow_r, *hand_r, shirt, smd)
    p.fill_ellipse(elbow_r[0], elbow_r[1], 2, 2, shirt)
    p.fill_ellipse(hand_r[0], hand_r[1], 3, 2, skin)
    p.put(hand_r[0], hand_r[1] + 1, skin_lo)
    p.put(hand_r[0] + 1, hand_r[1] + 1, skin_lo)


def paint_legs(p: Pix, pal: dict, cx: int):
    pants, plo = pal["pants"], pal["pants_lo"]
    shoes, slo = pal["shoes"], pal["shoes_lo"]
    # Wide hips under coat hem → taper to heels (reads as seated, not standing).
    top, bot = 35, 44
    for y in range(top, bot + 1):
        t = (y - top) / max(1.0, bot - top)
        half = int(round(11 * (1.0 - 0.40 * t)))
        p.fill_rect(cx - half, y, cx + half, y, pants)
    p.vline(cx, top + 1, bot - 1, plo)
    for foot_cx in (cx - 5, cx + 5):
        p.fill_rect(foot_cx - 2, 44, foot_cx + 2, 45, darken(pants, 0.78))
        p.fill_rect(foot_cx - 2, 46, foot_cx + 2, 49, shoes)
        p.fill_rect(foot_cx - 1, 49, foot_cx + 1, 50, slo)


def render_logical(person: str, frame: str, pal: dict) -> Image.Image:
    p = Pix()
    cx = LW // 2
    typing, lh_dy, rh_dy, ls_dy, rs_dy, widen = typing_offsets(frame)

    paint_hair(p, pal, person, cx)
    paint_torso(p, pal, cx, typing, widen, ls_dy, rs_dy)
    if typing:
        paint_arms_type(p, pal, cx, lh_dy, rh_dy, widen)
    else:
        paint_arms_sit(p, pal, cx)
    paint_legs(p, pal, cx)
    p.outline_silhouette()
    return p.im


def place_on_canvas(logical: Image.Image, canvas_size, dest_bbox) -> Image.Image:
    """NEAREST-upscale logical art into canvas, centered on dest_bbox."""
    out = Image.new("RGB", canvas_size, MAG)
    minx, miny, maxx, maxy = dest_bbox
    bw = maxx - minx + 1
    bh = maxy - miny + 1
    # Scale logical to fit ~92% of bbox height, keep aspect
    scale = min(bw / LW, bh / LH) * 0.92
    nw = max(1, int(round(LW * scale)))
    nh = max(1, int(round(LH * scale)))
    # Force scale to integer for sharp pixels when possible
    iscale = max(1, int(round(scale)))
    # Prefer integer scale if it still fits
    if LW * iscale <= bw and LH * iscale <= bh:
        nw, nh = LW * iscale, LH * iscale
    scaled = logical.resize((nw, nh), Image.NEAREST)
    # Center horizontally; bottom-align heels near bbox bottom (seated at desk)
    dx = minx + (bw - nw) // 2
    dy = miny + max(0, bh - nh - int(bh * 0.02))
    out.paste(scaled, (dx, dy))
    return out


def make_seated_back(src: Image.Image, frame: str, person: str) -> Image.Image:
    """Public entry: produce sit/type *_back from palette + pixel template."""
    pal = _load_palette(person)
    logical = render_logical(person, frame, pal)
    im = src.convert("RGB")
    bb = bbox(im)
    if bb[2] < bb[0]:
        # fallback full canvas center
        bb = (im.size[0] // 4, im.size[1] // 8, 3 * im.size[0] // 4, 7 * im.size[1] // 8)
    return place_on_canvas(logical, im.size, bb)


def regenerate_all(only_people=None):
    people = only_people or PEOPLE
    frames = ["sit", "type0", "type1", "type2"]
    written = 0
    for person in people:
        for frame in frames:
            src_path = SRC / f"{person}_{frame}.png"
            if not src_path.exists() and frame == "type2":
                src_path = SRC / f"{person}_type0.png"
            if not src_path.exists() and frame.startswith("type"):
                src_path = SRC / f"{person}_sit.png"
            if not src_path.exists():
                print("missing", src_path)
                continue
            src = Image.open(src_path)
            back = make_seated_back(src, frame, person)
            out = SRC / f"{person}_{frame}_back.png"
            back.save(out)
            written += 1
            print("wrote", out.name)
    print(f"wrote {written} seated back frames")
    return written


if __name__ == "__main__":
    regenerate_all()
