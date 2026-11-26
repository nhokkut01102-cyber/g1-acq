#!/usr/bin/env python3
"""Nhan vat TAM (cho den khi tai duoc sprite PixelLab ve characters/hero/).
Cung cau truc thu muc: characters/hero_placeholder/<dong tac>/<huong>/<so khung>.png
"""
import os
from gen_props import *

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "characters", "hero_placeholder")
SKIN = [W[2], W[3], C[1]]


def body(view, bob, step):
    """view: 0 truoc, 1 sau, 2 ngang (quay phai). step: -1/0/1 the chan."""
    a = new(64, 64)
    y = bob
    for i, s in enumerate((-1, 1)):                        # chan
        lift = 3 if step == s else 0
        x = 32 + s * 5 + (step * 3 * s if view == 2 else 0)
        rect(a, x - 4, 46 + y, x + 3, 57 - lift, W[1]); rect(a, x - 4, 46 + y, x - 3, 55 - lift, W[2])
        rect(a, x - 4, 53 - lift, x + 3, 55 - lift, C[0])           # bang vai
        rect(a, x - 4, 56 - lift, x + 4, 58 - lift, W[0])           # giay
    rect(a, 23, 34 + y, 40, 47 + y, C[1])                  # ao
    rect(a, 23, 34 + y, 26, 47 + y, WH); rect(a, 37, 36 + y, 40, 47 + y, C[0])
    rect(a, 23, 45 + y, 40, 46 + y, W[1])                  # that lung
    sw = step * 2
    if view == 2:
        rect(a, 29 + sw, 36 + y, 34 + sw, 46 + y, C[1]); rect(a, 30 + sw, 46 + y, 34 + sw, 49 + y, SKIN[1])
    else:
        rect(a, 18, 36 + y + sw, 22, 45 + y + sw, C[1]); rect(a, 18, 45 + y + sw, 22, 48 + y + sw, SKIN[1])
        rect(a, 41, 36 + y - sw, 45, 45 + y - sw, C[0]); rect(a, 41, 45 + y - sw, 45, 48 + y - sw, SKIN[0])
    outline(a)
    h = new(64, 64)                                        # dau
    ell(h, (17, 9 + y, 46, 36 + y), SKIN[1])
    ell(h, (17, 9 + y, 40, 30 + y), SKIN[2])
    if view == 0:
        for x in (25, 36):
            rect(h, x, 23 + y, x + 2, 28 + y, O); px(h, x, 23 + y, WH)
        rect(h, 30, 31 + y, 33, 31 + y, R[0])
        rect(h, 21, 29 + y, 23, 30 + y, R[2]); rect(h, 40, 29 + y, 42, 30 + y, R[2])
    elif view == 2:
        rect(h, 39, 23 + y, 41, 28 + y, O); px(h, 39, 23 + y, WH)
        rect(h, 42, 31 + y, 44, 31 + y, R[0])
    blobs(h, [(31, 12 + y, 16, 9, 0), (20, 16 + y, 6, 8, 1), (43, 16 + y, 6, 8, 1), (26, 8 + y, 6, 5, 2), (37, 7 + y, 6, 5, 2)]
          + ([(31, 22 + y, 15, 13, 3)] if view == 1 else [(22, 20 + y, 7, 10, 3)] if view == 2 else []),
          [R[0], R[1], R[1], R[2]])
    if view != 0:                                          # duoi toc
        blobs(h, [(31 if view == 1 else 20, 36 + y, 4, 6, 0)], [R[0], R[1], R[1], R[2]])
    return over(a, outline(h))


DIRS = {"south": (0, 0), "south-east": (0, 0), "south-west": (0, 1), "east": (2, 0), "west": (2, 1),
        "north": (1, 0), "north-east": (1, 0), "north-west": (1, 1)}
ANIMS = {"idle": [(0, 0), (0, 0), (1, 0), (1, 0)], "run": [(0, -1), (1, 0), (0, 1), (1, 0)]}
for anim, frames in ANIMS.items():
    for d, (view, flip) in DIRS.items():
        p = os.path.join(OUT, anim, d)
        os.makedirs(p, exist_ok=True)
        for i, (bob, step) in enumerate(frames):
            a = body(view, bob, step)
            if flip:
                a = a[:, ::-1]
            Image.fromarray(RGBA[a], "RGBA").save(os.path.join(p, "%02d.png" % i))
