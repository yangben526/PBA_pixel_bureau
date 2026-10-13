# Run from repo root: python3 scripts/gen_pazada_props.py media/sprites/props  (needs Pillow; cd scripts or set PYTHONPATH=scripts)
import math, random, sys
from pazada_iso import Iso, sh, mix

OUTDIR = sys.argv[1] if len(sys.argv) > 1 else 'out'
import os; os.makedirs(OUTDIR, exist_ok=True)
DARK = (34, 28, 32)
WOOD = (176, 112, 62)
WOOD_D = (120, 72, 40)
BRASS = (214, 170, 72)
CREAM = (246, 232, 196)
STEEL = (150, 160, 170)

def save(iso, name):
    bad, size = iso.finish(f'{OUTDIR}/{name}.png')
    print(name, size, 'keyfix', bad)

# 1 floor lamp -----------------------------------------------------------
def floor_lamp():
    i = Iso(220, 420, S=64)
    i.cylinder(0, 0, 0, 0.06, 0.26, (60, 56, 62))
    i.cylinder(0, 0, 0.06, 0.04, 0.12, (80, 76, 84))
    i.cylinder(0, 0, 0.1, 2.3, 0.035, BRASS, bands=3)
    # shade (tapered, wider at bottom)
    i.cylinder(0, 0, 2.15, 0.62, 0.36, (250, 214, 120), top=(255, 236, 170), bands=5, r_top=0.22)
    # warm glow rim
    pts = i.ellipse_pts(0, 0, 2.15, 0.36, a0=-math.pi / 4, a1=3 * math.pi / 4)
    for a, b in zip(pts, pts[1:]):
        i.line(a, b, (255, 196, 90), 2)
    i.cylinder(0, 0, 2.77, 0.03, 0.22, (230, 186, 96), top=(255, 244, 200))
    # little finial
    i.cylinder(0, 0, 2.8, 0.1, 0.03, BRASS)
    save(i, 'floor_lamp')

# 2 arcade cabinet --------------------------------------------------------
def arcade():
    i = Iso(300, 480, S=64)
    body = (70, 52, 150)
    b = i.box(-0.32, -0.32, 0, 0.64, 0.64, 1.25, body)
    # control ledge sticks out on the front (left face)
    ledge = i.box(-0.32, 0.32, 1.0, 0.64, 0.16, 0.12, (50, 40, 110), top=(40, 36, 52))
    # joystick + buttons on ledge top
    j = ledge.T(0.25, 0.5)
    i.line(j, (j[0], j[1] - 9), (30, 30, 34), 2)
    i.sphere((j[0], j[1] - 10), 3, (230, 60, 60))
    for k, c in enumerate([(250, 210, 60), (70, 200, 240), (110, 220, 110)]):
        p = ledge.T(0.55 + k * 0.13, 0.5)
        i.rect(p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1, c)
    # upper cabinet (screen hood)
    up = i.box(-0.32, -0.32, 1.25, 0.64, 0.56, 0.75, body)
    # screen bezel + glowing screen on front face
    up.quad('L', 0.08, 0.12, 0.92, 0.82, (20, 20, 28))
    up.quad('L', 0.14, 0.18, 0.86, 0.76, (40, 170, 190))
    for r in range(6):
        v = 0.22 + r * 0.09
        up.fline('L', 0.16, v, 0.84, v, (60, 200, 215))
    # little sprites on screen
    for (u, v, c) in [(0.3, 0.6, (250, 230, 90)), (0.6, 0.35, (255, 110, 90)), (0.7, 0.62, (250, 250, 250))]:
        p = up.L(u, v); i.rect(p[0] - 2, p[1] - 2, p[0] + 2, p[1] + 2, c)
    # marquee
    mq = i.box(-0.32, -0.32, 2.0, 0.64, 0.64, 0.26, (250, 196, 60), top=(60, 48, 120))
    mq.quad('L', 0.1, 0.2, 0.9, 0.8, (255, 236, 130))
    for u in (0.22, 0.4, 0.58, 0.76):
        mq.quad('L', u, 0.35, u + 0.08, 0.65, (220, 80, 50))
    # side art stripes
    for k, c in enumerate([(240, 120, 60), (250, 200, 70), (70, 190, 230)]):
        v = 0.25 + k * 0.12
        b.quad('R', 0.05, v, 0.95, v + 0.07, c)
    # coin door
    b.quad('L', 0.36, 0.2, 0.64, 0.55, (40, 34, 70))
    b.quad('L', 0.44, 0.3, 0.56, 0.45, (240, 200, 80))
    save(i, 'arcade')

# 3 vending machine -------------------------------------------------------
def vending():
    i = Iso(320, 520, S=64)
    red = (206, 52, 48)
    b = i.box(-0.38, -0.3, 0, 0.76, 0.6, 2.1, red)
    # glass window
    b.quad('L', 0.07, 0.3, 0.68, 0.94, (30, 34, 44))
    b.quad('L', 0.1, 0.33, 0.65, 0.91, (150, 205, 225))
    rng = random.Random(4)
    snacks = [(250, 200, 60), (90, 180, 90), (240, 120, 50), (80, 140, 220), (240, 240, 240), (200, 60, 70)]
    for row in range(4):
        v = 0.38 + row * 0.135
        b.fline('L', 0.1, v - 0.02, 0.65, v - 0.02, (110, 120, 130), 2)
        for col in range(4):
            u = 0.14 + col * 0.13
            c = rng.choice(snacks)
            p0 = b.L(u, v); p1 = b.L(u + 0.08, v + 0.09)
            i.poly([b.L(u, v), b.L(u + 0.08, v), b.L(u + 0.08, v + 0.09), b.L(u, v + 0.09)], c)
            i.px(b.L(u + 0.02, v + 0.07), sh(c, 1.3))
    # glare
    b.fline('L', 0.5, 0.88, 0.62, 0.55, (225, 245, 250), 2)
    # coin panel
    b.quad('L', 0.74, 0.5, 0.93, 0.88, (52, 52, 60))
    b.quad('L', 0.77, 0.78, 0.9, 0.85, (90, 230, 120))
    for k in range(3):
        v = 0.56 + k * 0.06
        b.quad('L', 0.78, v, 0.89, v + 0.035, (210, 210, 200))
    # pickup slot
    b.quad('L', 0.1, 0.08, 0.65, 0.2, (30, 26, 30))
    b.quad('L', 0.12, 0.1, 0.63, 0.13, (70, 64, 70))
    # top sign band
    b.quad('L', 0.0, 0.955, 1.0, 1.0, (250, 236, 210))
    b.quad('R', 0.0, 0.88, 1.0, 0.96, (250, 236, 210))
    # side logo
    b.quad('R', 0.2, 0.5, 0.8, 0.7, (250, 236, 210))
    b.quad('R', 0.3, 0.55, 0.7, 0.65, red)
    save(i, 'vending')

# 4 filing cabinet --------------------------------------------------------
def filing_cabinet():
    # Drawers on the right (+x / SE) face so the front faces camera-right,
    # matching other office props that read toward the room.
    i = Iso(240, 380, S=64)
    col = (120, 150, 136)
    # depth along x (drawer pull), width along y (front face on R)
    b = i.box(-0.32, -0.26, 0, 0.64, 0.52, 1.35, col)
    for k in range(3):
        v0 = 0.05 + k * 0.315
        # R face is base-shaded darker; lift drawer panels so seams/handles read
        b.quad('R', 0.07, v0, 0.93, v0 + 0.27, sh(col, 0.92))
        b.fline('R', 0.07, v0, 0.93, v0, sh(col, 0.45))
        b.quad('R', 0.36, v0 + 0.17, 0.64, v0 + 0.22, (60, 64, 70))
        b.quad('R', 0.4, v0 + 0.08, 0.6, v0 + 0.14, (238, 232, 214))
    # stuff on top: a paper tray + papers
    t = i.box(-0.22, -0.18, 1.35, 0.38, 0.3, 0.06, (60, 60, 66))
    i.box(-0.18, -0.15, 1.41, 0.3, 0.24, 0.03, (248, 246, 236), edges=False)
    save(i, 'filing_cabinet')

# 5 cactus ----------------------------------------------------------------
def cactus():
    i = Iso(220, 380, S=64)
    pot = (196, 104, 64)
    i.cylinder(0, 0, 0, 0.42, 0.24, pot, top=sh(pot, 0.8), r_top=0.28)
    i.cylinder(0, 0, 0.36, 0.08, 0.3, sh(pot, 1.05), top=(92, 64, 44))
    g = (84, 160, 72)
    # trunk + arms
    i.cylinder(0, 0, 0.42, 1.25, 0.11, g, top=sh(g, 1.2))
    cx, cy = i.P(0, 0, 0.42)
    # left arm
    for (dx, z0, z1) in [(-1, 0.85, 1.35), (1, 0.65, 1.1)]:
        ax = cx + dx * 18
        _, ya = i.P(0, 0, z0); _, yb = i.P(0, 0, z1)
        i.rect(min(cx, ax), ya - 5, max(cx, ax), ya + 4, sh(g, 0.92 if dx < 0 else 0.78))
        i.rect(ax - 5, yb, ax + 5, ya + 4, sh(g, 1.0 if dx < 0 else 0.8))
        i.rect(ax - 2, yb, ax + 1, ya, sh(g, 1.18 if dx < 0 else 0.92))
        i.disc(0, 0, 0, 0, g)
        i.rect(ax - 4, yb - 2, ax + 4, yb + 1, sh(g, 1.25))
    # spines
    rng = random.Random(2)
    for _ in range(40):
        z = rng.uniform(0.5, 1.6); t = rng.uniform(-0.8, 0.8)
        p = i.P(0, 0, z)
        i.px((p[0] + t * 9, p[1]), (236, 236, 200))
    # flower
    top = i.P(0, 0, 1.69)
    for dx, dy in [(-3, 0), (3, 0), (0, -3), (0, 2)]:
        i.rect(top[0] + dx - 2, top[1] + dy - 2, top[0] + dx + 1, top[1] + dy + 1, (255, 120, 110))
    i.rect(top[0] - 1, top[1] - 1, top[0] + 1, top[1] + 1, (255, 220, 90))
    save(i, 'cactus')

# 6 aquarium --------------------------------------------------------------
def aquarium():
    i = Iso(340, 420, S=64)
    stand = i.box(-0.42, -0.3, 0, 0.84, 0.6, 0.75, WOOD_D)
    stand.quad('L', 0.06, 0.12, 0.48, 0.86, sh(WOOD_D, 1.12))
    stand.quad('L', 0.52, 0.12, 0.94, 0.86, sh(WOOD_D, 1.12))
    for u in (0.42, 0.58):
        p = stand.L(u, 0.5); i.rect(p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1, BRASS)
    water = (60, 150, 200)
    tank = i.box(-0.4, -0.28, 0.75, 0.8, 0.56, 0.8, water, top=(150, 215, 235), right=(40, 116, 170))
    # gravel
    tank.quad('L', 0.0, 0.0, 1.0, 0.16, (214, 190, 140))
    tank.quad('R', 0.0, 0.0, 1.0, 0.16, (170, 148, 106))
    # plants
    for u, hgt, c in [(0.12, 0.7, (60, 170, 80)), (0.2, 0.5, (40, 140, 70)), (0.85, 0.65, (70, 180, 90))]:
        for k in range(4):
            uu = u + (k - 1.5) * 0.02
            tank.fline('L', uu, 0.14, uu + 0.03 * math.sin(k), 0.14 + hgt * (0.7 + 0.1 * k), c, 2)
    tank.fline('R', 0.5, 0.14, 0.55, 0.7, (60, 170, 80), 2)
    # fish
    for (u, v, c) in [(0.4, 0.55, (255, 140, 40)), (0.62, 0.35, (255, 220, 70)), (0.3, 0.3, (250, 100, 80))]:
        p = tank.L(u, v)
        i.rect(p[0] - 4, p[1] - 2, p[0] + 3, p[1] + 2, c)
        i.poly([(p[0] + 3, p[1]), (p[0] + 7, p[1] - 3), (p[0] + 7, p[1] + 3)], sh(c, 0.85))
        i.px((p[0] - 2, p[1] - 1), (20, 20, 20))
    # bubbles
    for v in (0.4, 0.55, 0.7, 0.82):
        p = tank.L(0.72 + 0.02 * math.sin(v * 9), v)
        i.px(p, (230, 250, 255)); i.px((p[0] + 1, p[1]), (190, 230, 245))
    # glass highlight + frame edges
    tank.fline('L', 0.08, 0.95, 0.2, 0.4, (200, 240, 250))
    for u in (0.0, 1.0):
        tank.fline('L', u, 0, u, 1, (40, 44, 52), 2)
    tank.fline('R', 1.0, 0, 1.0, 1, (40, 44, 52), 2)
    # lid
    i.box(-0.42, -0.3, 1.55, 0.84, 0.6, 0.07, (44, 48, 56))
    save(i, 'aquarium')

# 7 pouf ------------------------------------------------------------------
def pouf():
    i = Iso(260, 260, S=64)
    col = (60, 170, 160)
    i.cylinder(0, 0, 0, 0.36, 0.3, col, top=sh(col, 1.2), r_top=0.34)
    # puffy top dome
    i.poly(i.ellipse_pts(0, 0, 0.36, 0.34), sh(col, 1.15))
    i.poly(i.ellipse_pts(-0.04, -0.04, 0.4, 0.26), sh(col, 1.24))
    i.poly(i.ellipse_pts(-0.08, -0.08, 0.42, 0.14), sh(col, 1.32))
    # stitched seams
    c = i.P(0, 0, 0.42)
    for a in range(0, 360, 60):
        e = i.P(0.33 * math.cos(math.radians(a)), 0.33 * math.sin(math.radians(a)), 0.36)
        i.line(c, e, sh(col, 0.9))
    i.rect(c[0] - 2, c[1] - 1, c[0] + 2, c[1] + 1, (240, 210, 90))
    # piping bands on the visible (front) half
    for z, r in ((0.05, 0.305), (0.34, 0.338)):
        pts = i.ellipse_pts(0, 0, z, r, a0=-math.pi / 4, a1=3 * math.pi / 4)
        for a, b in zip(pts, pts[1:]):
            i.line(a, b, (240, 210, 90), 2)
    save(i, 'pouf')

# 8 printer / copier ------------------------------------------------------
def printer():
    i = Iso(320, 380, S=64)
    body = (226, 226, 220)
    base = i.box(-0.36, -0.3, 0, 0.72, 0.6, 0.7, (200, 202, 200))
    for k in range(2):
        v0 = 0.1 + k * 0.42
        base.quad('L', 0.06, v0, 0.94, v0 + 0.34, (214, 216, 212))
        base.quad('L', 0.3, v0 + 0.22, 0.7, v0 + 0.28, (80, 84, 92))
    i.box(-0.36, -0.3, 0.7, 0.72, 0.6, 0.06, (60, 64, 72), edges=False)
    top = i.box(-0.36, -0.3, 0.76, 0.72, 0.6, 0.36, body)
    top.quad('L', 0.1, 0.25, 0.8, 0.55, (50, 54, 62))  # output slot
    # paper coming out
    i.poly([top.L(0.18, 0.4), top.L(0.72, 0.4), (top.L(0.72, 0.4)[0] - 6, top.L(0.72, 0.4)[1] + 10),
            (top.L(0.18, 0.4)[0] - 6, top.L(0.18, 0.4)[1] + 10)], (250, 250, 246))
    lid = i.box(-0.36, -0.3, 1.12, 0.72, 0.6, 0.06, (70, 76, 86))
    # control panel on top
    cp = i.box(-0.36, 0.05, 1.18, 0.5, 0.25, 0.05, (52, 56, 64), top=(44, 48, 56))
    p = cp.T(0.3, 0.5); i.rect(p[0] - 5, p[1] - 2, p[0] + 5, p[1] + 2, (110, 220, 140))
    for k in range(3):
        q = cp.T(0.65 + k * 0.1, 0.5); i.px(q, (240, 200, 70) if k == 0 else (230, 230, 230))
    # paper tray on the right side
    i.box(-0.1, -0.42, 0.25, 0.3, 0.12, 0.05, (190, 192, 190))
    save(i, 'printer')

# 9 globe -----------------------------------------------------------------
def globe():
    i = Iso(240, 380, S=64)
    i.cylinder(0, 0, 0, 0.08, 0.24, WOOD_D, top=WOOD)
    i.cylinder(0, 0, 0.08, 0.7, 0.04, WOOD, bands=3)
    c = i.P(0, 0, 1.2)
    R = 30
    i.sphere(c, R, (60, 130, 210))
    rng = random.Random(7)
    land = [(-10, -12, 9, 7), (8, -4, 10, 12), (-14, 8, 8, 6), (4, 16, 6, 4)]
    for (dx, dy, w, h) in land:
        for _ in range(w * h):
            x = c[0] + dx + rng.uniform(-w, w) * 0.8
            y = c[1] + dy + rng.uniform(-h, h) * 0.8
            if (x - c[0]) ** 2 + (y - c[1]) ** 2 < (R - 1) ** 2:
                shade = 1.15 if (x - c[0]) + (y - c[1]) < 0 else 0.85
                i.px((x, y), sh((96, 178, 84), shade))
    # meridian ring (brass arc, tilted)
    for a in range(-110, 111, 2):
        t = math.radians(a)
        x = c[0] + (R + 4) * math.sin(t) * 0.35 + 4
        y = c[1] - (R + 4) * math.cos(t)
        i.rect(x - 1, y - 1, x, y, BRASS)
    i.rect(c[0] + 2, c[1] + R + 2, c[0] + 6, c[1] + R + 6, BRASS)
    save(i, 'globe')

# 10 guitar on stand ------------------------------------------------------
def guitar():
    i = Iso(220, 420, S=64)
    # stand legs
    for a, b in [((-0.2, 0.2, 0), (0, 0, 0.6)), ((0.2, -0.2, 0), (0, 0, 0.6)), ((0.15, 0.25, 0), (0, 0, 0.6))]:
        i.line(i.P(*a), i.P(*b), (40, 40, 46), 2)
    base = i.P(0.02, 0.02, 0.15)
    body = (220, 120, 50)
    cx, cy = base[0], base[1] - 34
    # lower bout
    def blob(x, y, rx, ry, col):
        for yy in range(-ry, ry + 1):
            for xx in range(-rx, rx + 1):
                if (xx / rx) ** 2 + (yy / ry) ** 2 <= 1:
                    k = 1.12 if xx < -rx * 0.3 else (0.85 if xx > rx * 0.45 else 1.0)
                    i.px((x + xx, y + yy), sh(col, k))
    blob(cx, cy, 22, 20, body)
    blob(cx - 2, cy - 26, 16, 14, body)
    blob(cx - 1, cy - 12, 15, 10, body)
    # sunburst edge
    # sound hole
    blob(cx - 1, cy - 14, 6, 6, (40, 24, 18))
    # bridge
    i.rect(cx - 8, cy + 6, cx + 6, cy + 8, (70, 40, 24))
    # neck
    for k in range(60):
        x = cx - 2 - k * 0.12
        y = cy - 36 - k
        i.rect(x - 2, y, x + 2, y, (120, 74, 40))
    for k in range(0, 56, 7):
        i.px((cx - 2 - k * 0.12 - 2, cy - 36 - k), (220, 210, 170))
    # headstock
    hx, hy = cx - 9.5, cy - 104
    i.rect(hx - 4, hy - 4, hx + 3, hy + 8, (60, 36, 24))
    for k in range(3):
        i.px((hx - 5, hy - 2 + k * 4), (220, 200, 120)); i.px((hx + 4, hy - 2 + k * 4), (220, 200, 120))
    # strings
    i.line((cx - 3, cy + 7), (hx - 1, hy + 6), (232, 228, 210))
    i.line((cx - 1, cy + 7), (hx + 1, hy + 6), (232, 228, 210))
    save(i, 'guitar')

# 11 grandfather clock ----------------------------------------------------
def clock():
    i = Iso(240, 500, S=64)
    w = (128, 70, 40)
    base = i.box(-0.25, -0.22, 0, 0.5, 0.44, 0.3, sh(w, 0.9))
    trunk = i.box(-0.2, -0.18, 0.3, 0.4, 0.36, 1.25, w)
    trunk.quad('L', 0.18, 0.08, 0.82, 0.88, (40, 28, 26))
    # pendulum
    p0 = trunk.L(0.5, 0.86); p1 = trunk.L(0.5, 0.3)
    i.line(p0, p1, BRASS)
    i.sphere((p1[0], p1[1]), 4, BRASS)
    head = i.box(-0.26, -0.24, 1.55, 0.52, 0.48, 0.55, w)
    # face
    c = head.L(0.5, 0.5)
    for yy in range(-13, 14):
        for xx in range(-13, 14):
            if xx * xx + yy * yy <= 169:
                i.px((c[0] + xx, c[1] + yy + xx * 0.45), (246, 236, 206) if xx * xx + yy * yy < 130 else BRASS)
    for h in range(12):
        a = math.radians(h * 30)
        i.px((c[0] + 9 * math.sin(a), c[1] - 9 * math.cos(a) + 9 * math.sin(a) * 0.45), (50, 40, 36))
    i.line(c, (c[0], c[1] - 8), (30, 24, 24))
    i.line(c, (c[0] + 6, c[1] + 4), (30, 24, 24))
    # crown
    crown = i.box(-0.29, -0.27, 2.1, 0.58, 0.54, 0.08, sh(w, 1.1))
    i.cylinder(0, 0, 2.18, 0.08, 0.06, BRASS)
    save(i, 'clock')

# 12 neon sign ------------------------------------------------------------
def neon_sign():
    i = Iso(300, 420, S=64)
    # two legs and a dark board facing down-left
    for x in (-0.32, 0.28):
        i.box(x, -0.05, 0, 0.05, 0.05, 0.8, (60, 60, 66))
    board = i.box(-0.38, -0.05, 0.75, 0.76, 0.08, 0.95, (36, 36, 48))
    board.quad('L', 0.05, 0.07, 0.95, 0.93, (24, 24, 34))
    # neon "OPEN 24/7"-ish squiggles: a coffee cup + text bar
    pink = (255, 110, 120)
    cyan = (90, 230, 240)
    yel = (255, 220, 90)
    def stroke(pts, col):
        sp = [board.L(u, v) for u, v in pts]
        for a, b in zip(sp, sp[1:]):
            i.line(a, b, sh(col, 0.7), 3)
        for a, b in zip(sp, sp[1:]):
            i.line(a, b, col, 1)
    # cup
    stroke([(0.15, 0.75), (0.18, 0.42), (0.38, 0.42), (0.41, 0.75), (0.15, 0.75)], cyan)
    stroke([(0.41, 0.68), (0.47, 0.66), (0.47, 0.55), (0.40, 0.52)], cyan)
    stroke([(0.22, 0.82), (0.24, 0.88)], pink); stroke([(0.31, 0.82), (0.33, 0.9)], pink)
    # "PB" letters
    stroke([(0.56, 0.3), (0.56, 0.75), (0.68, 0.73), (0.69, 0.6), (0.56, 0.55)], pink)
    stroke([(0.74, 0.3), (0.74, 0.75), (0.86, 0.73), (0.87, 0.62), (0.75, 0.55), (0.86, 0.5), (0.86, 0.35), (0.74, 0.3)], yel)
    stroke([(0.12, 0.2), (0.88, 0.2)], cyan)
    save(i, 'neon_sign')

# 13 picture frame on easel -----------------------------------------------
def picture_frame():
    i = Iso(240, 380, S=64)
    for a, b in [((-0.22, 0.12, 0), (-0.05, 0.0, 1.3)), ((0.2, 0.12, 0), (0.05, 0.0, 1.3)), ((0.0, -0.3, 0), (0.0, -0.02, 1.2))]:
        i.line(i.P(*a), i.P(*b), WOOD, 3)
    i.box(-0.3, 0.1, 0.42, 0.6, 0.06, 0.04, WOOD)
    fr = i.box(-0.32, 0.0, 0.46, 0.64, 0.06, 0.82, (200, 160, 70), top=(230, 196, 100))
    fr.quad('L', 0.08, 0.08, 0.92, 0.92, (150, 205, 235))
    fr.quad('L', 0.08, 0.08, 0.92, 0.4, (90, 170, 90))
    i.poly([fr.L(0.2, 0.4), fr.L(0.42, 0.75), fr.L(0.62, 0.4)], (110, 110, 130))
    i.poly([fr.L(0.45, 0.4), fr.L(0.66, 0.66), fr.L(0.88, 0.4)], (140, 140, 160))
    i.poly([fr.L(0.36, 0.66), fr.L(0.42, 0.75), fr.L(0.48, 0.66)], (250, 250, 250))
    p = fr.L(0.78, 0.8); i.sphere(p, 4, (255, 220, 90))
    save(i, 'picture_frame')

# 14 storage boxes ---------------------------------------------------------
def storage_box():
    i = Iso(300, 320, S=64)
    card = (200, 150, 96)
    def cbox(x, y, z, dx, dy, dz, label=True):
        b = i.box(x, y, z, dx, dy, dz, card)
        b.quad('T', 0.45, 0.0, 0.55, 1.0, (230, 200, 140))  # tape
        b.quad('L', 0.45, 0.75, 0.55, 1.0, (230, 200, 140))
        if label:
            b.quad('L', 0.12, 0.3, 0.38, 0.6, (246, 240, 226))
            b.fline('L', 0.15, 0.48, 0.34, 0.48, (90, 90, 100))
            b.fline('L', 0.15, 0.4, 0.3, 0.4, (90, 90, 100))
        # arrows on side
        b.quad('R', 0.4, 0.35, 0.6, 0.55, sh(card, 0.6))
        return b
    cbox(-0.42, -0.4, 0, 0.5, 0.8, 0.5)
    cbox(0.1, -0.1, 0, 0.36, 0.5, 0.36)
    cbox(-0.36, -0.3, 0.5, 0.42, 0.6, 0.38, label=False)
    save(i, 'storage_box')

ALL = [floor_lamp, arcade, vending, filing_cabinet, cactus, aquarium, pouf, printer, globe, guitar, clock, neon_sign, picture_frame, storage_box]
for f in ALL:
    f()
