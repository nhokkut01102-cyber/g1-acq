#!/usr/bin/env python3
"""Sinh bo do hoa kieu HD-2D: chat lieu pixel lap lai duoc (cho mat 3D) va the pixel
(co, lua, hoa... dung thang trong canh 3D).  Ti le: 32 diem anh = 1 don vi the gioi.

Chay:  python3 tools/gen_art.py      ->  art/tex/*.png, art/sprites/*.png, art/_tong_hop.png
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "art")


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def tex(n, base):
    a = np.zeros((n, n, 3), np.uint8)
    a[:] = hx(base)
    return a


def put(a, x, y, c):
    a[y % a.shape[0], x % a.shape[1]] = hx(c) if isinstance(c, str) else c


def speck(a, rng, col, count, shape="dot"):
    n = a.shape[0]
    pts = {"dot": [(0, 0)], "v2": [(0, 0), (0, 1)], "v3": [(0, 0), (0, 1), (0, 2)], "h2": [(0, 0), (1, 0)],
           "h3": [(0, 0), (1, 0), (2, 0)], "sq": [(0, 0), (1, 0), (0, 1), (1, 1)]}[shape]
    for _ in range(count):
        x, y = rng.randrange(n), rng.randrange(n)
        for dx, dy in pts:
            put(a, x + dx, y + dy, col)


def blotch(a, rng, col, count, r):
    n = a.shape[0]
    for _ in range(count):
        cx, cy = rng.randrange(n), rng.randrange(n)
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy * 1.6 <= r * r and rng.random() < 0.8:
                    put(a, cx + dx, cy + dy, col)


def save_tex(a, name):
    Image.fromarray(a).save(os.path.join(ROOT, "tex", name + ".png"))
    return a


# ---------------------------------------------------------------- chat lieu
def textures():
    out = {}
    rng = random.Random(1)
    a = tex(64, "569c36")
    blotch(a, rng, "5fa63a", 9, 6)
    blotch(a, rng, "4e9233", 7, 5)
    speck(a, rng, "45862f", 150, "v2")
    speck(a, rng, "6cb240", 130, "v2")
    speck(a, rng, "80c24a", 40)
    out["co"] = save_tex(a, "co")

    a = tex(64, "c98d52")
    blotch(a, rng, "d09658", 8, 6)
    blotch(a, rng, "c0844b", 6, 5)
    speck(a, rng, "b27a44", 110, "h2")
    speck(a, rng, "dca968", 80)
    speck(a, rng, "9c6a3c", 14, "sq")
    out["dat"] = save_tex(a, "dat")

    a = tex(64, "e2cc94")
    blotch(a, rng, "e8d4a0", 8, 6)
    speck(a, rng, "d4bc80", 110, "h2")
    speck(a, rng, "f2e2b8", 70)
    speck(a, rng, "cdb47a", 16)
    out["cat"] = save_tex(a, "cat")

    a = tex(32, "efe3c4")
    blotch(a, rng, "f4ead0", 4, 5)
    speck(a, rng, "e2d2ae", 50, "h2")
    speck(a, rng, "d3c09a", 10, "v3")
    out["vua"] = save_tex(a, "vua")

    a = tex(32, "5a5248")                                  # da xay
    cols = ["8d8478", "9d958a", "7b7368", "938a7c"]
    for row in range(4):
        y0 = row * 8
        x = (4 if row % 2 else 0) + rng.randint(0, 2)
        end = x + 32
        while x < end:
            w = min(rng.choice([6, 8, 10, 12]), end - x)
            c = hx(rng.choice(cols))
            for yy in range(y0, y0 + 7):
                for xx in range(x, x + w - 1):
                    put(a, xx, yy, c)
            for xx in range(x, x + w - 1):
                put(a, xx, y0, tuple(min(255, v + 22) for v in c))
                put(a, xx, y0 + 6, tuple(max(0, v - 22) for v in c))
            x += w
    speck(a, rng, "6a6258", 20)
    out["da_xay"] = save_tex(a, "da_xay")

    a = tex(32, "4a261c")                                  # van go do
    for i, c in enumerate(["8e4a32", "9d5538", "7f4230", "96503a"]):
        for yy in range(32):
            for xx in range(i * 8, i * 8 + 7):
                put(a, xx, yy, c)
            put(a, i * 8, yy, tuple(min(255, v + 24) for v in hx(c)))
        for _ in range(5):
            x, y = i * 8 + rng.randint(2, 5), rng.randrange(32)
            for j in range(rng.randint(2, 5)):
                put(a, x, y + j, tuple(max(0, v - 26) for v in hx(c)))
        put(a, i * 8 + 3, 1, "c9a070"); put(a, i * 8 + 3, 30, "c9a070")
    out["van_go"] = save_tex(a, "van_go")

    a = tex(32, "5c3526")                                  # go sam (xa, coc, than cay)
    for _ in range(46):
        x, y, c = rng.randrange(32), rng.randrange(32), rng.choice(["4a2a1f", "6e4230", "7a4b36", "4a2a1f"])
        for j in range(rng.randint(3, 9)):
            put(a, x, y + j, c)
    out["go_sam"] = save_tex(a, "go_sam")

    a = tex(32, "d9a63c")                                  # rom
    for _ in range(170):
        x, y, c = rng.randrange(32), rng.randrange(32), rng.choice(["e9c152", "c08c2e", "f3d778", "e9c152", "b98328"])
        for j in range(rng.randint(3, 7)):
            put(a, x, y + j, c)
    for x in range(32):
        o = rng.randint(0, 2)
        for yy in (14, 30):
            put(a, x, yy + o, "9c6e22"); put(a, x, yy + o + 1, "f6e08a")
    out["rom"] = save_tex(a, "rom")

    a = tex(32, "2f7a30")                                  # tan la
    speck(a, rng, "25632a", 60, "sq")
    for _ in range(26):
        x, y = rng.randrange(32), rng.randrange(32)
        for dx, dy in ((0, 1), (1, 0), (2, 0), (1, 1), (2, 1), (3, 1), (1, 2), (2, 2)):
            put(a, x + dx, y + dy, "479838")
        put(a, x + 1, y, "67b545"); put(a, x + 2, y, "67b545"); put(a, x + 1, y + 1, "67b545")
        put(a, x + 2, y + 3, "1f5526"); put(a, x + 3, y + 2, "1f5526")
    out["la"] = save_tex(a, "la")

    a = tex(32, "8a8f96")                                  # da tang
    blotch(a, rng, "979ca3", 4, 5)
    blotch(a, rng, "7a7f87", 3, 4)
    speck(a, rng, "6c7078", 26, "h2")
    speck(a, rng, "aeb3b8", 26)
    out["da"] = save_tex(a, "da")

    a = tex(32, "9a6a40")                                  # thung go
    for yy in range(32):
        for xx in range(32):
            if xx in (10, 21):
                put(a, xx, yy, "6e472a")
            if xx < 3 or xx > 28 or yy < 3 or yy > 28:
                put(a, xx, yy, "b98550")
            if xx in (3, 28) and 3 <= yy <= 28 or yy in (3, 28) and 3 <= xx <= 28:
                put(a, xx, yy, "5f3c24")
            if abs((31 - xx) - yy) <= 1 and 4 <= xx <= 27:
                put(a, xx, yy, "b98550")
            if (31 - xx) - yy == 2 and 4 <= xx <= 27:
                put(a, xx, yy, "5f3c24")
    for c in ((1, 1), (30, 1), (1, 30), (30, 30)):
        put(a, c[0], c[1], "d8d2c8")
    out["thung"] = save_tex(a, "thung")

    a = tex(32, "f3ead2")                                  # vai canh quat
    for i in range(32):
        for k in (0, 15, 16, 31):
            put(a, i, k, "8e5a3a"); put(a, k, i, "8e5a3a")
        if i % 4 == 0:
            for j in range(32):
                if a[j, i, 0] > 200:
                    put(a, i, j, "e2d6b8")
    out["vai"] = save_tex(a, "vai")
    return out


# ---------------------------------------------------------------- the pixel
def canvas(w, h):
    return np.zeros((h, w, 4), np.uint8)


def sp(a, x, y, c):
    if 0 <= x < a.shape[1] and 0 <= y < a.shape[0]:
        a[y, x] = (hx(c) if isinstance(c, str) else tuple(c)) + (255,)


def ramp_col(ramp, t):
    return ramp[min(len(ramp) - 1, int(t * len(ramp)))]


def blade(a, x0, lean, h, ramp, w=2):
    H = a.shape[0]
    for i in range(h):
        t = i / max(1, h - 1)
        x = x0 + lean * t * t
        c = ramp_col(ramp, t)
        ww = w if t < 0.55 else 1
        for k in range(ww):
            sp(a, int(round(x)) + k, H - 1 - i, c)
        if ww == 2:
            sp(a, int(round(x)) + 1, H - 1 - i, tuple(max(0, v - 18) for v in hx(c)))


GREEN = ["2a5a26", "34702c", "418634", "55a03c", "72b846"]
DRY = ["3f6a2a", "5a8832", "86a03c", "b8ac48", "d8c45c"]
GOLD = ["7a5a20", "9c7828", "bc9434", "d8b248", "ecd070"]
REED = ["3f5f2c", "557a34", "6f9440", "8aa84c", "a4bc5c"]


def tuft(seed, w, h, ramp, n, tips=None):
    rng = random.Random(seed)
    a = canvas(w, h)
    xs = sorted(rng.uniform(w * 0.2, w * 0.8) for _ in range(n))
    order = list(range(n))
    rng.shuffle(order)
    for i in order:
        bx = xs[i]
        lean = (bx - w / 2) * rng.uniform(0.3, 0.9) + rng.uniform(-2, 2)
        bh = int(h * rng.uniform(0.55, 1.0))
        blade(a, int(bx), lean, bh, ramp)
        if tips:
            tx, ty = int(round(bx + lean)), h - bh
            for j, c in enumerate(tips):
                sp(a, tx + (j % 2), ty + j, c)
                sp(a, tx + 1 - (j % 2), ty + j + 1, c)
    return a


def wheat(seed):
    rng = random.Random(seed)
    a = canvas(16, 40)
    for bx in (3, 6, 9, 12, 5, 10, 8):
        lean = rng.uniform(-2.5, 2.5)
        bh = rng.randint(28, 39)
        blade(a, bx, lean, bh, GOLD[:4], 1)
        tx, ty = int(round(bx + lean)), 40 - bh
        for j in range(7):                                 # bong lua
            c = "ecd070" if j % 2 == 0 else "d8b248"
            sp(a, tx, ty + j, c)
            sp(a, tx + (1 if j % 2 else -1), ty + j, "bc9434" if j > 3 else "f4e296")
    return a


def reed(seed):
    rng = random.Random(seed)
    a = canvas(16, 44)
    for bx in (4, 8, 11, 6, 9):
        blade(a, bx, rng.uniform(-3, 3), rng.randint(24, 36), REED)
    for bx, bh in ((5, 42), (10, 38)):
        blade(a, bx, 0, bh, REED[:3], 1)
        for j in range(8):
            sp(a, bx, 44 - bh + j, "7a4a30"); sp(a, bx + 1, 44 - bh + j, "5f3824")
        sp(a, bx, 44 - bh, "9a6a48")
    return a


def flower(seed, petal, petal_d, core="f2c84a"):
    rng = random.Random(seed)
    a = canvas(16, 24)
    heads = [(4, 9), (10, 5), (8, 13)]
    for hx_, hy in heads:
        for y in range(hy + 2, 24):
            t = (y - hy) / (24 - hy)
            sp(a, int(round(hx_ + (8 - hx_) * t * t)) + 1, y, GREEN[1] if t > 0.5 else GREEN[2])
    for i in range(3):
        blade(a, 5 + i * 2, rng.uniform(-4, 4), rng.randint(5, 9), GREEN[1:4])
    for hx_, hy in heads:
        for dx, dy in ((0, -1), (1, -1), (-1, 0), (2, 0), (-1, 1), (2, 1), (0, 2), (1, 2)):
            sp(a, hx_ + dx, hy + dy, petal if dx + dy < 2 else petal_d)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            sp(a, hx_ + dx, hy + dy, core)
    return a


def mushroom(cap, cap_l, cap_d, dots):
    a = canvas(16, 16)
    for (cx, by, r, sh) in ((5, 15, 4, 5), (12, 15, 3, 3)):
        for y in range(by - sh, by + 1):
            sp(a, cx - 1, y, "f3ead2"); sp(a, cx, y, "f3ead2"); sp(a, cx + 1, y, "cfc2a2")
        top = by - sh - r
        for y in range(top, top + r + 1):
            hw = int(round(math.sqrt(max(0, r * r - (top + r - y) ** 2)) * 1.25))
            for x in range(cx - hw, cx + hw + 1):
                sp(a, x, y, cap_l if (x < cx and y < top + r - 1) else cap)
            sp(a, cx + hw, y, cap_d)
        for x in range(cx - r, cx + r + 1):
            sp(a, x, top + r, cap_d)
        if dots:
            sp(a, cx - 2, top + 2, "ffffff"); sp(a, cx + 1, top + 1, "ffffff"); sp(a, cx + 2, top + 3, "ffffff")
    return a


def lily(flower_on):
    a = canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, (y - 7.5) * 1.15)
            ang = math.atan2(y - 7.5, x - 7.5)
            if d < 7 and not (0.2 < ang < 0.75):
                sp(a, x, y, "4f9a3a" if d < 6 else "356e2e")
                if d < 5.2 and x + y < 13:
                    sp(a, x, y, "66b244")
    for i in range(1, 6):
        sp(a, 7 - i, 7 - i // 2, "3f8233"); sp(a, 7, 7 - i, "3f8233"); sp(a, 7 - i // 2, 7 + i, "3f8233")
    if flower_on:
        for dx, dy, c in ((0, 0, "f2c84a"), (-1, 0, "f7b6cf"), (1, 0, "e889b0"), (0, -1, "ffd9e6"), (0, 1, "e889b0"),
                          (-1, -1, "ffd9e6"), (1, 1, "d86a98")):
            sp(a, 9 + dx, 6 + dy, c)
    return a


def door():
    a = canvas(32, 48)
    for y in range(48):
        for x in range(32):
            arch = y < 12 and math.hypot(x - 15.5, y - 12) > 14
            if arch or x < 2 or x > 29:
                continue
            c = ["8e4a32", "9d5538", "7f4230", "96503a"][(x - 4) // 6 % 4]
            if (x - 4) % 6 == 5:
                c = "4a261c"
            edge = x < 4 or x > 27 or (y < 14 and math.hypot(x - 15.5, y - 12) > 12)
            sp(a, x, y, "4a2a1f" if edge else c)
    for y in (16, 17, 36, 37):
        for x in range(4, 28):
            sp(a, x, y, "3a3540")
    for x, y in ((23, 27), (24, 27), (23, 28), (24, 28)):
        sp(a, x, y, "f2c84a")
    return a


def window():
    a = canvas(32, 32)
    for y in range(2, 30):
        for x in range(2, 30):
            frame = x < 5 or x > 26 or y < 5 or y > 26 or x in (15, 16) or y in (15, 16)
            sp(a, x, y, "5c3526" if frame else ("6fb6e0" if y < 15 else "4a94c8"))
    for i in range(5):
        sp(a, 7 + i, 12 - i, "d6f0fb"); sp(a, 19 + i, 24 - i, "d6f0fb")
    for x in range(1, 31):
        sp(a, x, 28, "7a4b36"); sp(a, x, 29, "5c3526"); sp(a, x, 30, "4a2a1f")
    return a


def sign():
    a = canvas(32, 32)
    for y in range(6, 32):
        sp(a, 15, y, "7a4b36"); sp(a, 16, y, "5c3526")
    for y in range(3, 14):
        for x in range(3, 29):
            if x - 23 > 5 - abs(y - 8):
                continue
            sp(a, x, y, "b98550" if y > 3 else "d8a868")
            if y == 13 or x == 3:
                sp(a, x, y, "6e472a")
    for x0, x1, y in ((7, 13, 7), (15, 21, 7), (7, 18, 10)):
        for x in range(x0, x1):
            sp(a, x, y, "5f3c24")
    return a


def sprites():
    out = {
        "co_1": tuft(1, 24, 18, GREEN, 12), "co_2": tuft(2, 28, 26, GREEN, 14), "co_3": tuft(3, 32, 34, GREEN, 16),
        "co_kho": tuft(4, 28, 30, DRY, 13, tips=["e6d468", "cfc050", "e6d468"]),
        "lua_1": wheat(5), "lua_2": wheat(6), "lau": reed(7),
        "hoa_trang": flower(8, "ffffff", "d9dde6"), "hoa_vang": flower(9, "ffe07a", "e6b83c", "c9862e"),
        "hoa_hong": flower(10, "f7a6c4", "d86a98"), "hoa_tim": flower(11, "b99ae6", "8a66c4"),
        "nam_do": mushroom("d4453c", "ee7a5c", "9c2a30", True), "nam_nau": mushroom("b9824c", "d8a868", "8a5a32", False),
        "la_sung": lily(False), "la_sung_hoa": lily(True),
        "cua": door(), "cua_so": window(), "bien": sign(),
    }
    for k, a in out.items():
        Image.fromarray(a, "RGBA").save(os.path.join(ROOT, "sprites", k + ".png"))
    return out


# ---------------------------------------------------------------- nhan vat tu anh goc
def hero():
    """Thu nho anh nhan vat chu du an gui thanh sprite pixel (tam, 1 huong) de xem dang trong canh."""
    src = os.path.join(ROOT, "ref", "hero_ref.png")
    if not os.path.exists(src):
        return None
    im = Image.open(src).convert("RGB").crop((225, 1085, 625, 2032))
    for seed in ((2, 2), (im.width - 3, 2), (2, im.height - 3), (im.width - 3, im.height - 3), (im.width // 2, 2)):
        ImageDraw.floodfill(im, seed, (255, 0, 255), thresh=34)
    a = np.array(im)
    alpha = np.where((a[..., 0] == 255) & (a[..., 1] == 0) & (a[..., 2] == 255), 0, 255).astype(np.uint8)
    H = 72
    Wd = round(im.width * H / im.height)
    rgb = np.array(im, float)
    rgb[alpha == 0] = 0
    w8 = Image.fromarray(alpha).resize((Wd, H), Image.BOX)
    small = np.zeros((H, Wd, 3))
    for c in range(3):
        ch = np.array(Image.fromarray(rgb[..., c].astype(np.uint8)).resize((Wd, H), Image.BOX), float)
        small[..., c] = ch * 255.0 / np.maximum(np.array(w8, float), 1)
    al = (np.array(w8) > 120)
    q = Image.fromarray(np.clip(small, 0, 255).astype(np.uint8)).quantize(20, method=Image.MEDIANCUT, dither=Image.NONE).convert("RGB")
    out = np.dstack([np.array(q), np.where(al, 255, 0).astype(np.uint8)])
    cv = np.zeros((H + 2, Wd + 2, 4), np.uint8)
    cv[1:-1, 1:-1] = out
    m = cv[..., 3] > 0                                     # vien toi 1px
    d = np.zeros_like(m)
    d[1:] |= m[:-1]; d[:-1] |= m[1:]; d[:, 1:] |= m[:, :-1]; d[:, :-1] |= m[:, 1:]
    cv[d & ~m] = (58, 30, 34, 255)
    os.makedirs(os.path.join(ROOT, "hero"), exist_ok=True)
    Image.fromarray(cv, "RGBA").save(os.path.join(ROOT, "hero", "hero_tam.png"))
    return cv


def sheet(texs, sprs, hero_img):
    Z = 4
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
    Wd = 1500
    im = Image.new("RGBA", (Wd, 1100), (52, 60, 52, 255))
    d = ImageDraw.Draw(im)
    d.text((12, 8), "Chất liệu lặp lại được (phủ lên mặt 3D) — phóng x4, mỗi ô lặp 2 lần", font=font, fill="white")
    x = 12
    for k, a in texs.items():
        t = Image.fromarray(np.tile(a, (2, 2, 1)) if a.shape[0] == 32 else a).resize((64 * 2, 64 * 2), Image.NEAREST)
        im.paste(t, (x, 30)); d.text((x, 162), k, font=font, fill="white"); x += 136
    d.text((12, 196), "Thẻ pixel (đứng thẳng trong cảnh 3D, không viền, không phối cảnh vẽ sẵn) — phóng x4", font=font, fill="white")
    x, y, rh = 12, 220, 0
    for k, a in sprs.items():
        t = Image.fromarray(a, "RGBA").resize((a.shape[1] * Z, a.shape[0] * Z), Image.NEAREST)
        if x + t.width > Wd - 10:
            x, y, rh = 12, y + rh + 30, 0
        d.rectangle([x, y, x + t.width, y + t.height], fill=(94, 166, 58, 255))
        im.alpha_composite(t, (x, y)); d.text((x, y + t.height + 3), k, font=font, fill="white")
        x += max(t.width, 60) + 12; rh = max(rh, t.height)
    if hero_img is not None:
        t = Image.fromarray(hero_img, "RGBA").resize((hero_img.shape[1] * Z, hero_img.shape[0] * Z), Image.NEAREST)
        y += rh + 30
        d.rectangle([12, y, 12 + t.width, y + t.height], fill=(94, 166, 58, 255))
        im.alpha_composite(t, (12, y)); d.text((20 + t.width, y + 4), "nhân vật (tạm: thu nhỏ từ ảnh gốc, 1 hướng)", font=font, fill="white")
        y += t.height
    im.crop((0, 0, Wd, y + 30)).convert("RGB").save(os.path.join(ROOT, "_tong_hop.png"))


if __name__ == "__main__":
    for sub in ("tex", "sprites"):
        os.makedirs(os.path.join(ROOT, sub), exist_ok=True)
    sheet(textures(), sprites(), hero())
