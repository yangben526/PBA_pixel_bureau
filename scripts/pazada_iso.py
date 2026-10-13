"""Tiny isometric pixel-art kit for Pixel Bureau prop sprites (magenta keyed)."""
import math
from PIL import Image, ImageDraw

MAG = (255, 0, 255)
OUT = (28, 20, 24)

def is_key(c):
    r, g, b = c[:3]
    if r > 130 and b > 130 and g < r and g < b and r + b - 2 * g > 50:
        return True
    return (r > g * 1.6 and b > g * 1.6 and r + b - 2 * g > 55 and max(r, b) > 60
            and min(r, b) > 65 and abs(r - b) <= 28)

def sh(c, k):
    """Scale brightness (k<1 darker)."""
    return tuple(max(0, min(255, int(round(v * k)))) for v in c[:3])

def mix(a, b, t):
    return tuple(int(round(a[i] * (1 - t) + b[i] * t)) for i in range(3))

class Iso:
    def __init__(self, w=320, h=420, S=64, zs=1.15, ox=None, oy=None):
        self.img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.S = S
        self.zs = zs
        self.ox = w / 2 if ox is None else ox
        self.oy = h - S * 0.75 if oy is None else oy

    # grid (x,y in tile units centred on tile centre 0,0; z up in tile units)
    def P(self, x, y, z=0.0):
        S = self.S
        return (self.ox + (x - y) * S, self.oy + (x + y) * S / 2 - z * S * self.zs)

    def poly(self, pts, fill, outline=None):
        pts = [(round(p[0]), round(p[1])) for p in pts]
        self.d.polygon(pts, fill=fill + (255,), outline=(outline + (255,)) if outline else None)

    def line(self, a, b, col, w=1):
        self.d.line([(round(a[0]), round(a[1])), (round(b[0]), round(b[1]))], fill=col + (255,), width=w)

    def px(self, p, col):
        self.d.point((round(p[0]), round(p[1])), fill=col + (255,))

    def rect(self, x0, y0, x1, y1, col):
        self.d.rectangle([round(x0), round(y0), round(x1), round(y1)], fill=col + (255,))

    # ---------------- boxes ----------------
    def box(self, x0, y0, z0, dx, dy, dz, col, top=None, left=None, right=None, edges=True):
        x1, y1, z1 = x0 + dx, y0 + dy, z0 + dz
        P = self.P
        top = top or sh(col, 1.18)
        left = left or col
        right = right or sh(col, 0.72)
        self.poly([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], left)
        self.poly([P(x1, y1, z0), P(x1, y0, z0), P(x1, y0, z1), P(x1, y1, z1)], right)
        self.poly([P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1)], top)
        if edges:
            e = sh(col, 0.42)
            self.line(P(x1, y1, z0), P(x1, y1, z1), e)
            hl = sh(top, 1.12)
            self.line(P(x0, y1, z1), P(x1, y1, z1), hl)
            self.line(P(x1, y1, z1), P(x1, y0, z1), hl)
        return Box(self, x0, y0, z0, x1, y1, z1)

    # ---------------- round things ----------------
    def ellipse_pts(self, cx, cy, z, r, n=48, a0=0, a1=2 * math.pi):
        return [self.P(cx + r * math.cos(a0 + (a1 - a0) * i / n),
                       cy + r * math.sin(a0 + (a1 - a0) * i / n), z) for i in range(n + 1)]

    def cylinder(self, cx, cy, z0, h, r, col, top=None, bands=5, r_top=None):
        """Vertical (optionally tapered) cylinder with banded side shading."""
        r_top = r if r_top is None else r_top
        S = self.S
        cxs, cys0 = self.P(cx, cy, z0)
        _, cys1 = self.P(cx, cy, z0 + h)
        rx0, ry0 = r * S * math.sqrt(2), r * S * math.sqrt(2) / 2
        rx1, ry1 = r_top * S * math.sqrt(2), r_top * S * math.sqrt(2) / 2
        # side: per column
        steps = int(max(rx0, rx1) * 2) + 2
        for i in range(steps + 1):
            t = -1 + 2 * i / steps
            xa = cxs + t * rx0
            xb = cxs + t * rx1
            yb0 = cys0 + ry0 * math.sqrt(max(0, 1 - t * t))
            yb1 = cys1 + ry1 * math.sqrt(max(0, 1 - t * t))
            q = min(bands - 1, int((t + 1) / 2 * bands))
            k = 1.15 - 0.45 * q / max(1, bands - 1)
            c = sh(col, k)
            self.d.line([(round(xb), round(yb1)), (round(xa), round(yb0))], fill=c + (255,), width=2)
        self.poly(self.ellipse_pts(cx, cy, z0 + h, r_top), top or sh(col, 1.25))

    def disc(self, cx, cy, z, r, col):
        self.poly(self.ellipse_pts(cx, cy, z, r), col)

    def sphere(self, c, R, col, bands=5):
        """Screen-space shaded ball; c=screen centre, R=px radius."""
        cx, cy = c
        for yy in range(-int(R), int(R) + 1):
            for xx in range(-int(R), int(R) + 1):
                if xx * xx + yy * yy <= R * R:
                    # light from upper-left
                    lx, ly = xx + R * 0.4, yy + R * 0.45
                    dist = math.sqrt(lx * lx + ly * ly) / (R * 1.5)
                    q = min(bands - 1, int(dist * bands))
                    k = 1.25 - 0.5 * q / (bands - 1)
                    self.d.point((round(cx + xx), round(cy + yy)), fill=sh(col, k) + (255,))

    # ---------------- finish ----------------
    def finish(self, path, up=4, pad=6):
        a = self.img
        alpha = a.split()[3]
        # 1px silhouette outline (8-neighbour)
        W, H = a.size
        src = a.load()
        out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        o = out.load()
        for y in range(H):
            for x in range(W):
                if src[x, y][3]:
                    o[x, y] = src[x, y]
                    continue
                hit = False
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < W and 0 <= ny < H and src[nx, ny][3]:
                            hit = True
                if hit:
                    o[x, y] = OUT + (255,)
        bbox = out.getbbox()
        out = out.crop((bbox[0] - pad, bbox[1] - pad, bbox[2] + pad, bbox[3] + pad))
        # key safety: nudge any colour the app would key away
        px = out.load()
        bad = 0
        for y in range(out.size[1]):
            for x in range(out.size[0]):
                c = px[x, y]
                if c[3] and is_key(c):
                    bad += 1
                    r, g, b = c[:3]
                    while is_key((r, g, b)):
                        g = min(255, g + 8)
                    px[x, y] = (r, g, b, 255)
        bg = Image.new('RGB', out.size, MAG)
        bg.paste(out, (0, 0), out)
        big = bg.resize((bg.width * up, bg.height * up), Image.NEAREST)
        big.save(path)
        return bad, out.size

class Box:
    def __init__(s, iso, x0, y0, z0, x1, y1, z1):
        s.i, s.x0, s.y0, s.z0, s.x1, s.y1, s.z1 = iso, x0, y0, z0, x1, y1, z1
    # left face (front, faces +y / screen down-left): u: x0→x1, v: z0→z1
    def L(s, u, v):
        return s.i.P(s.x0 + u * (s.x1 - s.x0), s.y1, s.z0 + v * (s.z1 - s.z0))
    # right face (faces +x / down-right): u: y1→y0, v: z0→z1
    def R(s, u, v):
        return s.i.P(s.x1, s.y1 - u * (s.y1 - s.y0), s.z0 + v * (s.z1 - s.z0))
    # top: u along x, w along y
    def T(s, u, w):
        return s.i.P(s.x0 + u * (s.x1 - s.x0), s.y0 + w * (s.y1 - s.y0), s.z1)
    def quad(s, face, u0, v0, u1, v1, col):
        f = getattr(s, face)
        s.i.poly([f(u0, v0), f(u1, v0), f(u1, v1), f(u0, v1)], col)
    def fline(s, face, u0, v0, u1, v1, col, w=1):
        f = getattr(s, face)
        s.i.line(f(u0, v0), f(u1, v1), col, w)
