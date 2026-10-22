"""Kairosoft-style revamp of the Pixegle portal PixelHire + Pmart icons.

Same compositions and filenames (drop-in, ContentScale.Fit), repainted:
bold ink sticker ring, saturated cel tones, thickened line art, gloss,
blush/sparkle accents. Deterministic; overwrites the two drawables.
Run from repo root: python3 scripts/revamp_portal_icons.py
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance
from scipy import ndimage as nd

ROOT = Path(__file__).resolve().parent.parent
DRAW = ROOT / "app/src/main/res/drawable-nodpi"

INK = (30, 27, 40)
PAD = 7


ORIGINAL_SIZES = {"portal_pixelhire": (108, 103), "portal_pmart": (105, 99)}


def load(name, saturation, brightness):
    im = Image.open(DRAW / f"{name}.png").convert("RGBA")
    if im.size != ORIGINAL_SIZES[name]:
        raise SystemExit(f"{name}.png is {im.size}, expected original {ORIGINAL_SIZES[name]}; restore originals before re-running")
    rgb = ImageEnhance.Color(im.convert("RGB")).enhance(saturation)
    rgb = ImageEnhance.Brightness(rgb).enhance(brightness)
    rgb.putalpha(im.getchannel("A"))
    big = Image.new("RGBA", (im.width + 2 * PAD, im.height + 2 * PAD), (0, 0, 0, 0))
    big.alpha_composite(rgb, (PAD, PAD))
    a = np.array(big).astype(np.int16)
    a[..., 3] = np.where(a[..., 3] > 128, 255, 0)
    return a


def unify_ink(a, sil):
    lum = a[..., :3].sum(axis=2) / 3.0
    a[sil & (lum < 55)] = (*INK, 255)
    return a


def sticker(a, sil, width=3):
    ext = ~nd.binary_fill_holes(sil)
    ext = nd.binary_opening(ext, iterations=3)
    shadow = nd.binary_dilation(sil, iterations=2)
    shadow = np.roll(np.roll(shadow, 3, axis=0), 2, axis=1)
    shadow_only = shadow & ~sil & ext
    a[shadow_only] = (40, 30, 40, 90)
    ring = nd.binary_dilation(sil, iterations=width) & ~sil & ext
    a[ring] = (*INK, 255)
    return a, sil | ring | shadow_only


def gloss_layer(w, h, shapes):
    ov = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    for kind, box, fill in shapes:
        if kind == "ellipse":
            d.ellipse(box, fill=fill)
        else:
            d.rectangle(box, fill=fill)
    return ov


def revamp_pixelhire():
    a = load("portal_pixelhire", 1.12, 1.02)
    sil = a[..., 3] > 128
    r, g, b = a[..., 0], a[..., 1], a[..., 2]

    greens = sil & (g > r + 40) & (g > b + 40)
    a[greens] = (25, 205, 75, 255)
    gy, gx = np.nonzero(greens)

    browns = sil & (r > 60) & (r < 170) & (g > 30) & (g < 120) & (b > 10) & (b < 90) & (r > b + 30)
    hy, hx = np.nonzero(browns)

    skin = sil & (r > 200) & (g > 150) & (b > 110) & (r > b + 20)
    yys, xxs = np.nonzero(skin)
    cheek = ((xxs > 8 + PAD) & (xxs < 20 + PAD) | (xxs > 40 + PAD) & (xxs < 52 + PAD)) & (yys > 48 + PAD) & (yys < 58 + PAD)
    yy, xx = yys[cheek], xxs[cheek]
    if len(xx):
        blush = (a[yy, xx][..., :3] * 0.55 + np.array([255, 130, 150]) * 0.45).astype(int)
        a[yy, xx] = np.concatenate([blush, np.full((blush.shape[0], 1), 255)], axis=1)

    a = unify_ink(a, sil)
    H, W = sil.shape
    shapes = []
    if len(gx):
        shapes.append(("ellipse", (int(gx.min()), int(gy.min()) + 2, int(gx.min()) + 18, int(gy.min()) + 12), (255, 255, 255, 115)))
        shapes.append(("ellipse", (int(gx.min()) + 2, int(gy.min()) + 4, int(gx.min()) + 10, int(gy.min()) + 9), (255, 255, 255, 180)))
    if len(hx):
        shapes.append(("ellipse", (int(hx.min()) + 1, int(hy.min()) + 1, int(hx.min()) + 17, int(hy.min()) + 9), (255, 255, 255, 100)))
    im = Image.fromarray(a.astype(np.uint8))
    im.alpha_composite(gloss_layer(W, H, shapes))
    a = np.array(im).astype(np.int16)
    sil = a[..., 3] > 128
    a, _ = sticker(a, sil)
    Image.fromarray(a.astype(np.uint8)).save(DRAW / "portal_pixelhire.png")
    print("wrote portal_pixelhire", a.shape[1], a.shape[0])


def revamp_pmart():
    a = load("portal_pmart", 1.14, 1.03)
    sil = a[..., 3] > 128
    r, g, b = a[..., 0], a[..., 1], a[..., 2]

    yel = sil & (r > 200) & (g > 140) & (b < 120)
    a[yel] = (255, 195, 0, 255)
    core = nd.binary_erosion(yel, iterations=1)
    soft = (a[core][..., :3] * 0.2 + np.array([255, 246, 220]) * 0.8).astype(int)
    a[core] = np.concatenate([soft, np.full((soft.shape[0], 1), 255)], axis=1)

    a = unify_ink(a, sil)
    H, W = sil.shape
    strokes = sil & (b > 150) & (b > r + 40)
    sy, sx = np.nonzero(strokes)
    shapes = []
    if len(sx):
        shapes.append(("rect", (int(sx.min()) + 10, int(sy.min()) + 1, int(sx.min()) + 34, int(sy.min()) + 3), (255, 255, 255, 130)))
    im = Image.fromarray(a.astype(np.uint8))
    im.alpha_composite(gloss_layer(W, H, shapes))
    a = np.array(im).astype(np.int16)
    sil = a[..., 3] > 128
    a, _ = sticker(a, sil)
    Image.fromarray(a.astype(np.uint8)).save(DRAW / "portal_pmart.png")
    print("wrote portal_pmart", a.shape[1], a.shape[0])


def main():
    revamp_pixelhire()
    revamp_pmart()
    return 0


if __name__ == "__main__":
    sys.exit(main())
