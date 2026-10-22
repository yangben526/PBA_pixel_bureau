# Run from repo root: python3 scripts/gen_desk_oak.py  (needs numpy, scipy, Pillow)
"""Retired. Dream Starter desk art is the supplied illustration.

media/sprites/props/desk_oak.png and shop_thumb_oakwood_desk.png are that
picture, magenta-keyed. Running the old generator would overwrite them.
Startup Premium stays media/sprites/props/desk.png.
"""
import sys
sys.exit("desk_oak.png is the Dream Starter illustration; this generator is retired")
import numpy as np
from PIL import Image
from scipy import ndimage as nd

desk = np.asarray(Image.open('media/sprites/props/desk.png').convert('RGB')).astype(np.int16)
bare = np.asarray(Image.open('media/sprites/props/desk_bare.png').convert('RGB')).astype(np.int16)


def keymask(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    k1 = (r > 130) & (b > 130) & (g < r) & (g < b) & (r + b - 2 * g > 50)
    k2 = (
        (r > g * 1.6) & (b > g * 1.6) & (r + b - 2 * g > 55)
        & (np.maximum(r, b) > 60) & (np.minimum(r, b) > 65) & (np.abs(r - b) <= 28)
    )
    return k1 | k2


out = bare.copy()
out[keymask(out)] = [255, 0, 255]
H, W = out.shape[:2]
# ---- rebuild the back edge + inpaint the ghost of the old laptop/mug.
OUTL = np.array([5, 3, 5], dtype=np.int16)


def top_y(x):
    return int(round(max(420 - 0.52 * (x - 300), 263 + 0.52 * (x - 620)))) - 2


RX0, RX1, RY0, RY1 = 290, 628, 200, 548
region = np.zeros((H, W), bool)
region[RY0:RY1, RX0:RX1] = True
# ---- back edge rebuild (unchanged geometry)
for x in range(RX0, RX1):
    t = top_y(x)
    out[RY0:t, x] = [255, 0, 255]
    out[t:t + 11, x] = OUTL

# ---- extract laptop + mug mask first (same code, moved up: the wood fill
# ---- skips pixels the paste will cover)
x0, y0, x1, y1 = 350, 140, 720, 520
reg = desk[y0:y1, x0:x1].copy()
r, g, b = reg[..., 0], reg[..., 1], reg[..., 2]
wood = (r > g) & (g > b) & (r - b > 55) & (r > 140) & (g > 80)
mag = keymask(reg)
rim = nd.binary_dilation(mag, iterations=7) & ((r + g + b) < 200)
keep = ~wood & ~mag & ~rim
keep = nd.binary_opening(keep, iterations=1)
lab, n = nd.label(keep)
sizes = nd.sum(keep, lab, range(1, n + 1))
big = [i + 1 for i, s in enumerate(sizes) if s > 1500]
m = np.isin(lab, big)
m = nd.binary_fill_holes(m)
white = (r > 200) & (g > 200) & (b > 200) & ~mag
m |= white
lap = (lab == 1) | (white & nd.binary_dilation(lab == 1, iterations=2))
print('comps', len(big), [int(s) for s in sizes if s > 1500])

dx, dy = -80, 50
paste = np.zeros((H, W), bool)
pys, pxs = np.nonzero(m)
paste[pys + y0 + dy, pxs + x0 + dx] = True
paste_inner = nd.binary_erosion(paste, iterations=3)

# ---- redraw the work-zone wood: local-tone base (wide mean of clean-wood
# ---- donors, no directional streaks) + crisp 1px procedural grain at the
# ---- measured 27° grain angle + faint tooth. Seeded: deterministic.
bk = keymask(out)
below_edge = np.zeros((H, W), bool)
for x in range(RX0, RX1):
    below_edge[top_y(x) + 11:RY1, x] = True
fill = region & below_edge & ~bk & ~paste_inner
donor = (
    (out[..., 0] > out[..., 1]) & (out[..., 1] > out[..., 2])
    & (out[..., 0] - out[..., 2] > 55) & (out[..., 0] > 140) & (out[..., 1] > 80)
    & ~bk & ~nd.binary_dilation(region, iterations=25)
)
print('donor px', int(donor.sum()))
w = donor.astype(np.float32)
num = np.stack([
    nd.uniform_filter(np.where(donor, out[..., c].astype(np.float32), 0.0), size=401, mode='nearest')
    for c in range(3)
], axis=-1)
den = nd.uniform_filter(w, size=401, mode='nearest')[..., None]
synth = num / np.maximum(den, 1e-6)
synth[den[..., 0] < 1e-3] = [229.0, 157.0, 92.0]
rng = np.random.default_rng(7)
grain = np.zeros((H, W), bool)
gx, gy = np.cos(np.radians(27.0)), np.sin(np.radians(27.0))
streaks = 0
for _ in range(1500):
    x = float(rng.integers(RX0, RX1))
    y = float(rng.integers(RY0, RY1))
    length = int(rng.integers(20, 90))
    wob = rng.uniform(-0.05, 0.05)
    for _ in range(length):
        xi, yi = int(round(x)), int(round(y))
        if 0 <= xi < W and 0 <= yi < H and fill[yi, xi]:
            grain[yi, xi] = True
            streaks += 1
        x += gx + wob
        y += gy + wob * 0.5
synth[grain] -= [21.0, 28.0, 16.0]
synth += rng.uniform(-3.0, 3.0, size=synth.shape)
e1 = nd.binary_erosion(fill, iterations=1)
e2 = nd.binary_erosion(fill, iterations=2)
alpha = np.zeros((H, W), np.float32)
alpha[fill] = 0.34
alpha[e1] = 0.67
alpha[e2] = 1.0
a = alpha[..., None]
out_f = out.astype(np.float32)
out_f[fill] = synth[fill] * a[fill] + out_f[fill] * (1 - a[fill])
out = np.clip(out_f, 0, 255).astype(np.int16)
print('fill px', int(fill.sum()), 'grain px', streaks)

# ---- morph the extracted laptop → generic PC (unchanged code)


def bbox_full(a):
    k = ~keymask(a)
    ys, xs = np.nonzero(k)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


bx0, by0, bx1, by1 = bbox_full(desk)
bw, bh = bx1 - bx0, by1 - by0
# Premium desk glass fractions (OfficeCanvas DeskGlass) — used to locate the screen.
fr = [(0.3629, 0.1710), (0.5553, 0.1094), (0.5735, 0.2908), (0.3784, 0.3761)]
gq = np.array([(bx0 + fx * bw - x0, by0 + fy * bh - y0) for fx, fy in fr], dtype=float)


def point_in_tri(xx, yy, p0, p1, p2):
    v0 = p2 - p0
    v1 = p1 - p0
    v2x = xx - p0[0]
    v2y = yy - p0[1]
    den = v0[0] * v1[1] - v1[0] * v0[1]
    a = (v2x * v1[1] - v1[0] * v2y) / den
    b = (v0[0] * v2y - v2x * v0[1]) / den
    return (a >= 0) & (b >= 0) & (a + b <= 1)


yy, xx = np.mgrid[0:reg.shape[0], 0:reg.shape[1]].astype(float)
glass_full = point_in_tri(xx, yy, gq[0], gq[1], gq[2]) | point_in_tri(xx, yy, gq[0], gq[2], gq[3])
glass = nd.binary_erosion(glass_full, iterations=1)


def uv_quad(xx, yy):
    tl, tr, br, bl = gq
    e1 = tr - tl
    e2 = bl - tl
    C = (br - tl) - e1 - e2
    uu = np.zeros_like(xx)
    vv = np.zeros_like(yy)
    for _ in range(5):
        xu = e1[0] + vv * C[0]
        yu = e1[1] + vv * C[1]
        xv = e2[0] + uu * C[0]
        yv = e2[1] + uu * C[1]
        rx = tl[0] + uu * e1[0] + vv * e2[0] + uu * vv * C[0] - xx
        ry = tl[1] + uu * e1[1] + vv * e2[1] + uu * vv * C[1] - yy
        det = xu * yv - yu * xv
        det = np.where(np.abs(det) < 1e-6, 1e-6, det)
        uu = uu - (rx * yv - ry * xv) / det
        vv = vv - (xu * ry - yu * rx) / det
    return uu, vv


uu, vv = uv_quad(xx, yy)
reg_f = reg.astype(np.float32)
lum = (reg_f[..., 0] + reg_f[..., 1] + reg_f[..., 2]) / 3.0
chroma = np.maximum.reduce([
    np.abs(reg_f[..., 0] - reg_f[..., 1]),
    np.abs(reg_f[..., 1] - reg_f[..., 2]),
    np.abs(reg_f[..., 0] - reg_f[..., 2]),
])

# Chassis: cool silver metal → warm grey/beige plastic
chassis = lap & ~glass_full & (lum >= 75) & (chroma < 60) & (lum < 240)
if chassis.any():
    ol = lum[chassis]
    lo, hi = np.percentile(ol, [5, 95])
    t = np.clip((ol - lo) / (hi - lo + 1e-6), 0, 1)
    dark = np.array([128.0, 120.0, 102.0])
    mid = np.array([174.0, 164.0, 140.0])
    lit = np.array([194.0, 184.0, 158.0])
    c = np.where(
        t[:, None] < 0.5,
        dark + (mid - dark) * (t[:, None] * 2),
        mid + (lit - mid) * ((t[:, None] - 0.5) * 2),
    )
    xs = np.nonzero(chassis)[1]
    xnorm = (xs - xs.min()) / (xs.max() - xs.min() + 1e-6)
    c *= 0.92 + 0.12 * xnorm[:, None]
    reg_f[chassis] = np.clip(c, 0, 255)
scrap = lap & ~glass_full & ~chassis & (lum > 120) & (chroma < 65)
reg_f[scrap] = np.array([165.0, 155.0, 132.0])

bot_y = 0.5 * (gq[2][1] + gq[3][1])
mid_x = 0.5 * (gq[2][0] + gq[3][0])
lum = (reg_f[..., 0] + reg_f[..., 1] + reg_f[..., 2]) / 3.0
chroma = np.maximum.reduce([
    np.abs(reg_f[..., 0] - reg_f[..., 1]),
    np.abs(reg_f[..., 1] - reg_f[..., 2]),
    np.abs(reg_f[..., 0] - reg_f[..., 2]),
])
track = lap & ~glass_full & (yy > bot_y + 12) & (yy < bot_y + 68) & (xx > mid_x - 50) & (xx < mid_x + 65)
track &= (lum > 110) & (chroma < 55)
reg_f[track] = np.array([145.0, 137.0, 118.0])
border = nd.binary_dilation(track, iterations=1) & ~track & lap & ~glass_full & (lum > 95)
reg_f[border] = np.array([115.0, 107.0, 90.0])

# Full screen: PC bliss-like wallpaper + Windows taskbar (removes notch/dock/menu)
ys, xs = np.nonzero(glass)
for y, x in zip(ys, xs):
    u = float(np.clip(uu[y, x], 0, 1))
    v = float(np.clip(vv[y, x], 0, 1))
    sky_top = np.array([70.0, 140.0, 210.0])
    sky_bot = np.array([180.0, 210.0, 235.0])
    col = sky_top * (1 - v * 0.85) + sky_bot * (v * 0.85)
    d = ((u - 0.78) ** 2 + (v - 0.18) ** 2) ** 0.5
    if d < 0.35:
        w = 0.25 * (1 - d / 0.35)
        col = col * (1 - w) + np.array([255.0, 230.0, 160.0]) * w
    hill = 0.72 + 0.04 * np.sin(u * 9.0) + 0.03 * np.sin(u * 3.5 + 1.0)
    if 0.55 < v < 0.82 and v > hill:
        col = np.array([55.0, 110.0, 75.0]) * (0.85 + 0.2 * ((v - hill) / 0.3))
    if v >= 0.84:
        k = 1.12 if v < 0.86 else (0.88 if v > 0.95 else 1.0)
        col = np.array([32.0, 38.0, 48.0]) * k
        if 0.03 < u < 0.10 and 0.855 < v < 0.955:
            col = np.array([40.0, 155.0, 118.0]) * (1.15 if 0.05 < u < 0.08 else 0.95)
        elif 0.13 < u < 0.185 and 0.86 < v < 0.95:
            col = np.array([72.0, 132.0, 220.0])
        elif 0.205 < u < 0.26 and 0.86 < v < 0.95:
            col = np.array([210.0, 140.0, 55.0])
        elif 0.28 < u < 0.335 and 0.86 < v < 0.95:
            col = np.array([160.0, 85.0, 175.0])
        elif u > 0.86 and 0.88 < v < 0.94:
            col = np.array([48.0, 54.0, 64.0])
    else:
        for iu, iv, cicon in (
            (0.08, 0.12, (240, 240, 250)),
            (0.08, 0.22, (240, 220, 80)),
            (0.08, 0.32, (100, 180, 255)),
        ):
            if abs(u - iu) < 0.025 and abs(v - iv) < 0.035:
                col = np.array(cicon, dtype=float)
    reg_f[y, x] = np.clip(col, 0, 255)

# Flat top bezel + reinforce rim (no notch)
top_bezel = glass_full & (vv < 0.04)
reg_f[top_bezel] = np.array([28.0, 28.0, 32.0])
notch_rem = glass & (vv < 0.10) & (uu > 0.40) & (uu < 0.60)
for y, x in zip(*np.nonzero(notch_rem)):
    if reg_f[y, x].sum() < 120:
        u = float(uu[y, x])
        v = float(vv[y, x])
        sky_top = np.array([70.0, 140.0, 210.0])
        sky_bot = np.array([180.0, 210.0, 235.0])
        reg_f[y, x] = sky_top * (1 - v * 0.85) + sky_bot * (v * 0.85)
bezel_ring = glass_full & ~nd.binary_erosion(glass_full, iterations=1)
reg_f[bezel_ring] = np.array([24.0, 24.0, 28.0])
cam = glass_full & (vv < 0.032) & (uu > 0.475) & (uu < 0.525)
reg_f[cam] = np.array([50.0, 50.0, 56.0])
cam2 = glass_full & (vv < 0.022) & (uu > 0.488) & (uu < 0.512)
reg_f[cam2] = np.array([16.0, 16.0, 20.0])

# Wipe MacBook chin wordmark / logo glow
lum = (reg_f[..., 0] + reg_f[..., 1] + reg_f[..., 2]) / 3.0
chroma = np.maximum.reduce([
    np.abs(reg_f[..., 0] - reg_f[..., 1]),
    np.abs(reg_f[..., 1] - reg_f[..., 2]),
    np.abs(reg_f[..., 0] - reg_f[..., 2]),
])
logo = lap & ~glass_full & (yy > bot_y - 8) & (yy < bot_y + 22) & (np.abs(xx - mid_x) < 50) & (lum > 35)
reg_f[logo] = np.array([28.0, 28.0, 32.0])
logo2 = nd.binary_dilation(logo, iterations=1) & lap & ~glass_full
logo2 &= (yy > bot_y - 10) & (yy < bot_y + 24) & (np.abs(xx - mid_x) < 54) & (lum > 45) & (chroma < 65)
reg_f[logo2] = np.array([28.0, 28.0, 32.0])

keys_white = lap & ~glass_full & (yy > bot_y) & (lum > 165) & (chroma < 35)
reg_f[keys_white] = np.array([140.0, 132.0, 118.0])

reg_out = np.clip(reg_f, 0, 255).astype(np.int16)
ys, xs = np.nonzero(m)
out[ys + y0 + dy, xs + x0 + dx] = reg_out[ys, xs]

Image.fromarray(out.astype(np.uint8)).save('media/sprites/props/desk_oak.png')

# Glass quad for OfficeCanvas OakGlass (geometry unchanged vs Mac paste).
def bbox(a):
    k = ~keymask(a)
    ys, xs = np.nonzero(k)
    return xs.min(), ys.min(), xs.max() + 1, ys.max() + 1


cx0, cy0, cx1, cy1 = bbox(out)
cw, ch = cx1 - cx0, cy1 - cy0
absq = [(bx0 + fx * bw + dx, by0 + fy * bh + dy) for fx, fy in fr]
nq = [((x - cx0) / cw, (y - cy0) / ch) for x, y in absq]
print('quad', ', '.join('%.4f,%.4f' % q for q in nq), 'ratio w', cw / bw)

# Shop thumb: content bbox → 360x320 RGBA (magenta keyed).
content = out[cy0:cy1, cx0:cx1]
im = Image.fromarray(content.astype(np.uint8))
scale = min(360 / content.shape[1], 320 / content.shape[0])
nw, nh = max(1, int(round(content.shape[1] * scale))), max(1, int(round(content.shape[0] * scale)))
resized = np.asarray(im.resize((nw, nh), Image.NEAREST))
kk = keymask(resized.astype(np.int16))
rgba = np.zeros((nh, nw, 4), np.uint8)
rgba[..., :3] = resized
rgba[..., 3] = np.where(kk, 0, 255)
canvas = Image.new('RGBA', (360, 320), (0, 0, 0, 0))
canvas.paste(Image.fromarray(rgba), ((360 - nw) // 2, (320 - nh) // 2))
canvas.save('app/src/main/res/drawable-nodpi/shop_thumb_oakwood_desk.png')
print('wrote desk_oak.png + shop_thumb_oakwood_desk.png')
