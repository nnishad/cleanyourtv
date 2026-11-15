#!/usr/bin/env python3
"""Turn any picture into a launcher scene: the clock's glass, its depth cut-out and the line to add to SceneView.

Usage:  tools/venv/bin/python tools/scene_add.py <picture> <id> [--top N] [--size N] [--depth] [--bucket 0-7]
          picture   a photo, 16:9 is best (anything else is cropped to it)
          id        the scene's name, becomes d_<id>
          --top     where the digits' top stands, in px of 1920x1080 (default: found from the skyline)
          --size    the clock's box height (default 420)
          --depth   cut the foreground out with a depth model (needs torch and transformers); default finds the skyline
          --rel     with --depth: 0 = everything nearer than the sky, 0.08 = only the nearer scenery (default 0.03)
          --start   without --depth: the row the skyline search starts at, below any clouds (default 150)
          --bucket  the three hours it belongs to: 0 = 0-3 ... 7 = 21-24, to print the BY_TIME hint

The launcher reads the picture itself for the clock's colours (SceneView.Tone), so nothing here is about colour: this
script writes launcher/app/src/main/assets/scenes/d_<id>.webp, -glass.jpg, -mask.png and thumbs/d_<id>.jpg, and prints
the SceneView line. Needs Pillow, numpy and scipy. Check the result with tools/scene_emu.sh d_<id> on the emulator.
"""
import argparse, os, sys
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "launcher", "app", "src", "main", "assets", "scenes")
BOX_DIGITS, BOX_BASELINE = 0.87, 0.9175   # the digits' share of the box, as SceneView draws them


def fit(im):
    w, h = im.size
    want = 16 / 9
    if w / h > want:
        nw = round(h * want); im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = round(w / want); im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    return im.resize((1920, 1080), Image.LANCZOS)


def refine(photo, hard):
    """Snap a hard mask's edge to the picture's own edges (a guided filter), so ridges and leaves cut out cleanly."""
    from scipy.ndimage import uniform_filter
    I = np.asarray(photo.convert("L"), np.float32) / 255
    f = lambda x: uniform_filter(x, 9)
    mI, mp = f(I), f(hard)
    a = (f(I * hard) - mI * mp) / (f(I * I) - mI * mI + 1e-3)
    out = f(a) * I + f(mp - a * mI)
    return np.clip((out - 0.5) * 1.6 + 0.5, 0, 1)


def skyline(photo, grad=8, start=150):
    """The first strong vertical change under the sky, column by column: the nearer scenery's top edge."""
    from scipy.ndimage import median_filter, minimum_filter1d, maximum_filter1d
    L = np.array(photo.convert("L").filter(ImageFilter.GaussianBlur(3)), np.float32)
    k = 6
    cs = np.cumsum(np.vstack([np.zeros((1, 1920)), L]), 0)
    y = np.arange(k, 1080 - k)
    diff = np.zeros_like(L)
    diff[k:1080 - k] = np.abs((cs[y + k] - cs[y]) / k - (cs[y] - cs[y - k]) / k)
    line = np.full(1920, 1079.0)
    for x in range(1920):
        hits = np.nonzero(diff[start:, x] > grad)[0]
        if hits.size:
            line[x] = start + hits[0]
    line = median_filter(line, size=7, mode="nearest")
    line = minimum_filter1d(maximum_filter1d(line, 41), 41)   # a lone cloud or pole sticking up is no skyline
    ys = np.arange(1080)[:, None]
    return refine(photo, (ys >= line[None, :]).astype(np.float32)), line


def depth_mask(photo, rel=0.03):
    import torch
    from transformers import pipeline
    pipe = pipeline("depth-estimation", model="depth-anything/Depth-Anything-V2-Large-hf",
                    device="mps" if torch.backends.mps.is_available() else "cpu")
    d = pipe(photo)["predicted_depth"]
    d = torch.nn.functional.interpolate(d[None] if d.dim() == 3 else d[None, None], size=(1080, 1920), mode="bicubic")[0, 0].numpy()
    d = (d - d.min()) / (d.max() - d.min())
    from scipy import ndimage as nd
    sky = float(np.median(d[40:220, 560:1360]))          # the sky's own depth; nearer than it is scenery
    fg = nd.binary_opening(d > sky + rel * (1 - sky), iterations=2)
    lab, _ = nd.label(fg)                                # only scenery that stands on the ground: no floating cloud
    fg = np.isin(lab, np.unique(lab[-1][lab[-1] > 0]))
    fg = nd.binary_fill_holes(nd.binary_closing(fg, iterations=3))
    a = refine(photo, fg.astype(np.float32))
    return a, np.array([np.argmax(a[:, x] > 0.5) if (a[:, x] > 0.5).any() else 1079 for x in range(1920)], float)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("picture"); ap.add_argument("id")
    ap.add_argument("--top", type=float); ap.add_argument("--size", type=float, default=420)
    ap.add_argument("--depth", action="store_true"); ap.add_argument("--bucket", type=int)
    ap.add_argument("--rel", type=float, default=0.03, help="with --depth: how much nearer than the sky counts as scenery")
    ap.add_argument("--start", type=int, default=150, help="without --depth: the row the skyline search starts at (below the clouds)")
    a = ap.parse_args()
    sid = a.id if a.id.startswith("d_") else "d_" + a.id
    photo = fit(Image.open(a.picture).convert("RGB"))
    mask, line = depth_mask(photo, a.rel) if a.depth else skyline(photo, start=a.start)
    mid = float(np.median(line[470:1450]))
    dh = BOX_DIGITS * a.size
    # the scenery's edge at 55% of the digits' height, and the digits above the rows (their foot no lower than y=585)
    top = a.top if a.top is not None else max(110.0, min(585 - BOX_BASELINE * a.size, mid - 0.55 * dh - (BOX_BASELINE - BOX_DIGITS) * a.size))
    photo.save(os.path.join(OUT, sid + ".webp"), lossless=True, method=6)
    g = ImageEnhance.Color(ImageEnhance.Brightness(photo.filter(ImageFilter.GaussianBlur(14))).enhance(1.25)).enhance(1.15)
    g.save(os.path.join(OUT, sid + "-glass.jpg"), quality=82)
    photo.resize((192, 108), Image.LANCZOS).save(os.path.join(OUT, "thumbs", sid + ".jpg"), quality=80)
    m = Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))
    Image.merge("LA", (Image.new("L", m.size, 0), m)).save(os.path.join(OUT, sid + "-mask.png"), optimize=True)
    print(f"wrote {sid}: scenery's top edge at y={mid:.0f} in the middle")
    print(f'SceneView.SPECS:   auto("{sid}", true, {round(top)}, {round(a.size)}),')
    if a.bucket is not None:
        print(f'SceneView.BY_TIME: add "{sid}" to row {a.bucket} ({a.bucket * 3} to {a.bucket * 3 + 3})')
    print("If the picture has no scenery to cross the digits (a busy street), use auto(..., false, ...) and delete the -mask.png.")


if __name__ == "__main__":
    sys.exit(main())
