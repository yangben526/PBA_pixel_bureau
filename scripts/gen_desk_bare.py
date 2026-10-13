import numpy as np
import cv2
from PIL import Image

im = Image.open('app/src/main/assets/sprites/props/desk.png').convert('RGB')
arr = np.asarray(im).copy()
h, w = arr.shape[:2]
r0,g0,b0 = [arr[:,:,i].astype(np.int16) for i in range(3)]
magenta = (r0 > g0*1.6) & (b0 > g0*1.6) & (r0+b0-2*g0 > 55) & (np.maximum(r0,b0) > 60)
outline = (r0+g0+b0) < 45
mag_dil = cv2.dilate(magenta.astype(np.uint8)*255, np.ones((3,3),np.uint8), 1) > 0
outer_rim = outline & mag_dil

# Open wood reference on right tabletop
open_box = (380, 560, 520, 720)  # y0,x0,y1,x1
oy0,ox0,oy1,ox1 = open_box
open_wood = arr[oy0:oy1, ox0:ox1].copy()
# Filter only wood-looking pixels in open box
ow = open_wood.astype(np.int16)
ow_mask = ((ow[:,:,0] > ow[:,:,1]-5) & ((ow[:,:,0]-ow[:,:,2]) > 25) &
           (ow[:,:,0] > 100) & (ow[:,:,2] < 160))
print('open wood pixels', int(ow_mask.sum()))
palette = open_wood[ow_mask]
# Sort by brightness for plank variation
bright = palette.astype(np.int16).sum(axis=1)
order = np.argsort(bright)
palette = palette[order]
n = len(palette)
# Representative plank colors (dark line, mid, light)
c_dark = tuple(int(x) for x in palette[n//8].mean(axis=0)) if False else tuple(map(int, palette[max(0,n//10)]))
c_mid = tuple(map(int, palette[n//2]))
c_light = tuple(map(int, palette[min(n-1, 7*n//8)]))
print('plank colors', c_dark, c_mid, c_light, 'mean', palette.mean(axis=0))

# Item mask via seeds + grow (same as before, tighter band)
band = np.zeros((h,w), bool); band[235:505, 305:605] = True
mug = band & (r0 > 160) & (g0 < 75) & (b0 < 75)
steam = band & (r0 > 210) & (g0 > 210) & (b0 > 210) & (np.abs(r0-g0) < 20)
beige = band & (r0 >= 175) & (g0 >= 160) & (b0 >= 120) & (np.abs(r0-g0) < 35) & ((r0-b0) > 10) & ((r0-b0) < 70)
dark = band & (r0 < 45) & (g0 < 45) & (b0 < 45) & (~outline)
keys = band & (r0>=150)&(r0<=190)&(g0>=150)&(g0<=190)&(b0>=140)&(b0<=185)&(np.abs(r0-g0)<10)&(np.abs(g0-b0)<15)
liq = band & (r0>45)&(r0<95)&(g0>15)&(g0<50)&(b0<35)&(r0>g0)
seeds = mug|steam|beige|dark|keys|liq
mask = seeds.copy()
for i in range(30):
    dil = cv2.dilate(mask.astype(np.uint8)*255, np.ones((3,3),np.uint8), 1) > 0
    cand = dil & ~mask & band & ~magenta & ~outer_rim
    accept = cand & (
        ((r0 >= 160) & (g0 >= 145) & (b0 >= 100) & (np.abs(r0-g0) < 45) & ((r0-b0) < 85)) |
        ((r0 < 90) & (g0 < 90) & (b0 < 90)) |
        ((r0 > 140) & (g0 < 90) & (b0 < 90)) |
        ((r0 > 190) & (g0 > 190) & (b0 > 190)) |
        ((r0>=130)&(r0<=200)&(g0>=130)&(g0<=200)&(b0>=120)&(b0<=195)&(np.abs(r0-g0)<20)) |
        (outline & dil)
    )
    if not accept.any(): break
    mask |= accept
mask_u8 = cv2.dilate(mask.astype(np.uint8)*255, np.ones((3,3),np.uint8), 1)
inv = (mask_u8==0).astype(np.uint8)*255
ff = inv.copy(); cv2.floodFill(ff, None, (0,0), 128)
mask_u8[ff==255] = 255
mask = mask_u8 > 0
mask[magenta]=False; mask[outer_rim]=False; mask[535:]=False
print('mask', int(mask.sum()))

# Synthesize wood: base mid color + isometric plank lines matching existing grain direction
# Existing grain on desk top is roughly horizontal in screen space (rows of planks)
final = arr.copy()
ys, xs = np.where(mask)

# Sample continuous texture from open_wood with wrap — but remap brightness to match local
ph, pw = open_wood.shape[:2]
# Use open_wood as tile; only where ow_mask, else nearest in tile
# Build filled tile
tile = open_wood.copy()
# inpaint non-wood in tile quickly with mean
if (~ow_mask).any():
    tile[~ow_mask] = palette[n//2]

py = (ys - oy0) % ph
px = (xs - ox0) % pw
# Shift so we sample from a clean part; add hash jitter of 0-1 for pixel art grit
sampled = tile[py, px]

# Soften tile seams: blend with local mean wood
mean_col = palette.mean(axis=0)
# Add subtle plank lines: every ~18px in y, darker row (matches isometric plank spacing ~)
plank_period = 18
plank = ((ys // plank_period) % 2 == 0)
line = (ys % plank_period) < 2
synth = np.zeros((len(ys), 3), np.uint8)
synth[:] = mean_col
synth[plank] = np.clip(mean_col * 1.04, 0, 255)
synth[line] = np.clip(mean_col * 0.78, 0, 255)
# Mix 55% sampled open wood + 45% synth for continuity without ghosting
out_cols = (0.62 * sampled.astype(np.float32) + 0.38 * synth.astype(np.float32)).astype(np.uint8)
final[ys, xs] = out_cols

# Edge blend: for mask border, if original was wood keep it
dist_in = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 3)
edge = mask & (dist_in <= 1.5)
# keep original wood on edge
mid_wood = (~magenta)&(~outline)&(r0>g0)&((r0-b0)>25)&(g0>=40)&(g0<=170)&(r0>=70)&(r0<=210)&(b0<=110)
light_wood = (~magenta)&(~outline)&(r0>=175)&(g0>=135)&(b0>=75)&(b0<=185)&((r0-b0)>28)&((g0-b0)>12)
keep = edge & (mid_wood | light_wood)
final[keep] = arr[keep]

final[outer_rim]=arr[outer_rim]
final[magenta]=arr[magenta]
final[560:][outline[560:]]=arr[560:][outline[560:]]

# Kill any remaining item-colored pixels
rr,gg,bb=[final[:,:,i].astype(np.int16) for i in range(3)]
leftover = mask & (
    ((rr>165)&(gg>150)&(bb>110)&(np.abs(rr-gg)<40)&((rr-bb)<80)&((rr+gg)>340)) |
    ((rr<55)&(gg<55)&(bb<55)&((rr+gg+bb)>=45)) |
    ((rr>150)&(gg<80)&(bb<80)) |
    ((rr>200)&(gg>200)&(bb>200)&(np.abs(rr-gg)<25))
)
print('leftover', int(leftover.sum()))
if leftover.any():
    ly,lx=np.where(leftover)
    final[ly,lx] = mean_col.astype(np.uint8)

Image.fromarray(final).save('.sprite_preview/desk_bare_full.png')
Image.fromarray(final).resize((512,512), Image.NEAREST).save('.sprite_preview/desk_bare_preview.png')
vis=arr.copy(); vis[mask]=(255,40,40)
Image.fromarray(vis).resize((512,512), Image.NEAREST).save('.sprite_preview/desk_item_mask.png')
side=np.concatenate([
    np.asarray(Image.fromarray(final).resize((512,512), Image.NEAREST)),
    np.asarray(im.resize((512,512), Image.NEAREST)),
], axis=1)
Image.fromarray(side).save('.sprite_preview/desk_bare_vs_orig.png')
Image.fromarray(final[230:520,290:620]).resize((420,420), Image.NEAREST).save('.sprite_preview/crop_bare_full_top.png')
print('done', 'mean_col', mean_col)
