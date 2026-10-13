#!/usr/bin/env python3
"""Recolor existing agent sheets into new hires.

Same poses and pixel layout as the donor (idle/walk/sit/type/drink/sleep/work).
Hair, skin, and clothes shift; outlines, eyes, glasses, mugs, and the
sleep Z stay. Mouths are re-inked darker so they stay visible at game scale. Writes 1024x1024 magenta-backed PNGs next to the originals.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "media" / "sprites" / "chars"
FRAMES = [
    "idle", "sit", "sleep", "type0", "type1",
    "drink0", "drink1",
    "walk0", "walk1", "walk2", "walk3",
    "work0", "work1",
]

# region ids
BG, OUTLINE, KEEP = 0, 1, 2
SKIN, HAIR, TOP, MID, PANTS, EXTRA = 3, 4, 5, 6, 7, 8
RECOLOR = (SKIN, HAIR, TOP, MID, PANTS, EXTRA)


def key_mask(a: np.ndarray) -> np.ndarray:
    """Same backdrop test as SpriteStore.isSpriteKey. Those pixels must stay put."""
    r, g, b = [a[:, :, i].astype(np.int16) for i in range(3)]
    primary = (r > 130) & (b > 130) & (g < r) & (g < b) & ((r + b - 2 * g) > 50)
    fringe = (
        (r > g * 1.6) & (b > g * 1.6) & ((r + b - 2 * g) > 55)
        & (np.maximum(r, b) > 60) & (np.minimum(r, b) > 65) & (np.abs(r - b) <= 28)
    )
    return primary | fringe


def rgb_to_hsv(arr: np.ndarray):
    x = arr.astype(np.float32) / 255.0
    r, g, b = x[:, :, 0], x[:, :, 1], x[:, :, 2]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    df = mx - mn
    h = np.zeros_like(mx)
    mask = df > 1e-6
    rm = mask & (mx == r)
    gm = mask & (mx == g) & ~rm
    bm = mask & (mx == b) & ~rm
    h[rm] = ((g[rm] - b[rm]) / df[rm]) % 6
    h[gm] = ((b[gm] - r[gm]) / df[gm]) + 2
    h[bm] = ((r[bm] - g[bm]) / df[bm]) + 4
    h = (h / 6.0) % 1.0
    s = np.zeros_like(mx)
    nz = mx > 1e-6
    s[nz] = df[nz] / mx[nz]
    return h, s, mx


def hue_in(h, lo, hi):
    if lo <= hi:
        return (h >= lo) & (h <= hi)
    return (h >= lo) | (h <= hi)


def _mark_small(mask: np.ndarray, labels: np.ndarray, y0: int, bh: int, max_n: int, y_max_frac: float, code: int):
    if not mask.any():
        return
    comp, n = ndimage.label(mask)
    if n == 0:
        return
    counts = np.bincount(comp.ravel(), minlength=n + 1)
    ys, xs = np.nonzero(comp)
    # component id per pixel in the flat list
    ids = comp[ys, xs]
    # mean y per component
    sum_y = np.bincount(ids, weights=ys, minlength=n + 1)
    for i in range(1, n + 1):
        c = counts[i]
        if c == 0 or c > max_n:
            continue
        cy = sum_y[i] / c
        if (cy - y0) / bh <= y_max_frac:
            labels[comp == i] = code


def classify(arr: np.ndarray, donor: str, frame: str) -> np.ndarray:
    mag = key_mask(arr)
    h, s, v = rgb_to_hsv(arr)
    labels = np.full(arr.shape[:2], BG, np.uint8)
    content = ~mag
    ys, xs = np.nonzero(content)
    if len(ys) == 0:
        return labels
    y0, y1 = int(ys.min()), int(ys.max())
    x0, x1 = int(xs.min()), int(xs.max())
    bh = max(1, y1 - y0 + 1)
    bw = max(1, x1 - x0 + 1)
    rel_y = (np.arange(arr.shape[0])[:, None] - y0) / bh

    # Only the rim against the backdrop stays black. Interior blacks (hair, jacket)
    # are still paintable.
    near_bg = ndimage.distance_transform_edt(content) <= 8
    outline = content & near_bg & (v < 0.16) & (s < 0.45)
    labels[outline] = OUTLINE

    # Eyes, catchlights, drawstrings, shoe-whites are separated later.
    # Mouth / blush stay pink.
    pink = content & (s > 0.34) & (v > 0.32) & (v < 0.92) & (hue_in(h, 0.90, 0.02))
    _mark_small(pink & (labels == BG), labels, y0, bh, 8000, 0.62, KEEP)

    # Sleep "Z" — light blue, not a navy shirt.
    zed = content & hue_in(h, 0.52, 0.70) & (s > 0.28) & (v > 0.62)
    _mark_small(zed & (labels == BG), labels, y0, bh, 24000, 1.01, KEEP)

    if frame.startswith("drink"):
        # Coffee core, then the cream mug attached to it. Coat whites are not.
        coffee = content & hue_in(h, 0.02, 0.09) & (s > 0.45) & (v > 0.12) & (v < 0.42)
        _mark_small(coffee & (labels == BG), labels, y0, bh, 9000, 0.78, KEEP)
        cream = content & (s < 0.18) & (v > 0.72) & (v < 0.99) & (labels == BG)
        keep = labels == KEEP
        # Grow the mug a few pixels into adjacent cream / shade, not across the coat.
        grown = keep.copy()
        for _ in range(10):
            dil = ndimage.binary_dilation(grown, iterations=1)
            gain = dil & cream
            if not gain.any():
                break
            grown |= gain
        labels[grown & (labels == BG)] = KEEP

    skin = content & (labels == BG) & hue_in(h, 0.03, 0.095) & (s > 0.28) & (s < 0.58) & (v > 0.58)
    labels[skin] = SKIN
    # Pupils sit in the eye white, not in the bangs.
    eye_white = content & (s < 0.16) & (v > 0.82)
    near_eye = ndimage.binary_dilation(eye_white, iterations=2)
    labels[content & (labels == BG) & (v < 0.28) & near_eye] = KEEP

    # Glasses (Kai): dark neutral pixels on the face, not hair.
    if donor == "kai":
        glasses = (labels == BG) & (s < 0.32) & (v > 0.10) & (v < 0.42) & (rel_y > 0.10) & (rel_y < 0.40)
        labels[glasses] = KEEP

    rest = content & (labels == BG)
    y = rel_y

    def take(mask, code):
        hit = rest & mask
        labels[hit] = code
        rest[hit] = False

    if donor == "byte":
        # Pants before hair: khaki sits near blonde, but only below the shoulders.
        take(hue_in(h, 0.07, 0.18) & (s > 0.12) & (s < 0.55) & (v > 0.20) & (v < 0.78) & (y > 0.40), PANTS)
        take(hue_in(h, 0.09, 0.17) & (s > 0.35) & (v > 0.32), HAIR)
        take((s < 0.16) & (v > 0.62), TOP)  # coat
        take(hue_in(h, 0.42, 0.56) & (s > 0.22) & (v > 0.15), MID)  # inner shirt
    elif donor == "kai":
        take(hue_in(h, 0.03, 0.10) & (s > 0.55) & (v > 0.22), HAIR)
        take(hue_in(h, 0.84, 0.99) & (s > 0.22) & (v > 0.12), TOP)  # vest
        take(hue_in(h, 0.08, 0.18) & (s < 0.38) & (v > 0.62), MID)  # sleeves
        take((s < 0.14) & (v > 0.16) & (v < 0.72), PANTS)
    elif donor == "you":
        take(hue_in(h, 0.54, 0.74) & (s > 0.22) & (v < 0.72), TOP)  # polo
        take(hue_in(h, 0.02, 0.09) & (s > 0.42) & (v < 0.80), HAIR)
        take(hue_in(h, 0.07, 0.18) & (s > 0.22) & (v > 0.22) & (v < 0.72), PANTS)
        take((s < 0.16) & (v > 0.58), EXTRA)  # socks
    elif donor == "ops":
        take((hue_in(h, 0.94, 1.0) | hue_in(h, 0.0, 0.05)) & (s > 0.42) & (v > 0.12), TOP)
        take(hue_in(h, 0.12, 0.24) & (s > 0.22) & (v > 0.18), PANTS)
        take(hue_in(h, 0.03, 0.11) & (s > 0.32) & (v < 0.58), HAIR)
        take(hue_in(h, 0.04, 0.12) & (s > 0.28) & (v > 0.12) & (v < 0.48), EXTRA)
    elif donor == "cursor":
        take(hue_in(h, 0.34, 0.52) & (s > 0.12) & (v > 0.22), HAIR)
        take((s < 0.22) & (v < 0.34) & (y > 0.50), PANTS)
        take((s < 0.18) & (v < 0.62) & (y < 0.72), TOP)
        take((s < 0.12) & (v > 0.72), EXTRA)
    elif donor == "pixel":
        take(hue_in(h, 0.26, 0.44) & (s > 0.18), TOP)
        take(hue_in(h, 0.52, 0.78) & (s > 0.12) & (v < 0.55), PANTS)
        take((s < 0.28) & (v < 0.48) & (y < 0.50), HAIR)
        take((s < 0.14) & (v > 0.68), EXTRA)
    else:
        raise SystemExit(f"unknown donor {donor}")

    # Anti-aliased fringe: borrow the nearest recolored region, not the old hue.
    other = content & (labels == BG)
    if other.any():
        dists = []
        codes = []
        for code in RECOLOR:
            region = labels == code
            if not region.any():
                continue
            dists.append(ndimage.distance_transform_edt(~region))
            codes.append(code)
        if dists:
            stack = np.stack(dists, axis=0)
            nearest = stack.argmin(axis=0)
            mind = stack.min(axis=0)
            fill = other & (mind <= 10)
            code_arr = np.array(codes, np.uint8)
            labels[fill] = code_arr[nearest[fill]]
    return labels


def region_ref(labels: np.ndarray, arr: np.ndarray, code: int) -> float:
    m = labels == code
    if not m.any():
        return 0.75
    v = arr[m].max(axis=1).astype(np.float32) / 255.0
    return float(max(0.18, np.percentile(v, 78)))


def shade(pixels: np.ndarray, target: tuple[int, int, int], ref: float) -> np.ndarray:
    v = pixels.max(axis=1).astype(np.float32) / 255.0
    scale = np.clip(v / ref, 0.28, 1.62)
    out = np.clip(np.asarray(target, np.float32) * scale[:, None], 0, 255)
    return out.astype(np.uint8)


# donor -> which spec keys feed which region
DONOR_MAP = {
    "byte": {"skin": SKIN, "hair": HAIR, "coat": TOP, "shirt": MID, "pants": PANTS},
    "kai": {"skin": SKIN, "hair": HAIR, "vest": TOP, "sleeve": MID, "pants": PANTS},
    "you": {"skin": SKIN, "hair": HAIR, "shirt": TOP, "pants": PANTS, "socks": EXTRA},
    "ops": {"skin": SKIN, "hair": HAIR, "jacket": TOP, "pants": PANTS, "boots": EXTRA},
    "cursor": {"skin": SKIN, "hair": HAIR, "jacket": TOP, "pants": PANTS, "shoes": EXTRA},
    "pixel": {"skin": SKIN, "hair": HAIR, "hoodie": TOP, "pants": PANTS, "shoes": EXTRA},
}

CHARACTERS = [
    # Female — Byte's bun / lab-coat silhouette.
    dict(key="luna", label="Luna", donor="byte",
         skin=(255, 214, 198), hair=(48, 40, 52), coat=(214, 186, 238), shirt=(206, 86, 118), pants=(64, 62, 78),
         ref={"coat": 0.62}),
    dict(key="mina", label="Mina", donor="byte",
         skin=(214, 158, 114), hair=(150, 72, 36), coat=(214, 164, 48), shirt=(36, 128, 124), pants=(112, 78, 48)),
    dict(key="vera", label="Vera", donor="byte",
         skin=(244, 204, 176), hair=(214, 86, 150), coat=(36, 40, 64), shirt=(232, 196, 96), pants=(28, 28, 36)),
    dict(key="sora", label="Sora", donor="byte",
         skin=(255, 224, 208), hair=(186, 198, 214), coat=(120, 176, 220), shirt=(36, 64, 140), pants=(70, 86, 130)),
    dict(key="nola", label="Nola", donor="byte",
         skin=(148, 92, 62), hair=(72, 40, 28), coat=(214, 104, 86), shirt=(236, 224, 200), pants=(96, 108, 64)),
    # Male — one donor silhouette each.
    dict(key="reid", label="Reid", donor="pixel",
         skin=(255, 220, 196), hair=(220, 186, 110), hoodie=(36, 78, 176), pants=(32, 34, 44), shoes=(236, 238, 242),
         ref={"hair": 0.12}),
    dict(key="hugo", label="Hugo", donor="kai",
         skin=(206, 150, 108), hair=(36, 30, 32), vest=(42, 108, 72), sleeve=(230, 220, 196), pants=(88, 96, 112)),
    dict(key="nico", label="Nico", donor="cursor",
         skin=(255, 216, 200), hair=(220, 110, 156), jacket=(236, 238, 244), pants=(32, 32, 40), shoes=(250, 250, 252),
         ref={"jacket": 0.18}),
    dict(key="otto", label="Otto", donor="ops",
         skin=(196, 140, 102), hair=(196, 154, 96), jacket=(36, 58, 132), pants=(154, 136, 86), boots=(92, 60, 36)),
    dict(key="leif", label="Leif", donor="you",
         skin=(142, 88, 58), hair=(28, 24, 30), shirt=(138, 40, 56), pants=(116, 120, 128), socks=(232, 232, 236)),
]



def mouth_mask(arr: np.ndarray) -> np.ndarray:
    """Compact lip cluster under the eyes (not cheek blush)."""
    mag = key_mask(arr)
    h, s, v = rgb_to_hsv(arr)
    pink = (~mag) & (s > 0.34) & (v > 0.32) & (v < 0.92) & hue_in(h, 0.90, 0.02)
    ys, xs = np.nonzero(~mag)
    if len(ys) == 0:
        return np.zeros(arr.shape[:2], dtype=bool)
    y0, y1 = int(ys.min()), int(ys.max())
    x0, x1 = int(xs.min()), int(xs.max())
    bh = max(1, y1 - y0 + 1)
    bw = max(1, x1 - x0 + 1)
    yy = (np.arange(arr.shape[0])[:, None] - y0) / bh
    xx = (np.arange(arr.shape[1])[None, :] - (x0 + x1) / 2) / bw
    face = pink & (yy > 0.34) & (yy < 0.52) & (np.abs(xx) < 0.14)
    if not face.any():
        face = pink & (yy > 0.32) & (yy < 0.55) & (np.abs(xx) < 0.18)
    if not face.any():
        return face
    lab, n = ndimage.label(face)
    if n == 0:
        return face
    counts = np.bincount(lab.ravel())
    best = None
    best_score = -1.0
    for i in range(1, n + 1):
        m = lab == i
        c = int(counts[i])
        if c == 0 or c > 8000:
            continue
        cy = np.nonzero(m)[0].mean()
        cx = np.nonzero(m)[1].mean()
        # Prefer compact central smiles over large blush patches.
        if c > 2500:
            continue
        score = 1.0 / (1.0 + abs(cx - (x0 + x1) / 2) / bw * 10 + abs((cy - y0) / bh - 0.43) * 12 + c / 800.0)
        if score > best_score:
            best_score = score
            best = m
    return best if best is not None else face


def lip_color(skin_rgb) -> np.ndarray:
    """Dark rose that stays readable after SpriteStore inSampleSize=4."""
    skin = np.asarray(skin_rgb, dtype=np.float32)
    lum = 0.299 * skin[0] + 0.587 * skin[1] + 0.114 * skin[2]
    if lum < 110:
        return np.array([210, 92, 96], dtype=np.uint8)
    if lum < 170:
        return np.array([168, 58, 62], dtype=np.uint8)
    return np.array([158, 62, 66], dtype=np.uint8)


def boost_mouth(arr: np.ndarray, donor_arr: np.ndarray) -> np.ndarray:
    """Paint a stronger smile so hire faces keep a mouth at game scale."""
    out = arr.copy()
    core = mouth_mask(donor_arr)
    if not core.any():
        core = mouth_mask(arr)
    if not core.any():
        return out
    # One-pixel dilate keeps the smile curve; a second pass thickens for downsample.
    ring = ndimage.binary_dilation(core, iterations=1)
    wide = ndimage.binary_dilation(core, iterations=2)
    near = ndimage.binary_dilation(core, iterations=6) & ~core & ~key_mask(arr)
    skin = arr[near].mean(axis=0) if near.any() else arr[core].mean(axis=0)
    lip = lip_color(skin)
    shade = (lip.astype(np.float32) * 0.82).clip(0, 255).astype(np.uint8)
    out[wide & ~ring] = shade
    out[ring] = lip
    return out


def recolor(arr: np.ndarray, donor: str, frame: str, spec: dict, refs: dict[int, float], donor_arr: np.ndarray | None = None) -> np.ndarray:
    labels = classify(arr, donor, frame)
    out = arr.copy()
    mapping = DONOR_MAP[donor]
    refs = dict(refs)
    for name, value in spec.get("ref", {}).items():
        refs[mapping[name]] = value
    for name, code in mapping.items():
        m = labels == code
        if not m.any():
            continue
        out[m] = shade(arr[m], spec[name], refs[code])
    # KEEP mouths from the donor are too faint on recolored skin after downsample.
    out = boost_mouth(out, donor_arr if donor_arr is not None else arr)
    return out


def refs_for(donor: str) -> dict[int, float]:
    arr = np.array(Image.open(SRC / f"{donor}_idle.png").convert("RGB"))
    labels = classify(arr, donor, "idle")
    return {code: region_ref(labels, arr, code) for code in RECOLOR}


def main():
    preview = "--preview" in sys.argv
    out_dir = ROOT / ".sprite_preview" / "hires" if preview else SRC
    out_dir.mkdir(parents=True, exist_ok=True)
    frames = ["idle", "walk0", "sit", "type0", "drink1", "sleep", "work0"] if preview else FRAMES
    cache: dict[str, dict[int, float]] = {}
    for spec in CHARACTERS:
        donor = spec["donor"]
        if donor not in cache:
            cache[donor] = refs_for(donor)
            print(f"refs {donor}: " + ", ".join(f"{k}={v:.2f}" for k, v in cache[donor].items()))
        for frame in frames:
            src = SRC / f"{donor}_{frame}.png"
            arr = np.array(Image.open(src).convert("RGB"))
            out = recolor(arr, donor, frame, spec, cache[donor], donor_arr=arr)
            dest = out_dir / f"{spec['key']}_{frame}.png"
            Image.fromarray(out, "RGB").save(dest, optimize=True)
        print("wrote", spec["key"])
    if preview:
        # contact sheets
        def keyed(path):
            a = np.array(Image.open(path).convert("RGB"))
            m = key_mask(a)
            rgba = np.dstack([a, np.where(m, 0, 255).astype(np.uint8)])
            ys, xs = np.nonzero(~m)
            im = Image.fromarray(rgba, "RGBA").crop((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))
            im.thumbnail((100, 150), Image.Resampling.BOX)
            return im
        for spec in CHARACTERS:
            tiles = [keyed(out_dir / f"{spec['key']}_{fr}.png") for fr in frames]
            h = max(t.height for t in tiles)
            w = sum(t.width + 4 for t in tiles)
            sheet = Image.new("RGBA", (w, h), (32, 32, 40, 255))
            x = 0
            for t in tiles:
                sheet.paste(t, (x, 0), t)
                x += t.width + 4
            sheet.save(out_dir / f"sheet_{spec['key']}.png")
        print("preview sheets in", out_dir)


if __name__ == "__main__":
    main()
