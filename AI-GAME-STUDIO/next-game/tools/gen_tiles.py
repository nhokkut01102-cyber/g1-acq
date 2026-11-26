#!/usr/bin/env python3
"""Sinh bo nen (co, duong dat, cat, nuoc) 32x32 co mep chuyen tiep ghep duoc.

Chay:  python3 AI-GAME-STUDIO/next-game/tools/gen_tiles.py
Ket qua: AI-GAME-STUDIO/next-game/tiles/*.png

Moi bo chuyen tiep A->B la atlas 4x4 (128x128) kieu "goc" (corner Wang):
  chi so o = TL*1 + TR*2 + BL*4 + BR*8   (bit = 1 neu goc do la dia hinh B)
  cot = chi so % 4, hang = chi so // 4
O 0 la toan A, o 15 la toan B. Hai o ke nhau ghep duoc khi chung goc.
"""
import math
import os

import numpy as np
from PIL import Image

from gen_props import HEX, G, W, Y, B, WH

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tiles")
T = 32


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def pal(i):
    return rgb(HEX[i - 1])


# hai mau nen rieng cho co (ngoai bang 32 mau cua mau vat) de vat khong chim vao nen
GRASS, GRASS_D = rgb("78c850"), rgb("62b548")
TERR = {
    # ten: (nen, [(mau dom, mat do, hinh)], mau vien khi nam tren dia hinh khac)
    "co": (GRASS, [(GRASS_D, 0.030, "tick"), (pal(G[3]), 0.022, "tick"), (pal(G[1]), 0.004, "dot")], GRASS_D),
    "dat": (pal(W[3]), [(pal(W[2]), 0.030, "dash"), (pal(Y[3]), 0.015, "dot"), (pal(W[1]), 0.004, "dot")], pal(W[1])),
    "cat": (pal(Y[3]), [(pal(Y[2]), 0.030, "dot"), (pal(WH), 0.012, "dot"), (pal(Y[1]), 0.004, "dot")], pal(Y[1])),
    "nuoc": (pal(B[1]), [(pal(B[2]), 0.014, "wave"), (pal(B[0]), 0.008, "dash")], pal(WH)),
}


def h2(x, y, s):
    n = (x * 374761393 + y * 668265263 + s * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def texture(name, variant=0):
    """Mot o 32x32 lap lai duoc (moi hoa tiet nam gon trong o)."""
    base, specks, _ = TERR[name]
    im = np.zeros((T, T, 3), np.uint8)
    im[:] = base
    for k, (col, dens, shape) in enumerate(specks):
        for y in range(1, T - 2):
            for x in range(1, T - 3):
                if h2(x, y, 17 * k + 101 * variant + len(name)) < dens:
                    pts = {"dot": [(0, 0)], "tick": [(0, 0), (0, 1)], "dash": [(0, 0), (1, 0)],
                           "wave": [(0, 0), (1, 0), (2, 1)]}[shape]
                    for dx, dy in pts:
                        im[y + dy, x + dx] = col
    return im


def field(idx):
    """Truong 0..1: >0.5 la dia hinh B. Lien tuc qua mep o nho nhieu tuan hoan."""
    c = [(idx >> i) & 1 for i in range(4)]
    yy, xx = np.mgrid[0:T, 0:T]
    u, v = (xx + 0.5) / T, (yy + 0.5) / T
    su, sv = u * u * (3 - 2 * u), v * v * (3 - 2 * v)
    f = c[0] * (1 - su) * (1 - sv) + c[1] * su * (1 - sv) + c[2] * (1 - su) * sv + c[3] * su * sv
    wob = 0.07 * np.sin(2 * math.pi * (u * 2 + v) + 0.7) + 0.05 * np.sin(2 * math.pi * (v * 3 - u) + 2.1)
    return f + wob


def transition(a, b):
    sheet = np.zeros((T * 4, T * 4, 3), np.uint8)
    ta, tb = texture(a), texture(b)
    rim = TERR[b][2]
    for idx in range(16):
        if idx == 0:
            tile = ta.copy()
        elif idx == 15:
            tile = tb.copy()
        else:
            f = field(idx)
            mb = f > 0.5
            tile = np.where(mb[..., None], tb, ta)
            band = mb & (f < 0.5 + (0.13 if b == "nuoc" else 0.085))     # vien phia B
            tile[band] = rim
            if b == "nuoc":                                            # bo dat sam phia A
                shore = (~mb) & (f > 0.40)
                tile[shore] = TERR[a][2] if a != "co" else GRASS_D
            else:                                                      # bong nhe: B thap hon A
                lip = (~mb) & (f > 0.44)
                tile[lip] = TERR[a][2]
        sheet[(idx // 4) * T:(idx // 4 + 1) * T, (idx % 4) * T:(idx % 4 + 1) * T] = tile
    return sheet


def preview(sheets):
    """Ban do mau: ho nuoc co bo cat, duong dat cat ngang bai co."""
    Wd, Hd = 20, 11
    g = [["co"] * (Wd + 1) for _ in range(Hd + 1)]                     # dia hinh tai cac GOC
    for y in range(Hd + 1):
        for x in range(Wd + 1):
            d = math.hypot((x - 14.5) / 4.2, (y - 5) / 3.0)
            if d < 0.75:
                g[y][x] = "nuoc"
            elif d < 1.08:
                g[y][x] = "cat"
            elif 4 <= y <= 5 and x < 9 or 3 <= x <= 4:
                g[y][x] = "dat"
    out = np.zeros((Hd * T, Wd * T, 3), np.uint8)
    var = [texture("co", i) for i in range(4)]
    for y in range(Hd):
        for x in range(Wd):
            c = [g[y][x], g[y][x + 1], g[y + 1][x], g[y + 1][x + 1]]
            kinds = set(c)
            if kinds == {"co"}:
                tile = var[int(h2(x, y, 5) * 4) % 4]
            else:
                for (a, b) in (("co", "dat"), ("co", "cat"), ("cat", "nuoc"), ("co", "nuoc")):
                    if kinds <= {a, b}:
                        idx = sum((1 << i) for i in range(4) if c[i] == b)
                        s = sheets[(a, b)]
                        tile = s[(idx // 4) * T:(idx // 4 + 1) * T, (idx % 4) * T:(idx % 4 + 1) * T]
                        break
                else:
                    tile = texture(c[0])
            out[y * T:(y + 1) * T, x * T:(x + 1) * T] = tile
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(np.concatenate([texture("co", i) for i in range(4)], 1)).save(os.path.join(OUT, "nen_co.png"))
    sheets = {}
    for a, b in (("co", "dat"), ("co", "cat"), ("co", "nuoc"), ("cat", "nuoc")):
        sheets[(a, b)] = transition(a, b)
        Image.fromarray(sheets[(a, b)]).save(os.path.join(OUT, "nen_%s_%s.png" % (a, b)))
    p = Image.fromarray(preview(sheets))
    p.resize((p.width * 2, p.height * 2), Image.NEAREST).save(os.path.join(OUT, "_xem_thu.png"))


if __name__ == "__main__":
    main()
