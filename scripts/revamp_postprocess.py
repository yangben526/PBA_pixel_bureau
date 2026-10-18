#!/usr/bin/env python3
"""Postprocess revamp generations into game-ready sprites.

Takes raw image-generator output (any size, magenta-ish backdrop) and writes
1024x1024 RGB magenta-backed PNGs matching media/sprites/chars conventions:
each frame scaled so its longest content side is 910px (keeper band 886..924),
content centered, background keyed to the exact keeper magenta.
Previews go to .sprite_preview/revamp/; --apply writes media/sprites/chars/.

Usage:
  revamp_postprocess.py AGENT idle=PATH sit=PATH ... [--apply]
  revamp_postprocess.py --portrait AGENT=PATH ... [--apply]
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
MAGENTA = (211, 52, 173)  # mode of keeper border pixels
TARGET_LONGEST = 910  # keeper idle content heights run 886..924
CANVAS = 1024
DESPECKLE_MIN = 50  # generator noise specks; real content (incl. sleep-Z dots) is far bigger


def key_mask(a: np.ndarray) -> np.ndarray:
    """Same backdrop test as SpriteStore.isSpriteKey."""
    r, g, b = [a[:, :, i].astype(np.int16) for i in range(3)]
    primary = (r > 130) & (b > 130) & (g < r) & (g < b) & ((r + b - 2 * g) > 50)
    fringe = (
        (r > g * 1.6) & (b > g * 1.6) & ((r + b - 2 * g) > 55)
        & (np.maximum(r, b) > 60) & (np.minimum(r, b) > 65) & (np.abs(r - b) <= 28)
    )
    return primary | fringe


def content_bbox(mask: np.ndarray) -> tuple[int, int, int, int] | None:
    ys, xs = np.nonzero(~mask)
    if len(ys) == 0:
        return None
    return int(ys.min()), int(ys.max()), int(xs.min()), int(xs.max())


def despeckle(img: Image.Image) -> Image.Image:
    """Paint tiny non-key components (generator noise) magenta."""
    a = np.array(img)
    lab, n = ndimage.label(~key_mask(a))
    if n == 0:
        return img
    sizes = ndimage.sum(np.ones_like(lab), lab, range(1, n + 1))
    kill = [i + 1 for i, s in enumerate(sizes) if s < DESPECKLE_MIN]
    if not kill:
        return img
    a[np.isin(lab, kill)] = MAGENTA
    return Image.fromarray(a, "RGB")


def process(src: Path) -> tuple[Image.Image, dict]:
    img = despeckle(Image.open(src).convert("RGB"))
    w, h = img.size
    box = content_bbox(key_mask(np.array(img)))
    assert box is not None, f"{src}: no content found"
    y0, y1, x0, x1 = box
    bh, bw = y1 - y0 + 1, x1 - x0 + 1
    edge = min(y0, x0, h - 1 - y1, w - 1 - x1)
    assert edge >= 5, f"{src}: content cut by frame edge (margin {edge})"
    scale = TARGET_LONGEST / max(bh, bw)
    art = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    # Paste so the content center lands on the canvas center.
    ccx = (x0 + x1) / 2 * scale
    ccy = (y0 + y1) / 2 * scale
    canvas = Image.new("RGB", (CANVAS, CANVAS), MAGENTA)
    canvas.paste(art, (round(CANVAS / 2 - ccx), round(CANVAS / 2 - ccy)))
    c = np.array(canvas)
    ckey = key_mask(c)
    c[:, :, 0][ckey] = MAGENTA[0]
    c[:, :, 1][ckey] = MAGENTA[1]
    c[:, :, 2][ckey] = MAGENTA[2]
    # Interior-key audit: keyed pixels inside the bbox that are not border-connected.
    cbox = content_bbox(ckey)
    stats: dict = {"scale": scale}
    if cbox is not None:
        lab, _ = ndimage.label(ckey)
        border_ids = set(np.unique(np.concatenate([lab[0, :], lab[-1, :], lab[:, 0], lab[:, -1]]))) - {0}
        inside = np.zeros_like(ckey)
        inside[cbox[0]:cbox[1] + 1, cbox[2]:cbox[3] + 1] = True
        holes = inside & ckey & ~np.isin(lab, list(border_ids))
        stats["hole_frac"] = float(holes.sum() / max(1, inside.sum()))
        stats["content_h"] = cbox[1] - cbox[0] + 1
        stats["content_w"] = cbox[3] - cbox[2] + 1
    return Image.fromarray(c, "RGB"), stats


def process_portrait(src: Path) -> Image.Image:
    """Bust on magenta -> 320x320 RGBA with transparent backdrop."""
    img = despeckle(Image.open(src).convert("RGB"))
    w, h = img.size
    box = content_bbox(key_mask(np.array(img)))
    assert box is not None, f"{src}: no content found"
    y0, y1, x0, x1 = box
    scale = 288 / max(y1 - y0 + 1, x1 - x0 + 1)
    art = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    aa = np.array(art)
    alpha = np.where(key_mask(aa), 0, 255).astype(np.uint8)
    rgba = np.dstack([aa, alpha])
    out = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
    ccx = (x0 + x1) / 2 * scale
    ccy = (y0 + y1) / 2 * scale
    out.paste(Image.fromarray(rgba, "RGBA"), (round(160 - ccx), round(160 - ccy)), Image.fromarray(alpha, "L"))
    return out


def main() -> None:
    args = [a for a in sys.argv[1:] if a not in ("--apply", "--portrait")]
    apply = "--apply" in sys.argv[1:]
    if "--portrait" in sys.argv[1:]:
        out_dir = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" if apply \
            else ROOT / ".sprite_preview" / "revamp" / "portraits"
        out_dir.mkdir(parents=True, exist_ok=True)
        for pair in args:
            agent, path = pair.split("=", 1)
            dest = out_dir / f"hire_portrait_{agent}.png"
            process_portrait(Path(path)).save(dest, optimize=True)
            print(f"{dest.name}: ok")
        return
    agent = args[0]
    pairs = dict(a.split("=", 1) for a in args[1:])
    out_dir = ROOT / "media" / "sprites" / "chars" if apply else ROOT / ".sprite_preview" / "revamp"
    out_dir.mkdir(parents=True, exist_ok=True)
    for frame, path in pairs.items():
        img, stats = process(Path(path))
        dest = out_dir / f"{agent}_{frame}.png"
        img.save(dest, optimize=True)
        hole = stats.get("hole_frac", 0.0)
        flag = "  <-- HOLES" if hole > 0.01 else ""
        print(f"{dest.name}: scale={stats['scale']:.3f} "
              f"{stats.get('content_w')}x{stats.get('content_h')} hole={hole:.4f}{flag}")


if __name__ == "__main__":
    main()
