#!/usr/bin/env python3
"""Sinh bo mau vat pixel art (props) cho next-game.

Chay:  python3 AI-GAME-STUDIO/next-game/tools/gen_props.py
Ket qua: AI-GAME-STUDIO/next-game/props/*.png + _tong_hop.png

Quy cach: PNG nen trong suot, nang tu tren-trai, vien toi 1px,
bang mau chung 32 mau, ti le theo nhan vat cao 64px, khong do bong san.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFont

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "props")

# ---------------------------------------------------------------- bang mau
HEX = [
    "2a1d2b",                                  # 1  vien
    "1f5c3a", "2f8f46", "5cc04a", "a8e05a",    # 2-5   la
    "5a3424", "8a5232", "b97a45", "dea868",    # 6-9   go
    "9a6a22", "cf9a2e", "f0c845", "fbe98a",    # 10-13 rom
    "4d5266", "7c8496", "adb5c0", "dfe5e8",    # 14-17 da
    "cdb38a", "f1e0b8",                        # 18-19 tuong
    "ffffff",                                  # 20
    "9c2a36", "dc4444", "ff8a70",              # 21-23 do
    "e0609a", "ffa8c8",                        # 24-25 hong
    "2c5aa8", "4c9be0", "a8e0f8",              # 26-28 xanh duong
    "8a5cc8",                                  # 29 tim
    "7a3c2c", "b85c38", "e08a50",              # 30-32 dat nung
]
RGBA = np.array([[0, 0, 0, 0]] + [[int(h[i:i + 2], 16) for i in (0, 2, 4)] + [255] for h in HEX], np.uint8)
O = 1
G = [2, 3, 4, 5]
W = [6, 7, 8, 9]
Y = [10, 11, 12, 13]
S = [14, 15, 16, 17]
C = [18, 19]
WH = 20
R = [21, 22, 23]
P = [24, 25]
B = [26, 27, 28]
V = 29
K = [30, 31, 32]

LIGHT = np.array([-0.55, -0.62, 0.56])
LIGHT /= np.linalg.norm(LIGHT)


# ---------------------------------------------------------------- co ban
def new(w, h):
    return np.zeros((h, w), np.uint8)


def _mask(a, fn):
    m = Image.new("L", (a.shape[1], a.shape[0]), 0)
    fn(ImageDraw.Draw(m))
    return np.array(m) > 0


def poly(a, pts, c):
    a[_mask(a, lambda d: d.polygon(pts, fill=1))] = c


def line(a, p, q, c, w=1):
    a[_mask(a, lambda d: d.line([p, q], fill=1, width=w))] = c


def ell(a, box, c):
    a[_mask(a, lambda d: d.ellipse(box, fill=1))] = c


def rect(a, x0, y0, x1, y1, c):
    a[max(y0, 0):y1 + 1, max(x0, 0):x1 + 1] = c


def px(a, x, y, c):
    if 0 <= y < a.shape[0] and 0 <= x < a.shape[1]:
        a[y, x] = c


def outline(a, c=O):
    m = a > 0
    d = np.zeros_like(m)
    d[1:] |= m[:-1]
    d[:-1] |= m[1:]
    d[:, 1:] |= m[:, :-1]
    d[:, :-1] |= m[:, 1:]
    a[d & ~m] = c
    return a


def over(b, l):
    m = l > 0
    b[m] = l[m]
    return b


def edges(a, base, light, dark, lw=2, dw=2):
    """To sang mep trai / toi mep phai cho moi dong cua vung mau `base`."""
    for y in range(a.shape[0]):
        xs = np.where(a[y] == base)[0]
        if len(xs) == 0:
            continue
        for r in np.split(xs, np.where(np.diff(xs) > 1)[0] + 1):
            l_, d_ = (lw, dw) if len(r) > lw + dw else (1, 1)
            if len(r) < 3:
                continue
            a[y, r[:l_]] = light
            a[y, r[-d_:]] = dark


def shift(a, mask, ramp, d):
    src = a.copy()
    for i, c in enumerate(ramp):
        a[mask & (src == c)] = ramp[min(max(i + d, 0), len(ramp) - 1)]


def blobs(a, bl, ramp, g=None, th=(-0.05, 0.36, 0.7), gw=0.5):
    """Ve cum khoi cau (cx, cy, rx, ry, z) co z-buffer, to bong 4 nac."""
    h, w = a.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    zb = np.full((h, w), -1e9)
    val = np.zeros((h, w))
    for cx, cy, rx, ry, z0 in bl:
        nx = (xx - cx) / rx
        ny = (yy - cy) / ry
        d = nx * nx + ny * ny
        nz = np.sqrt(np.clip(1 - d, 0, 1))
        z = z0 + nz * min(rx, ry)
        v = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        u = (d <= 1) & (z > zb)
        zb[u] = z[u]
        val[u] = v[u]
    m = zb > -1e8
    if g:
        gx, gy, grx, gry = g
        nx = (xx - gx) / grx
        ny = (yy - gy) / gry
        nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
        val = (1 - gw) * val + gw * (nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2])
    idx = (val > th[0]).astype(int) + (val > th[1]) + (val > th[2])
    a[m] = np.array(ramp, np.uint8)[idx][m]
    return m


def cluster(rng, cx, cy, rx, ry, n, rmin, rmax):
    bl = [(cx, cy, rx * 0.8, ry * 0.8, 0)]
    for _ in range(n):
        ang = rng.uniform(0, 2 * math.pi)
        d = math.sqrt(rng.random()) * 0.85
        r = rng.uniform(rmin, rmax)
        bl.append((cx + math.cos(ang) * d * rx, cy + math.sin(ang) * d * ry, r, r * 0.85,
                   (1 - d) * rx * 0.6 + rng.uniform(0, 4)))
    return bl


def thatch(a, mask, rng, band=14, y0=0):
    h, w = a.shape
    off = [rng.randint(0, 2) for _ in range(w)]
    ys, xs = np.where(mask)
    for y, x in zip(ys, xs):
        k = (y - y0 + off[x]) % band
        a[y, x] = Y[2] if k < 4 else Y[0] if k >= band - 2 else Y[1]
    for _ in range(len(ys) // 14):
        i = rng.randrange(len(ys))
        c = rng.choice([Y[2], Y[2], Y[0], Y[3]])
        for j in range(rng.randint(2, 5)):
            y, x = ys[i] + j, xs[i]
            if y < h and mask[y, x]:
                a[y, x] = c


def planks_v(a, x0, y0, x1, y1, pw, rng):
    x = x0
    while x <= x1:
        c = rng.choice([W[1], W[2], W[2]])
        xe = min(x + pw - 2, x1)
        rect(a, x, y0, xe, y1, c)
        rect(a, x, y0, x, y1, W[3] if c == W[2] else W[2])
        if xe + 1 <= x1:
            rect(a, xe + 1, y0, xe + 1, y1, W[0])
        for _ in range(2):
            yy = rng.randint(y0 + 2, y1 - 4)
            rect(a, x + rng.randint(2, max(2, pw - 4)), yy, x + rng.randint(2, max(2, pw - 4)), yy + 2, W[1] if c == W[2] else W[0])
        x += pw


def planks_h(a, x0, y0, x1, y1, ph, rng):
    y = y0
    while y <= y1:
        c = rng.choice([W[1], W[2], W[2]])
        ye = min(y + ph - 2, y1)
        rect(a, x0, y, x1, ye, c)
        rect(a, x0, y, x1, y, W[3] if c == W[2] else W[2])
        if ye + 1 <= y1:
            rect(a, x0, ye + 1, x1, ye + 1, W[0])
        for _ in range(3):
            xx = rng.randint(x0 + 2, x1 - 6)
            rect(a, xx, y + 3, xx + rng.randint(2, 5), y + 3, W[1] if c == W[2] else W[0])
        y += ph


def post(a, x0, y0, x1, y1, cap=True):
    rect(a, x0, y0, x1, y1, W[1])
    rect(a, x0, y0, x0 + 1, y1, W[2])
    rect(a, x1, y0, x1, y1, W[0])
    if cap:
        rect(a, x0, y0, x1, y0 + 1, W[3])
        px(a, x1, y0 + 1, W[2])


def beam_h(a, x0, y0, x1, y1):
    rect(a, x0, y0, x1, y1, W[1])
    rect(a, x0, y0, x1, y0, W[3])
    rect(a, x0, y1, x1, y1, W[0])


def bricks(a, x0, y0, x1, y1, bw=10, bh=5):
    """Ve mach da tren vung da to mau."""
    for r, y in enumerate(range(y0, y1 + 1, bh)):
        for x in range(x0, x1 + 1):
            if a[y, x] in S:
                a[y, x] = S[0]
        for x in range(x0 + (bw // 2 if r % 2 else 0), x1 + 1, bw):
            for yy in range(y, min(y + bh, y1 + 1)):
                if a[yy, x] in S:
                    a[yy, x] = S[0]


# ---------------------------------------------------------------- cay
def tree(kind):
    rng = random.Random(100 + kind)
    t = new(96, 128)
    poly(t, [(42, 70), (54, 70), (55, 110), (63, 124), (33, 124), (41, 110)], W[1])
    edges(t, W[1], W[2], W[0], 3, 4)
    for _ in range(14):
        x, y = rng.randint(43, 52), rng.randint(74, 118)
        if t[y, x] == W[1]:
            rect(t, x, y, x, y + rng.randint(1, 3), W[0])
    if kind == 3:  # chac nhanh
        for (p, q) in (((46, 84), (30, 62)), ((50, 84), (68, 60))):
            line(t, p, q, W[1], 5)
            line(t, (p[0] - 1, p[1]), (q[0] - 1, q[1]), W[2], 1)
    outline(t)
    c = new(96, 128)
    if kind == 1:
        bl = cluster(rng, 48, 46, 35, 34, 40, 8, 13)
        g = (48, 46, 46, 44)
    elif kind == 2:
        bl = cluster(rng, 48, 34, 24, 24, 22, 7, 11) + cluster(rng, 48, 62, 31, 22, 26, 7, 12)
        g = (48, 50, 42, 50)
    else:
        bl = (cluster(rng, 27, 54, 19, 17, 14, 6, 10) + cluster(rng, 69, 52, 19, 17, 14, 6, 10)
              + cluster(rng, 48, 34, 26, 22, 22, 7, 11))
        g = (48, 46, 50, 42)
    m = blobs(c, bl, G, g)
    if kind == 3:  # qua do
        ys, xs = np.where(m)
        for _ in range(11):
            i = rng.randrange(len(ys))
            x, y = xs[i], ys[i]
            if 3 < x < 92 and 3 < y < 80 and m[y - 2:y + 4, x - 2:x + 4].all():
                rect(c, x, y, x + 2, y + 2, R[1])
                px(c, x, y, R[2])
                px(c, x + 2, y + 2, R[0])
                px(c, x + 1, y - 1, W[0])
    outline(c)
    return over(t, c)


def bush(kind, flowers=0):
    rng = random.Random(200 + kind + 10 * flowers)
    a = new(32, 24)
    if kind == 1:
        bl = cluster(rng, 16, 12, 10.5, 7.5, 16, 2.8, 4.5)
        g = (16, 12, 15, 12)
    else:
        bl = cluster(rng, 10, 14, 7, 6, 9, 2.6, 4) + cluster(rng, 21, 11.5, 8, 7.5, 11, 2.8, 4.2)
        g = (16, 12, 16, 12)
    m = blobs(a, bl, G, g, th=(0.0, 0.4, 0.74), gw=0.35)
    a[23:] = 0
    m[23:] = False
    ys, xs = np.where(m)
    placed = []
    tries = 0
    while flowers and len(placed) < 7 and tries < 400:
        tries += 1
        i = rng.randrange(len(ys))
        x, y = int(xs[i]), int(ys[i])
        if not (2 <= x <= 29 and 2 <= y <= 20 and m[y - 1:y + 2, x - 1:x + 2].all()):
            continue
        if any(abs(x - u) + abs(y - v) < 6 for u, v in placed):
            continue
        placed.append((x, y))
        if flowers == 1:   # hoa hong nhat, nhuy vang
            for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
                px(a, x + dx, y + dy, P[1] if dx + dy < 0 else P[0])
            px(a, x, y, Y[2])
        else:              # hoa do + trang
            col = (R[1], R[2]) if len(placed) % 3 else (WH, WH)
            rect(a, x, y, x + 1, y + 1, col[0])
            px(a, x, y, col[1])
            px(a, x + 1, y + 1, R[0] if col[0] == R[1] else S[3])
    return outline(a)


def rock(w, h, seed, moss=False):
    rng = random.Random(seed)
    a = new(w, h)
    bl = [(w / 2, h * 0.62, w / 2 - 2, h * 0.52, 0)]
    for _ in range(2 + w // 16):
        r = rng.uniform(w * 0.16, w * 0.27)
        bl.append((rng.uniform(r + 2, w - r - 2), rng.uniform(r + 2, h * 0.7), r, r * 0.8, rng.uniform(1, 5)))
    m = blobs(a, bl, S, th=(-0.12, 0.34, 0.74))
    a[h - 1:] = 0
    if w >= 32:  # vet nut
        x, y = int(w * 0.55), int(h * 0.35)
        for _ in range(w // 5):
            if 1 < y < h - 3 and a[y, x] in (S[1], S[2]):
                a[y, x] = S[0]
            x += rng.choice([0, 1, 1])
            y += rng.choice([0, 1, 1])
    if moss:
        ys, xs = np.where((a == S[3]) | (a == S[2]))
        for y, x in zip(ys, xs):
            if y < h * 0.45 and x < w * 0.6 and rng.random() < 0.92:
                a[y, x] = G[3] if a[y, x] == S[3] else G[2]
    return outline(a)


# ---------------------------------------------------------------- cong trinh
def house():
    rng = random.Random(7)
    a = new(160, 160)
    rect(a, 20, 96, 139, 147, C[1])
    rect(a, 20, 96, 139, 107, C[0])                       # bong duoi mai hien
    for _ in range(40):                                   # vet vua
        x, y = rng.randint(26, 132), rng.randint(110, 144)
        rect(a, x, y, x + rng.randint(1, 3), y, C[0])
    rect(a, 20, 146, 139, 154, S[1])                      # mong da
    rect(a, 20, 146, 139, 146, S[2])
    bricks(a, 20, 150, 139, 154, 14, 5)
    for x in range(20, 140, 14):
        rect(a, x, 147, x, 149, S[0])
    for x in (20, 135):                                   # cot go
        post(a, x, 96, x + 4, 146, cap=False)
    beam_h(a, 20, 108, 139, 110)
    rect(a, 66, 110, 93, 146, W[0])                       # cua
    planks_v(a, 68, 113, 91, 146, 6, rng)
    rect(a, 68, 122, 91, 123, W[0])
    rect(a, 68, 137, 91, 138, W[0])
    rect(a, 87, 129, 88, 131, Y[2])
    rect(a, 64, 147, 95, 150, S[2])                       # bac them
    rect(a, 64, 150, 95, 150, S[1])
    for x in (32, 108):                                   # cua so
        rect(a, x, 113, x + 19, 131, W[0])
        rect(a, x + 2, 115, x + 17, 129, B[1])
        rect(a, x + 2, 115, x + 17, 116, B[0])
        rect(a, x + 9, 115, x + 10, 129, W[1])
        rect(a, x + 2, 122, x + 17, 122, W[1])
        for i in range(4):
            px(a, x + 4 + i, 121 - i, B[2])
            px(a, x + 13 + i, 128 - i, B[2])
        rect(a, x - 1, 132, x + 20, 136, W[1])            # bon hoa
        rect(a, x - 1, 132, x + 20, 132, W[3])
        rect(a, x - 1, 136, x + 20, 136, W[0])
        for i in range(0, 20, 2):
            px(a, x + i, 131, G[2])
            px(a, x + i + 1, 130, G[3])
            if i % 4 == 0:
                px(a, x + i + 1, 129, P[1] if x == 32 else R[2])
                px(a, x + i + 1, 130, P[0] if x == 32 else R[1])
    outline(a)

    ch = new(160, 160)                                    # ong khoi
    rect(ch, 110, 4, 125, 34, S[1])
    rect(ch, 110, 4, 112, 34, S[2])
    rect(ch, 123, 4, 125, 34, S[0])
    bricks(ch, 110, 9, 125, 34, 8, 5)
    rect(ch, 108, 3, 127, 6, S[2])
    rect(ch, 108, 3, 127, 3, S[3])
    rect(ch, 112, 4, 123, 5, S[0])
    over(a, outline(ch))

    r = new(160, 160)
    poly(r, [(44, 14), (116, 14), (156, 98), (156, 104), (4, 104), (4, 98)], Y[1])
    for x in range(4, 157):                               # tua rom mep mai
        for j in range(rng.randint(0, 3)):
            px(r, x, 105 + j, Y[1])
    m = r > 0
    thatch(r, m, rng, 15, 14)
    xx = np.arange(160)[None, :].repeat(160, 0)
    for y in range(160):
        xs = np.where(m[y])[0]
        if len(xs):
            shift(r, m & (np.arange(160)[:, None] == y) & (xx < xs[0] + 16), Y, 1)
            shift(r, m & (np.arange(160)[:, None] == y) & (xx > xs[-1] - 22), Y, -1)
    rows = np.arange(160)[:, None].repeat(160, 1)
    r[m & (rows >= 103)] = Y[0]
    rect(r, 38, 9, 122, 19, Y[1])                         # bo noc
    rect(r, 38, 9, 122, 11, Y[3])
    rect(r, 38, 12, 60, 13, Y[2])
    rect(r, 38, 18, 122, 19, Y[0])
    for x in range(44, 120, 12):
        rect(r, x, 9, x + 1, 19, W[1])
        px(r, x, 9, W[3])
    over(a, outline(r))
    return a


def windmill():
    rng = random.Random(11)
    a = new(128, 192)
    poly(a, [(45, 80), (83, 80), (97, 186), (31, 186)], C[1])
    edges(a, C[1], WH, C[0], 4, 11)
    for _ in range(50):
        x, y = rng.randint(40, 80), rng.randint(90, 164)
        if a[y, x] == C[1]:
            rect(a, x, y, x + rng.randint(1, 3), y, C[0])
    base = (a > 0) & (np.arange(192)[:, None] >= 168)     # chan da
    a[base] = S[1]
    edges(a, S[1], S[2], S[0], 4, 10)
    for x in range(128):
        if a[168, x]:
            a[168, x] = S[3]
    bricks(a, 30, 173, 98, 186, 12, 5)
    ell(a, (52, 142, 76, 166), W[0])                      # cua vom
    rect(a, 52, 154, 76, 186, W[0])
    ell(a, (54, 144, 74, 164), W[1])
    rect(a, 54, 154, 74, 186, W[1])
    for x in (54, 59, 64, 69):
        rect(a, x, 147 if x in (59, 64) else 152, x, 186, W[2])
        rect(a, x + 4, 150, x + 4, 186, W[0])
    rect(a, 54, 168, 74, 169, W[0])
    rect(a, 70, 164, 71, 166, Y[2])
    rect(a, 56, 104, 71, 122, W[0])                       # cua so
    rect(a, 58, 106, 69, 120, B[0])
    rect(a, 58, 110, 69, 120, B[1])
    rect(a, 63, 106, 64, 120, W[1])
    rect(a, 58, 113, 69, 113, W[1])
    px(a, 60, 108, B[2]); px(a, 61, 107, B[2])
    rect(a, 55, 123, 72, 124, W[2])
    beam_h(a, 43, 80, 85, 84)                             # dai go
    outline(a)

    cap = new(128, 192)                                   # mai chop
    poly(cap, [(64, 34), (90, 80), (88, 84), (40, 84), (38, 80)], Y[1])
    m = cap > 0
    thatch(cap, m, rng, 12, 34)
    xx = np.arange(128)[None, :].repeat(192, 0)
    yy = np.arange(192)[:, None].repeat(128, 1)
    shift(cap, m & (xx < 64 - (yy - 34) * 0.22), Y, 1)
    shift(cap, m & (xx > 64 + (yy - 34) * 0.2), Y, -1)
    cap[m & (yy >= 83)] = Y[0]
    over(a, outline(cap))

    b = new(128, 192)                                     # canh quat
    cx, cy = 64, 64
    for deg in (22, 112, 202, 292):
        t = math.radians(deg)
        dx, dy = math.cos(t), math.sin(t)
        qx, qy = -dy, dx
        pt = lambda s, o: (round(cx + dx * s + qx * o), round(cy + dy * s + qy * o))
        poly(b, [pt(16, 2), pt(58, 2), pt(58, 15), pt(16, 15)], C[1])
        poly(b, [pt(16, 2), pt(58, 2), pt(58, 6), pt(16, 6)], WH)
        for s in (16, 26.5, 37, 47.5, 58):
            line(b, pt(s, 2), pt(s, 15), W[2])
        for o in (8.5, 15):
            line(b, pt(16, o), pt(58, o), W[2])
        line(b, pt(0, 0), pt(60, 0), W[1], 3)
        line(b, pt(4, -1), pt(60, -1), W[3], 1)
    ell(b, (cx - 5, cy - 5, cx + 5, cy + 5), W[0])
    ell(b, (cx - 3, cy - 3, cx + 3, cy + 3), W[1])
    rect(b, cx - 2, cy - 2, cx - 1, cy - 1, W[3])
    over(a, outline(b))
    return a


def well():
    rng = random.Random(5)
    a = new(48, 64)
    rect(a, 6, 40, 41, 56, S[1])
    ell(a, (6, 50, 41, 62), S[1])
    edges(a, S[1], S[2], S[0], 5, 7)
    bricks(a, 6, 46, 41, 62, 9, 5)
    ell(a, (6, 32, 41, 48), S[2])                         # mieng gieng
    ell(a, (6, 32, 30, 44), S[3])
    ell(a, (8, 34, 39, 47), S[2])
    ell(a, (11, 35, 36, 45), B[0])
    ell(a, (12, 38, 35, 45), B[1])
    rect(a, 17, 41, 21, 41, B[2])
    rect(a, 25, 43, 27, 43, B[2])
    for x in (7, 37):                                     # cot
        post(a, x, 14, x + 3, 40, cap=False)
    rect(a, 11, 22, 36, 23, W[0])                         # truc quay
    rect(a, 11, 22, 36, 22, W[2])
    rect(a, 20, 21, 27, 24, Y[1])
    rect(a, 20, 21, 27, 21, Y[2])
    rect(a, 24, 25, 24, 30, Y[0])                         # day
    rect(a, 21, 31, 27, 37, W[1])                         # xo
    rect(a, 21, 31, 22, 37, W[2])
    rect(a, 27, 31, 27, 37, W[0])
    rect(a, 21, 33, 27, 33, S[2])
    outline(a)
    r = new(48, 64)
    poly(r, [(24, 2), (46, 15), (46, 19), (1, 19), (1, 15)], Y[1])
    m = r > 0
    thatch(r, m, rng, 7, 2)
    xx = np.arange(48)[None, :].repeat(64, 0)
    shift(r, m & (xx < 20), Y, 1)
    shift(r, m & (xx > 32), Y, -1)
    r[19][m[19]] = Y[0]
    over(a, outline(r))
    return a


# ---------------------------------------------------------------- do go
def fence_h():
    a = new(32, 32)
    beam_h(a, 0, 11, 31, 14)
    beam_h(a, 0, 20, 31, 23)
    for x in (12, 22):
        px(a, x, 12, W[0]); px(a, x + 1, 12, W[0]); px(a, x + 4, 21, W[0]); px(a, x + 5, 21, W[0])
    post(a, 2, 6, 7, 29)
    return outline(a)


def fence_v():
    a = new(16, 32)
    rect(a, 6, 0, 9, 31, W[1])
    rect(a, 6, 0, 6, 31, W[3])
    rect(a, 9, 0, 9, 31, W[0])
    for y in (5, 17, 26):
        px(a, 7, y, W[0]); px(a, 7, y + 1, W[0])
    post(a, 5, 6, 10, 29)
    return outline(a)


def fence_post():
    a = new(16, 32)
    post(a, 5, 6, 10, 29)
    rect(a, 4, 5, 11, 7, W[2])
    rect(a, 4, 5, 11, 5, W[3])
    px(a, 11, 7, W[0])
    return outline(a)


def signpost():
    a = new(32, 48)
    post(a, 14, 3, 17, 45)
    b = new(32, 48)
    poly(b, [(5, 8), (24, 8), (29, 13), (24, 18), (5, 18)], W[2])
    rect(b, 5, 8, 24, 8, W[3]); rect(b, 5, 18, 24, 18, W[1])
    for x0, x1, y in ((8, 14, 11), (16, 22, 11), (8, 19, 14)):
        rect(b, x0, y, x1, y, W[0])
    px(b, 15, 13, S[2]); px(b, 16, 13, S[0])
    over(a, outline(b))
    b = new(32, 48)
    poly(b, [(26, 23), (8, 23), (3, 27), (8, 31), (26, 31)], W[2])
    rect(b, 8, 23, 26, 23, W[3]); rect(b, 8, 31, 26, 31, W[1])
    for x0, x1, y in ((10, 17, 26), (19, 23, 26), (12, 22, 28)):
        rect(b, x0, y, x1, y, W[0])
    over(a, outline(b))
    for x, y in ((12, 45), (11, 44), (19, 45), (20, 44), (13, 43)):   # co o chan
        px(a, x, y, G[2])
    return outline(a)


def crate():
    a = new(32, 32)
    rect(a, 2, 3, 29, 11, W[3])                           # mat tren
    for x in (9, 16, 23):
        rect(a, x, 4, x, 10, W[2])
    rect(a, 2, 3, 29, 3, W[2]); rect(a, 2, 11, 29, 11, W[2])
    rect(a, 2, 3, 2, 11, W[2]); rect(a, 29, 3, 29, 11, W[2])
    rect(a, 2, 12, 29, 29, W[1])                          # mat truoc
    for y in (17, 23):
        rect(a, 4, y, 27, y, W[0])
    line(a, (5, 27), (26, 15), W[2], 3)
    line(a, (5, 28), (26, 16), W[0], 1)
    rect(a, 2, 12, 29, 14, W[2]); rect(a, 2, 27, 29, 29, W[2])
    rect(a, 2, 12, 4, 29, W[2]); rect(a, 27, 12, 29, 29, W[2])
    rect(a, 2, 12, 29, 12, W[0]); rect(a, 5, 15, 26, 15, W[0])
    rect(a, 2, 29, 29, 29, W[0]); rect(a, 29, 13, 29, 29, W[0])
    rect(a, 5, 15, 5, 26, W[0])
    for x, y in ((3, 13), (28, 13), (3, 28), (28, 28)):
        px(a, x, y, S[2])
    return outline(a)


def jar():
    a = new(24, 32)
    rect(a, 8, 27, 15, 29, K[0])
    blobs(a, [(11.5, 18.5, 9.5, 10.5, 0)], [K[0], K[1], K[2], W[3]], th=(-0.1, 0.4, 0.8))
    for x in range(3, 21):                                 # hoa van
        if a[15, x] and x % 3 != 0:
            a[15, x] = K[0]
    rect(a, 8, 6, 15, 9, K[1])
    rect(a, 8, 6, 9, 9, K[2]); rect(a, 15, 6, 15, 9, K[0])
    ell(a, (5, 2, 18, 8), K[2])
    ell(a, (5, 2, 14, 6), W[3])
    ell(a, (6, 3, 17, 7), K[1])
    ell(a, (7, 3, 16, 6), K[0])
    rect(a, 9, 5, 14, 5, B[0])                             # nuoc trong chum
    return outline(a)


def bridge():
    rng = random.Random(21)
    a = new(96, 48)
    for x in (4, 46, 88):                                  # lan can xa
        post(a, x, 2, x + 3, 13)
    beam_h(a, 0, 5, 95, 7)
    outline(a)
    d = new(96, 48)
    planks_v(d, 0, 12, 95, 39, 8, rng)
    rect(d, 0, 12, 95, 12, W[3])
    rect(d, 0, 40, 95, 43, W[1])                           # canh cau
    rect(d, 0, 40, 95, 40, W[0]); rect(d, 0, 43, 95, 43, W[0])
    over(a, outline(d))
    n = new(96, 48)
    beam_h(n, 0, 28, 95, 30)
    for x in (4, 46, 88):                                  # lan can gan
        post(n, x, 24, x + 3, 45)
    over(a, outline(n))
    return a


def pier():
    rng = random.Random(22)
    a = new(64, 96)
    for x in (9, 49):                                      # coc duoi nuoc
        rect(a, x, 80, x + 5, 93, W[0])
        rect(a, x, 80, x + 1, 90, W[1])
    rect(a, 28, 80, 33, 91, W[0])
    outline(a)
    d = new(64, 96)
    planks_h(d, 8, 0, 55, 79, 8, rng)
    rect(d, 8, 0, 8, 79, W[3]); rect(d, 55, 0, 55, 79, W[0])
    rect(d, 8, 80, 55, 83, W[1])
    rect(d, 8, 80, 55, 80, W[0]); rect(d, 8, 83, 55, 83, W[0])
    over(a, outline(d))
    p = new(64, 96)
    for y in (4, 38, 70):                                  # coc neo hai ben
        for x in (4, 54):
            post(p, x, y, x + 5, y + 13)
            ell(p, (x, y - 2, x + 5, y + 2), W[3])
            px(p, x + 4, y + 1, W[2]); px(p, x + 3, y + 1, W[2])
    for y in (10, 11):                                     # day thung
        rect(p, 54, y + 66, 59, y + 66, Y[2 if y == 10 else 0])
    over(a, outline(p))
    return a


# ---------------------------------------------------------------- cay co nho
def rice(kind):
    a = new(16, 16)
    tips = [(3, 5), (6, 2), (10, 3), (13, 6)] if kind == 1 else [(2, 7), (5, 3), (8, 1), (11, 4), (13, 8)]
    for i, (x, y) in enumerate(tips):
        line(a, (7 + i % 2, 14), (x, y), G[2] if i % 2 else G[1])
    for i, (x, y) in enumerate(tips):
        s = -1 if x < 8 else 1
        for j, (dx, dy) in enumerate(((0, 0), (s, 1), (s, 2), (2 * s, 3))):
            px(a, x + dx, y + dy, Y[2] if j % 2 == 0 else Y[1])
        px(a, x, y - 1 if y > 1 else y, Y[3])
    px(a, 6, 14, G[1]); px(a, 9, 14, G[1])
    return outline(a)


def grass(kind):
    a = new(16, 16)
    if kind == 1:
        blades = [((4, 14), (2, 6)), ((6, 14), (5, 2)), ((8, 14), (8, 4)), ((10, 14), (12, 3)), ((11, 14), (14, 8))]
    else:
        blades = [((5, 14), (2, 8)), ((6, 14), (4, 3)), ((8, 14), (7, 1)), ((9, 14), (10, 5)), ((11, 14), (13, 4))]
    for i, (p, q) in enumerate(blades):
        line(a, p, q, G[1 + i % 2])
        line(a, (p[0] + 1, p[1]), ((p[0] + q[0]) // 2 + 1, (p[1] + q[1]) // 2), G[1 + i % 2])
        px(a, q[0], q[1], G[3])
    if kind == 2:                                          # bong co
        for x, y in ((4, 3), (7, 1), (13, 4)):
            px(a, x, y, Y[2]); px(a, x, y + 1, Y[1]); px(a, x, y + 2, Y[2])
    rect(a, 5, 14, 10, 14, G[0])
    return outline(a)


def mushroom(kind):
    if kind == 1:
        a = new(12, 12)
        rect(a, 4, 6, 7, 10, WH)
        rect(a, 7, 6, 7, 10, C[0]); rect(a, 4, 6, 7, 6, C[0])
        ell(a, (1, 1, 10, 7), R[1])
        rect(a, 1, 6, 10, 7, 0)
        rect(a, 2, 6, 9, 6, R[0])
        rect(a, 4, 6, 7, 6, C[0])
        for x, y in ((3, 2), (4, 2), (2, 3)):
            px(a, x, y, R[2])
        for x, y in ((5, 3), (8, 2), (3, 4), (8, 5)):
            px(a, x, y, WH)
        px(a, 9, 4, R[0]); px(a, 9, 5, R[0])
        return outline(a)
    a = new(16, 16)
    for (cx, by, cw, sh) in ((5, 14, 7, 6), (11, 14, 5, 4)):
        rect(a, cx - 1, by - sh, cx + 1, by, C[1])
        rect(a, cx + 1, by - sh, cx + 1, by, C[0])
        t = by - sh - 4
        ell(a, (cx - cw // 2 - 1, t, cx + cw // 2 + 1, t + 6), W[2])
        rect(a, 0 if cx == 5 else 8, t + 4, 9 if cx == 5 else 15, t + 6, 0)
        rect(a, cx - 1, t + 4, cx + 1, by - sh, C[1]); px(a, cx + 1, t + 4, C[0])
        rect(a, cx - cw // 2, t + 3, cx + cw // 2, t + 3, W[1])
        px(a, cx - 1, t + 1, W[3]); px(a, cx - 2, t + 1, W[3]); px(a, cx - 2, t + 2, W[3])
    return outline(a)


def flower(kind):
    if kind == 1:                                          # cuc trang 8x8
        a = new(8, 8)
        for x, y in ((3, 1), (1, 3), (5, 3), (3, 5)):
            rect(a, x, y, x + 1, y + 1, WH)
        rect(a, 3, 3, 4, 4, Y[2]); px(a, 4, 4, Y[1])
        px(a, 6, 4, S[3]); px(a, 4, 6, S[3]); px(a, 3, 6, S[3])
        px(a, 2, 2, G[2]); px(a, 5, 5, G[1])
        return outline(a)
    if kind == 2:                                          # tulip do 8x12
        a = new(8, 12)
        rect(a, 4, 6, 4, 10, G[1])
        px(a, 3, 8, G[2]); px(a, 2, 7, G[3]); px(a, 5, 9, G[2]); px(a, 6, 8, G[1])
        rect(a, 2, 2, 5, 5, R[1])
        rect(a, 2, 1, 2, 4, R[2]); px(a, 3, 2, R[2])
        px(a, 4, 1, R[1]); px(a, 5, 1, R[0]); rect(a, 5, 2, 5, 5, R[0])
        px(a, 3, 1, 0)
        px(a, 2, 5, 0); px(a, 5, 5, 0); rect(a, 3, 5, 4, 5, R[0])
        return outline(a)
    if kind == 3:                                          # chuong xanh 12x12
        a = new(12, 12)
        line(a, (6, 10), (6, 4), G[1]); line(a, (6, 6), (3, 3), G[1]); line(a, (6, 5), (9, 3), G[1])
        px(a, 5, 9, G[2]); px(a, 4, 8, G[3]); px(a, 7, 9, G[2]); px(a, 8, 8, G[1])
        for x, y in ((2, 3), (8, 2), (5, 1)):
            rect(a, x, y, x + 2, y + 2, B[1])
            px(a, x, y, B[2]); px(a, x + 2, y + 2, B[0]); px(a, x + 1, y + 2, B[0])
            px(a, x + 1, y + 1, WH)
        return outline(a)
    a = new(16, 16)                                        # hoa hong tim lon 16x16
    line(a, (8, 14), (8, 8), G[1]); rect(a, 7, 9, 7, 14, G[2])
    for x, y, c in ((5, 12, G[3]), (4, 11, G[3]), (5, 11, G[2]), (3, 10, G[2]),
                    (10, 12, G[1]), (11, 11, G[1]), (10, 11, G[2]), (12, 10, G[1])):
        px(a, x, y, c)
    for x, y, c in ((6, 1, P[1]), (2, 3, P[1]), (10, 3, P[0]), (3, 7, P[0]), (9, 7, V)):
        rect(a, x, y, x + 2, y + 2, c)
    for x, y in ((6, 1), (2, 3)):
        px(a, x, y, WH)
    px(a, 11, 5, V); px(a, 12, 5, V); px(a, 5, 9, V); px(a, 12, 4, V)
    rect(a, 5, 4, 9, 7, P[0])
    rect(a, 6, 4, 8, 6, Y[2]); px(a, 6, 4, Y[3]); px(a, 8, 6, Y[1]); px(a, 7, 6, Y[1])
    return outline(a)


# ---------------------------------------------------------------- xuat
GROUPS = [
    ("Cây lá rộng", [("cay_la_rong_1", lambda: tree(1)), ("cay_la_rong_2", lambda: tree(2)),
                     ("cay_la_rong_3", lambda: tree(3))]),
    ("Công trình", [("nha_mai_rom", house), ("coi_xay_gio", windmill), ("gieng_nuoc", well)]),
    ("Bụi cây / bụi hoa", [("bui_cay_1", lambda: bush(1)), ("bui_cay_2", lambda: bush(2)),
                           ("bui_hoa_1", lambda: bush(1, 1)), ("bui_hoa_2", lambda: bush(2, 2))]),
    ("Đá", [("da_nho", lambda: rock(16, 14, 31)), ("da_vua", lambda: rock(32, 26, 35)),
            ("da_lon", lambda: rock(48, 40, 33, True))]),
    ("Hàng rào", [("hang_rao_ngang", fence_h), ("hang_rao_doc", fence_v), ("hang_rao_coc_goc", fence_post)]),
    ("Đồ vật", [("bien_chi_duong", signpost), ("thung_go", crate), ("chum", jar)]),
    ("Cây cỏ nhỏ", [("cum_lua_1", lambda: rice(1)), ("cum_lua_2", lambda: rice(2)),
                    ("cum_co_cao_1", lambda: grass(1)), ("cum_co_cao_2", lambda: grass(2)),
                    ("nam_1", lambda: mushroom(1)), ("nam_2", lambda: mushroom(2)),
                    ("hoa_1", lambda: flower(1)), ("hoa_2", lambda: flower(2)),
                    ("hoa_3", lambda: flower(3)), ("hoa_4", lambda: flower(4))]),
    ("Cầu", [("cau_go_ngan", bridge), ("cau_tau", pier)]),
]


def to_img(a):
    return Image.fromarray(RGBA[a], "RGBA")


def actor_ref():
    """Bong nhan vat 64px de so ti le (chi dung trong anh tong hop)."""
    a = new(32, 64)
    ell(a, (9, 2, 22, 16), S[2])
    rect(a, 9, 17, 22, 42, S[1])
    rect(a, 5, 19, 8, 38, S[1]); rect(a, 23, 19, 26, 38, S[1])
    rect(a, 10, 43, 15, 62, S[0]); rect(a, 17, 43, 21, 62, S[0])
    return to_img(outline(a))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    used = set()
    sprites = []
    for title, items in GROUPS:
        row = []
        for name, fn in items:
            a = fn()
            used |= set(np.unique(a).tolist())
            im = to_img(a)
            im.save(os.path.join(OUT_DIR, name + ".png"), optimize=True)
            row.append((name, im))
        sprites.append((title, row))
    print("so mau da dung:", len(used - {0}), "| so file:", sum(len(r) for _, r in sprites))

    # ---- anh tong hop
    Z, PAD, LAB = 2, 14, 30
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
        fbig = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 16)
    except OSError:
        font = fbig = ImageFont.load_default()
    ref = actor_ref()
    lines = [[("Công trình", [("(nhân vật 64px)", ref)] + sprites[1][1])],
             [("Cây lá rộng", sprites[0][1]), sprites[7], sprites[4]],
             [sprites[2], sprites[3], sprites[5]],
             [sprites[6]]]
    tmp = ImageDraw.Draw(Image.new("RGB", (1, 1)))

    def cell_w(name, im):
        return max(im.width * Z, int(tmp.textlength(name, font=font))) + PAD

    def group_w(g):
        return sum(cell_w(n, i) for n, i in g[1]) + PAD * 2

    widths = [sum(group_w(g) for g in ln) for ln in lines]
    heights = [max(i.height for g in ln for _, i in g[1]) * Z + LAB + 34 for ln in lines]
    Wd, Hd = max(widths) + PAD, sum(heights) + 50
    sheet = Image.new("RGBA", (Wd, Hd), (122, 190, 92, 255))
    d = ImageDraw.Draw(sheet)
    for yy in range(0, Hd, 16):                            # nen co ca-ro nhat
        for xx in range(0, Wd, 16):
            if (xx // 16 + yy // 16) % 2:
                d.rectangle([xx, yy, xx + 15, yy + 15], fill=(114, 182, 86, 255))
    d.rectangle([0, 0, Wd, 36], fill=(42, 29, 43, 255))
    d.text((PAD, 9), "next-game / props — bộ mẫu vật pixel art (phóng x2, nắng trên-trái, bảng 32 màu)", font=fbig, fill="white")
    y = 44
    for ln, hh in zip(lines, heights):
        x = PAD
        for title, items in ln:
            d.text((x, y), title, font=fbig, fill=(42, 29, 43, 255))
            base = y + hh - LAB - 6
            for name, im in items:
                cw = cell_w(name, im)
                big = im.resize((im.width * Z, im.height * Z), Image.NEAREST)
                sheet.alpha_composite(big, (x + (cw - PAD - big.width) // 2, base - big.height))
                d.text((x, base + 3), name, font=font, fill=(30, 40, 30, 255))
                if not name.startswith("("):
                    d.text((x, base + 16), "%dx%d" % im.size, font=font, fill=(60, 90, 50, 255))
                x += cw
            x += PAD * 2
        y += hh
    sheet.convert("RGB").save(os.path.join(OUT_DIR, "_tong_hop.png"), optimize=True)


if __name__ == "__main__":
    main()
