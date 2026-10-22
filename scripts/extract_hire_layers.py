"""Extract loop-ready layers from agent_hire_banner.png (2000x667, pixel-faithful).

Layers (all drawn over base; rest frame == original):
  agent_hire_base.png     full art, clouds removed (sky inpainted)
  agent_hire_clouds.png   RGBA keyed clouds, rolled to loop point (wrap scroll)
  agent_hire_card1/2/3.png  RGBA window cards (vertical bob, phase-locked)
  agent_hire_greeter.png  RGBA boy, hand zone inpainted (bob, phase-locked)
  agent_hire_hand.png     RGBA forearm+hand on desk (lift/tap gestures)
  agent_hire_lid_l/r.png  RGBA closed-lid overlays for blinks (alpha-gated)
  agent_hire_logo.png     RGBA brightened laptop logo (slow glow pulse)
  agent_hire_steam.png    RGBA procedural steam puff (3 parametric wisps)
  agent_hire_hire.png     RGBA 'Hire' word (subtle breathe, bottom-anchored)

Overlay design: base keeps every static pixel; layers carry moving content with
soft 2px edges (Hire uses hard cuts through flat sky). Cards+greeter share one
bob phase so their overlaps (hair over card1/card2) never shear.
"""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/res/drawable-nodpi/agent_hire_banner.png"
OUT = ROOT / "app/src/main/res/drawable-nodpi"

W, H = 2000, 667

# (name, x0, y0, x1, y1) moving rects, measured on the 2000x667 asset.
CARD1 = (1185, 104, 1386, 360)
CARD2 = (1386, 53, 1606, 354)
CARD3 = (1606, 97, 1808, 362)
GREETER = (1342, 300, 1584, 667)
HIRE = (679, 140, 1096, 362)
HAND = (1480, 502, 1528, 578)
HAND_FILL = (1482, 504, 1526, 576)  # inpainted behind HAND in greeter layer
LID_L = (1399, 413, 1436, 453)
EYE_L = (1403, 417, 1431, 449)  # opening repainted with cheek skin + lash
LID_R = (1472, 413, 1503, 455)
EYE_R = (1476, 417, 1500, 448)  # opening repainted with temple skin + lash
LOGO = (1300, 515, 1341, 549)

# Cloud wrap loop point: center of the widest empty column run (layer space
# is rolled left by this so the tile boundary falls in transparent sky).
CLOUD_LOOP_X = 1535

# Cloud-key exclusion rects: title, cards, greeter, laptop zone (mug/pot/logo).
TITLE_EXCL = (150, 135, 1100, 345)
LAPTOP_EXCL = (1050, 445, 1430, 585)
EXCLUSIONS = [TITLE_EXCL, CARD1, CARD2, CARD3, GREETER, LAPTOP_EXCL]

# Cloud-key trim: key only above ymax per x band (keeps tower windows out).
# (x0, x1, ymax)
TRIMS = [
    (0, 150, 248),
    (150, 1050, 340),
    (1050, 1150, 340),
    (1150, 1185, 325),
    (1185, 1386, 104),
    (1386, 1606, 53),
    (1606, 1808, 97),
    (1808, 1840, 320),
    (1840, 2000, 240),
]

FEATHER = 2
# Hard saturation-based cloud key: sky is strongly blue (b-r ~120+), while
# clouds, bottoms, and thin wisps are near-neutral (sat <= 78). Hard copy
# (alpha 255) keeps the rest frame bit-exact and clouds rigid while drifting.
SAT_KEY = 78
BRIGHT_MIN = 430  # sum(r,g,b) guard


def skyish(r, g, b):
    return (b - r) > 95 and b > 180 and r > 50


def trim_top(x):
    for x0, x1, ymax in TRIMS:
        if x0 <= x < x1:
            return ymax
    return 0


def excluded(x, y):
    return any(x0 <= x < x1 and y0 <= y < y1 for x0, y0, x1, y1 in EXCLUSIONS)


def main():
    src = Image.open(SRC).convert("RGB")
    assert src.size == (W, H), src.size
    px = src.load()

    clouds = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cpx = clouds.load()
    mask = Image.new("L", (W, H), 0)
    mpx = mask.load()

    for y in range(H):
        for x in range(W):
            if y >= trim_top(x) or excluded(x, y):
                continue
            r, g, b = px[x, y]
            if r + g + b < BRIGHT_MIN:
                continue
            sat = max(r, g, b) - min(r, g, b)
            if sat > SAT_KEY:
                continue
            cpx[x, y] = (r, g, b, 255)
            mpx[x, y] = 255

    # Binary closing (3x3) fills interior specks so clouds drift rigidly,
    # then re-apply exclusions/trims so closing never leaks into them.
    mask = mask.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
    mpx = mask.load()
    clouds = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cpx = clouds.load()
    for y in range(H):
        for x in range(W):
            if not mpx[x, y]:
                continue
            if y >= trim_top(x) or excluded(x, y):
                mpx[x, y] = 0
                continue
            r, g, b = px[x, y]
            cpx[x, y] = (r, g, b, 255)

    # Sky inpaint: per-column vertical gradient between nearest unmasked sky
    # samples above/below each masked run, then horizontal smoothing.
    base = src.copy()
    bpx = base.load()
    for x in range(W):
        y = 0
        while y < H:
            if not mpx[x, y]:
                y += 1
                continue
            r0 = y
            while y < H and mpx[x, y]:
                y += 1
            r1 = y - 1
            # Walk past cloud-colored pixels to true sky; stop at exclusions.
            top = None
            yy = r0 - 1
            while yy >= max(0, r0 - 60):
                if excluded(x, yy):
                    break
                r, g, b = px[x, yy]
                if not mpx[x, yy] and skyish(r, g, b):
                    top = (r, g, b)
                    break
                yy -= 1
            bot = None
            yy = r1 + 1
            while yy <= min(H - 1, r1 + 60):
                if excluded(x, yy):
                    break
                r, g, b = px[x, yy]
                if not mpx[x, yy] and skyish(r, g, b):
                    bot = (r, g, b)
                    break
                yy += 1
            if top is None:
                top = px[x, r0 - 1] if r0 > 0 else (120, 210, 250)
            if bot is None:
                bot = top
            span = max(1, r1 - r0)
            for yy in range(r0, r1 + 1):
                t = (yy - r0) / span if span else 0.0
                bpx[x, yy] = tuple(
                    int(top[c] + (bot[c] - top[c]) * t) for c in range(3)
                )

    # Horizontal smoothing of inpainted columns only (radius 8), keeps sky soft.
    sm = base.filter(ImageFilter.BoxBlur(4))
    spx = sm.load()
    for y in range(H):
        for x in range(W):
            if mpx[x, y]:
                bpx[x, y] = spx[x, y]

    base.save(OUT / "agent_hire_base.png")

    # Roll the cloud layer so the wrap tile boundary falls in empty sky.
    import numpy as np
    rolled = np.roll(np.array(clouds), -CLOUD_LOOP_X, axis=1)
    assert rolled[:, 0, 3].sum() == 0 and rolled[:, -1, 3].sum() == 0
    Image.fromarray(rolled).save(OUT / "agent_hire_clouds.png")

    def feathered(rect, name, fw=FEATHER):
        x0, y0, x1, y1 = rect
        w, h = x1 - x0, y1 - y0
        layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        crop = src.crop(rect)
        lpx = layer.load()
        c2 = crop.load()
        for yy in range(h):
            for xx in range(w):
                r, g, b = c2[xx, yy]
                d = min(xx, yy, w - 1 - xx, h - 1 - yy)
                a = 255 if d >= fw else int(255 * (d + 1) / (fw + 1))
                lpx[xx, yy] = (r, g, b, a)
        layer.save(OUT / name)

    def hard(rect, name):
        layer = src.crop(rect).convert("RGBA")
        layer.save(OUT / name)

    feathered(CARD1, "agent_hire_card1.png")
    feathered(CARD2, "agent_hire_card2.png")
    feathered(CARD3, "agent_hire_card3.png")

    # Greeter with the hand zone inpainted: per-column vertical gradient
    # between the rows above (collar/shirt/suit) and below (desk-slate/wood),
    # flat fill from below when the top sample is skin.
    def skinlike(r, g, b):
        return r > 200 and g > 150 and b > 120 and r > b + 20

    gx0, gy0, gx1, gy1 = GREETER
    gw, gh = gx1 - gx0, gy1 - gy0
    greeter = Image.new("RGBA", (gw, gh), (0, 0, 0, 0))
    gpx = greeter.load()
    fx0, fy0, fx1, fy1 = HAND_FILL
    fills = {}
    for x in range(fx0, fx1):
        top = sorted(px[x, y] for y in range(500, 503))[1]
        bot = sorted(px[x, y] for y in range(576, 578))[0]
        fills[x] = (top, bot)
    for yy in range(gh):
        y = gy0 + yy
        for xx in range(gw):
            x = gx0 + xx
            if fx0 <= x < fx1 and fy0 <= y < fy1:
                top, bot = fills[x]
                if skinlike(*top):
                    r, g, b = bot
                else:
                    t = (y - fy0) / max(1, fy1 - 1 - fy0)
                    r, g, b = tuple(int(top[c] + (bot[c] - top[c]) * t) for c in range(3))
            else:
                r, g, b = px[x, y]
            d = min(xx, yy, gw - 1 - xx, gh - 1 - yy)
            a = 255 if d >= FEATHER else int(255 * (d + 1) / (FEATHER + 1))
            gpx[xx, yy] = (r, g, b, a)
    greeter.save(OUT / "agent_hire_greeter.png")

    feathered(HAND, "agent_hire_hand.png", fw=1)

    # Closed lids: base copies with the eye openings repainted in local skin,
    # plus a lash line and a faint crease. Left samples cheek below the eye,
    # right samples temple beside the eye.
    def closed_lid(rect, eye, lash_rows, lash_x0, lash_x1, crease_y, name, sample):
        lx0, ly0, lx1, ly1 = rect
        lw, lh = lx1 - lx0, ly1 - ly0
        lid = Image.new("RGBA", (lw, lh), (0, 0, 0, 0))
        lpx = lid.load()
        ex0, ey0, ex1, ey1 = eye
        for yy in range(lh):
            y = ly0 + yy
            for xx in range(lw):
                x = lx0 + xx
                r, g, b = px[x, y]
                if ex0 <= x < ex1 and ey0 <= y < ey1 and (
                    (r + g + b) < 240
                    or (r > 235 and g > 235 and b > 235)
                    or (r < 210 and g < 130 and b < 110 and r > b)
                ):
                    r, g, b = sample(x, y)
                if y in lash_rows and lash_x0 <= x < lash_x1:
                    r, g, b = (50, 25, 10)
                if y == lash_rows[1] + 1 and x in (
                    lash_x0, lash_x0 + 1, lash_x1 - 2, lash_x1 - 1,
                ):
                    r, g, b = (50, 25, 10)
                if y == crease_y and lash_x0 + 2 <= x < lash_x1 - 2:
                    r, g, b = tuple(int(c * 0.92) for c in (r, g, b))
                d = min(xx, yy, lw - 1 - xx, lh - 1 - yy)
                a = 255 if d >= 1 else 127
                lpx[xx, yy] = (r, g, b, a)
        lid.save(OUT / name)

    def cheek_sample(x, y):
        cols = sorted(px[x, yy] for yy in range(452, 456))
        return cols[len(cols) // 2]

    def temple_sample(x, y):
        cols = sorted(px[xx, y] for xx in range(1500, 1504))
        return cols[len(cols) // 2]

    closed_lid(LID_L, EYE_L, (433, 434), 1406, 1428, 429,
               "agent_hire_lid_l.png", cheek_sample)
    closed_lid(LID_R, EYE_R, (433, 434), 1478, 1498, 429,
               "agent_hire_lid_r.png", temple_sample)

    # Logo glow: brightened logo pixels only (lid stays transparent so no
    # box shows); compositor pulses alpha.
    ox0, oy0, ox1, oy1 = LOGO
    ow, oh = ox1 - ox0, oy1 - oy0
    logo = Image.new("RGBA", (ow, oh), (0, 0, 0, 0))
    opx = logo.load()
    ocrop = src.crop(LOGO)
    oc = ocrop.load()
    mask = [[sum(oc[xx, yy]) > 380 for xx in range(ow)] for yy in range(oh)]
    for yy in range(oh):
        for xx in range(ow):
            if not mask[yy][xx]:
                continue
            edge = (
                xx == 0 or yy == 0 or xx == ow - 1 or yy == oh - 1
                or not mask[yy][xx - 1] or not mask[yy][xx + 1]
                or not mask[yy - 1][xx] or not mask[yy + 1][xx]
            )
            r, g, b = oc[xx, yy]
            opx[xx, yy] = (
                min(255, r + 70), min(255, g + 70), min(255, b + 70),
                127 if edge else 255,
            )
    logo.save(OUT / "agent_hire_logo.png")

    # Procedural steam puff: soft radial white blob.
    import math
    sw, sh = 30, 36
    steam = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    spx = steam.load()
    for yy in range(sh):
        for xx in range(sw):
            dx = (xx - (sw - 1) / 2) / 11.0
            dy = (yy - (sh - 1) / 2) / 14.0
            q = dx * dx + dy * dy
            if q < 1.0:
                spx[xx, yy] = (255, 255, 255, int(220 * (1 + math.cos(math.pi * q)) / 2))
    steam.save(OUT / "agent_hire_steam.png")

    hard(HIRE, "agent_hire_hire.png")

    n = sum(1 for y in range(H) for x in range(W) if mpx[x, y])
    print(f"layers ok size={src.size} cloud_px={n}")
    for f in ["agent_hire_base.png", "agent_hire_clouds.png", "agent_hire_card1.png",
              "agent_hire_card2.png", "agent_hire_card3.png",
              "agent_hire_greeter.png", "agent_hire_hand.png",
              "agent_hire_lid_l.png", "agent_hire_lid_r.png",
              "agent_hire_logo.png",
              "agent_hire_steam.png", "agent_hire_hire.png"]:
        print(" ", f, (OUT / f).stat().st_size, "bytes")


if __name__ == "__main__":
    sys.exit(main())
