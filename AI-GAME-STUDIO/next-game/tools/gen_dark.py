#!/usr/bin/env python3
"""Bien the "dark fantasy / gothic storybook": lam tram bang mau ve tong dat, them vet co tho,
va ve nhan vat tam dang nguoi that (ti le dau:than 1:7, ao choang, gay den long).

Chay sau gen_art.py:  python3 tools/gen_dark.py   ->  art/tex_dark, art/sprites_dark, art/hero/hero_dark.png
"""
import glob
import os
import random

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "art")
TINT = np.array([0.95, 0.86, 0.72])


def mute(rgb, rng, wrap, strokes):
    a = rgb.astype(float) / 255.0
    lum = (a * [0.299, 0.587, 0.114]).sum(-1, keepdims=True)
    a = (lum + (a - lum) * 0.5) * TINT * 0.84
    h, w = a.shape[:2]
    for _ in range(strokes):                               # vet co tho
        x, y, d = rng.randrange(w), rng.randrange(h), rng.choice([-0.07, -0.05, 0.05, 0.07])
        slope = rng.choice([0, 0, 1, -1])
        for i in range(rng.randint(3, 8)):
            xx, yy = x + i, y + (i // 3) * slope
            if wrap:
                xx, yy = xx % w, yy % h
            if 0 <= xx < w and 0 <= yy < h:
                a[yy, xx] += d
    return (np.clip(a, 0, 1) * 255).astype(np.uint8)


def variants():
    rng = random.Random(9)
    for sub, wrap in (("tex", True), ("sprites", False)):
        os.makedirs(os.path.join(ROOT, sub + "_dark"), exist_ok=True)
        for f in sorted(glob.glob(os.path.join(ROOT, sub, "*.png"))):
            im = np.array(Image.open(f).convert("RGBA"))
            n = im.shape[0] * im.shape[1]
            im[..., :3] = mute(im[..., :3], rng, wrap, n // (40 if wrap else 60))
            Image.fromarray(im, "RGBA" if not wrap else "RGBA").convert("RGBA" if not wrap else "RGB").save(
                os.path.join(ROOT, sub + "_dark", os.path.basename(f)))


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def hero():
    W, H = 60, 138
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    INK = hx("1a1416")
    CL, CM, CD = hx("6b5a60"), hx("4a3e46"), hx("2e262c")           # ao choang man tro
    TL, TM, TD = hx("a39068"), hx("857450"), hx("62553c")           # ao trong vang dat
    SK, SKD = hx("d9c2a6"), hx("a88f78")
    HAIR, HAIRL = hx("6e3528"), hx("94503a")
    GOLD = hx("c9a85a")
    # gay + den long (ve truoc de tay/ao de len)
    d.line([(49, 22), (51, 134)], fill=hx("4a3428"), width=2)
    d.line([(50, 22), (52, 134)], fill=hx("6e5240"), width=1)
    d.line([(49, 22), (54, 18)], fill=hx("4a3428"), width=1)
    d.rectangle([52, 19, 57, 28], fill=hx("2a2220"))
    d.rectangle([53, 21, 56, 26], fill=hx("f2c860"))
    d.rectangle([54, 22, 55, 24], fill=hx("fff0b0"))
    d.line([(52, 18), (57, 18)], fill=hx("4a3c34"))
    # chan + giay
    for x0 in (24, 31):
        d.rectangle([x0, 96, x0 + 4, 118], fill=hx("3a3230"))
        d.line([(x0, 96), (x0, 118)], fill=hx("4e4440"))
        d.polygon([(x0 - 1, 114), (x0 + 5, 114), (x0 + 6, 133), (x0 - 2, 133)], fill=hx("2a2220"))
        d.line([(x0 - 1, 116), (x0, 131)], fill=hx("4a3c34"))
        d.line([(x0 - 1, 121), (x0 + 5, 121)], fill=hx("5a4a3c"))
        d.line([(x0 - 1, 125), (x0 + 5, 125)], fill=hx("5a4a3c"))
    # ao choang
    cloak = [(22, 27), (38, 27), (46, 36), (49, 60), (51, 96), (47, 106), (42, 101), (37, 108), (30, 102), (23, 109),
             (18, 101), (12, 106), (9, 96), (11, 60), (14, 36)]
    d.polygon(cloak, fill=CM)
    d.polygon([(22, 27), (14, 36), (11, 60), (9, 96), (12, 106), (15, 103), (17, 60), (20, 38), (26, 29)], fill=CL)
    d.polygon([(38, 27), (46, 36), (49, 60), (51, 96), (47, 106), (44, 102), (43, 60), (40, 38), (35, 29)], fill=CD)
    for pts in ([(19, 40), (17, 70), (18, 101)], [(41, 40), (44, 72), (42, 100)], [(24, 72), (22, 96), (23, 108)],
                [(36, 72), (38, 96), (37, 107)]):
        d.line(pts, fill=CD if pts[0][0] < 30 else INK, width=1)
    # ao trong + that lung
    d.polygon([(27, 30), (33, 30), (36, 70), (35, 97), (25, 97), (24, 70)], fill=TM)
    d.polygon([(27, 30), (29, 30), (27, 70), (27, 97), (25, 97), (24, 70)], fill=TL)
    d.polygon([(33, 30), (36, 70), (35, 97), (33, 97), (33, 70), (32, 30)], fill=TD)
    d.line([(27, 30), (24, 70), (25, 97)], fill=INK)
    d.line([(33, 30), (36, 70), (35, 97)], fill=INK)
    d.rectangle([24, 62, 36, 65], fill=hx("3a2a22"))
    d.rectangle([29, 62, 31, 65], fill=GOLD)
    for x in range(26, 35, 2):
        d.point((x, 95), fill=GOLD)
    for y in range(34, 60, 4):
        d.point((30, y), fill=hx("3a2a22"))
    d.rectangle([29, 29, 31, 31], fill=GOLD)
    # tay cam gay
    d.rectangle([47, 66, 50, 70], fill=SK)
    d.line([(47, 70), (50, 70)], fill=SKD)
    # mu trum + mat
    d.polygon([(30, 1), (39, 4), (43, 13), (42, 23), (37, 29), (23, 29), (18, 23), (17, 13), (21, 4)], fill=CM)
    d.polygon([(30, 1), (21, 4), (17, 13), (18, 23), (23, 29), (25, 27), (21, 20), (21, 11), (25, 5)], fill=CL)
    d.polygon([(39, 4), (43, 13), (42, 23), (37, 29), (36, 26), (39, 20), (39, 11)], fill=CD)
    d.ellipse([24, 8, 36, 25], fill=SK)
    d.rectangle([25, 8, 35, 12], fill=SKD)
    d.polygon([(24, 9), (36, 9), (36, 13), (33, 11), (30, 14), (27, 11), (24, 14)], fill=HAIR)
    d.line([(24, 10), (23, 23)], fill=HAIR); d.line([(25, 10), (24, 20)], fill=HAIRL)
    d.line([(36, 10), (37, 22)], fill=HAIR)
    d.rectangle([27, 16, 28, 18], fill=INK); d.rectangle([32, 16, 33, 18], fill=INK)
    d.point((27, 16), fill=hx("b8452e")); d.point((32, 16), fill=hx("b8452e"))
    d.line([(29, 22), (31, 22)], fill=SKD)
    d.line([(34, 13), (35, 24)], fill=SKD)
    a = np.array(im)
    rng = random.Random(3)                                  # vet co
    for _ in range(140):
        x, y, dv = rng.randrange(W), rng.randrange(H), rng.choice([-12, -8, 8, 12])
        for i in range(rng.randint(2, 5)):
            yy = y + i
            if yy < H and a[yy, x, 3] and tuple(a[yy, x]) != INK and a[yy, x, 0] < 200:
                a[yy, x, :3] = np.clip(a[yy, x, :3].astype(int) + dv, 0, 255)
    m = a[..., 3] > 0                                      # net muc ngoai
    e = np.zeros_like(m)
    e[1:] |= m[:-1]; e[:-1] |= m[1:]; e[:, 1:] |= m[:, :-1]; e[:, :-1] |= m[:, 1:]
    a[e & ~m] = INK
    os.makedirs(os.path.join(ROOT, "hero"), exist_ok=True)
    Image.fromarray(a, "RGBA").save(os.path.join(ROOT, "hero", "hero_dark.png"))
    return a


if __name__ == "__main__":
    variants()
    h = hero()
    Image.fromarray(h, "RGBA").resize((h.shape[1] * 5, h.shape[0] * 5), Image.NEAREST).save(
        "/tmp/claude-0/-home-user-g1-acq/ee591e2c-577b-532e-b311-43b3a75e404d/scratchpad/hero_dark_x5.png")
