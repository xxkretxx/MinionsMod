"""Generates every texture the mod needs (entity skins, items, GUI sprites, icon)."""
import json, math, os, random, sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from model_def import CUBES, TIERS, TEX_W, TEX_H

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
A = os.path.join(OUT, "assets", "buildminions", "textures")


def save(img, rel):
    p = os.path.join(A, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    return p


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


# --------------------------------------------------------------------------- entity
IRON = dict(base=(150, 156, 166), light=(196, 202, 212), dark=(98, 104, 116), darker=(64, 68, 78))
WOOD = dict(base=(122, 82, 48), light=(156, 108, 64), dark=(88, 58, 32), darker=(60, 38, 20))
LEATHER = dict(base=(122, 79, 46), light=(156, 106, 64), dark=(85, 54, 31), darker=(58, 36, 20))


def face_rects(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def metal(w, h, pal, rng, face, patina=None, bevel=True):
    g = [[pal["base"]] * w for _ in range(h)]
    lit = {"top": 1.12, "bottom": 0.78}.get(face, 1.0)
    for y in range(h):
        for x in range(w):
            r = rng.random()
            c = pal["base"]
            if r < 0.10:
                c = mix(pal["base"], pal["light"], 0.55)
            elif r < 0.18:
                c = mix(pal["base"], pal["dark"], 0.45)
            g[y][x] = shade(c, lit)
    if patina:
        for _ in range(max(1, (w * h) // 18)):
            px, py = rng.randrange(w), rng.randrange(h)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0)):
                if 0 <= px + dx < w and 0 <= py + dy < h and rng.random() < 0.8:
                    g[py + dy][px + dx] = patina[0] if rng.random() < 0.7 else patina[1]
    if bevel and w > 2 and h > 2:
        for x in range(w):
            g[0][x] = shade(mix(g[0][x], pal["light"], 0.7), lit)
            g[h - 1][x] = shade(mix(g[h - 1][x], pal["darker"], 0.7), lit)
        for y in range(h):
            g[y][0] = mix(g[y][0], pal["light"], 0.35)
            g[y][w - 1] = mix(g[y][w - 1], pal["dark"], 0.55)
    return g


def rivet(g, x, y, pal):
    if 0 <= y < len(g) and 0 <= x < len(g[0]):
        g[y][x] = pal["light"]
        if x + 1 < len(g[0]) and y + 1 < len(g):
            g[y + 1][x + 1] = pal["darker"]


def paint_entity(tier):
    pal = TIERS[tier]
    rng = random.Random(hash(tier) & 0xffff)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    patina = (pal["patina"], pal["patina_l"]) if pal["patina"] else None

    def put(rect, grid):
        x0, y0, w, h = rect
        for y in range(h):
            for x in range(w):
                c = grid[y][x]
                px[x0 + x, y0 + y] = c + (255,) if len(c) == 3 else c

    for part, name, frm, size, uv, mirror in CUBES:
        w, h, d = size
        rects = face_rects(uv[0], uv[1], w, h, d)
        if name == "bolt_l":
            continue  # shares UV with bolt_r (mirrored)
        for face, rect in rects.items():
            fw, fh = rect[2], rect[3]
            if name in ("skull", "torso", "arm", "leg"):
                g = metal(fw, fh, pal, rng, face, patina if name in ("skull", "torso") else None)
            elif name == "pack":
                g = metal(fw, fh, LEATHER, rng, face, None)
            elif name in ("bolt_r", "stalk"):
                g = metal(fw, fh, IRON, rng, face, None, bevel=False)
            elif name == "handle":
                g = metal(fw, fh, WOOD, rng, face, None, bevel=False)
                for x in range(fw):
                    for y in range(fh):
                        if (x + y) % 3 == 0:
                            g[y][x] = WOOD["dark"]
            elif name == "head":  # hammer head
                g = metal(fw, fh, IRON, rng, face, None)
                if face in ("right", "left", "front", "back") and fh >= 3:
                    for x in range(fw):
                        g[fh // 2][x] = pal["base"]
            elif name == "gem":
                g = [[pal["gem"]] * fw for _ in range(fh)]
                g[0][0] = pal["gem_l"]
                if fw > 1: g[fh - 1][fw - 1] = pal["gem_d"]
                if face == "top": g = [[pal["gem_l"], pal["gem"]], [pal["gem"], pal["gem_l"]]]
            else:
                g = metal(fw, fh, pal, rng, face)

            # --- details ---
            if name == "skull":
                if face == "front":  # visor with glowing eyes
                    for y in range(2, 8):
                        for x in range(1, 9):
                            t = (y - 2) / 5
                            g[y][x] = mix((20, 22, 30), (40, 44, 58), t)
                    for x in range(1, 9):
                        g[1][x] = pal["darker"]; g[8][x] = mix(pal["darker"], pal["dark"], .5)
                    for y in range(2, 8):
                        g[y][0] = pal["darker"]; g[y][9] = pal["darker"]
                    for ex in (2, 6):
                        for yy in (3, 4):
                            for xx in (ex, ex + 1):
                                g[yy][xx] = pal["eye"]
                        g[3][ex] = pal["eye_glow"]
                        g[5][ex] = mix(pal["eye"], (20, 22, 30), .6); g[5][ex + 1] = mix(pal["eye"], (20, 22, 30), .6)
                    g[6][4] = mix(pal["eye"], (30, 30, 40), .35); g[6][5] = mix(pal["eye"], (30, 30, 40), .35)
                    g[2][7] = (90, 96, 118)  # glass reflection
                    g[2][8] = (70, 76, 96)
                elif face == "top":
                    for x in range(fw):
                        g[5][x] = mix(g[5][x], pal["dark"], .6)
                    g[4][4] = g[4][5] = g[5][4] = g[5][5] = pal["darker"]
                    for (rx, ry) in ((1, 1), (8, 1), (1, 8), (8, 8)):
                        rivet(g, rx, ry, pal)
                elif face in ("right", "left"):
                    for y in (2, 4, 6):
                        for x in range(2, 8):
                            g[y][x] = pal["darker"]
                            g[y + 1][x] = mix(g[y + 1][x], pal["light"], .4)
                elif face == "back":
                    for y in range(3, 7):
                        for x in range(3, 7):
                            g[y][x] = mix(pal["base"], pal["dark"], .35)
                    for (rx, ry) in ((3, 3), (6, 3), (3, 6), (6, 6)):
                        rivet(g, rx, ry, pal)
            if name == "torso":
                if face == "front":
                    for x in range(fw):
                        g[5][x] = (70, 46, 28)
                    g[5][3] = g[5][4] = IRON["light"]
                    g[2][3] = g[2][4] = g[3][3] = g[3][4] = pal["gem"]
                    g[2][3] = pal["gem_l"]
                    for x in (2, 5):
                        for y in (1, 2, 3, 4):
                            g[y][x] = mix(g[y][x], pal["darker"], .5)
                    g[1][3] = g[1][4] = g[4][3] = g[4][4] = pal["darker"]
                elif face in ("back", "left", "right"):
                    for x in range(fw):
                        g[5][x] = (70, 46, 28)
                    rivet(g, 1, 1, pal); rivet(g, fw - 3, 1, pal)
            if name == "arm" and face != "top":
                for y in range(max(0, fh - 2), fh):
                    for x in range(fw):
                        g[y][x] = mix(g[y][x], pal["darker"], .5)
                if face in ("front", "back", "left", "right") and fh > 3:
                    for x in range(fw):
                        g[1][x] = mix(g[1][x], pal["trim"], .6)
            if name == "leg" and face != "top":
                for y in range(max(0, fh - 2), fh):
                    for x in range(fw):
                        g[y][x] = mix(IRON["dark"], pal["darker"], .4) if face != "bottom" else IRON["darker"]
            if name == "pack":
                if face == "back":
                    for y in range(fh):
                        g[y][1] = LEATHER["darker"]; g[y][fw - 2] = LEATHER["darker"]
                    g[2][2] = g[2][3] = IRON["light"]
                if face == "top":  # rolled blueprint
                    for x in range(1, fw - 1):
                        g[0][x] = (59, 111, 214); g[1][x] = (40, 80, 170)
                    g[0][1] = g[1][1] = (230, 230, 240)
            put(rect, g)
    return img


def paint_glow(tier, base):
    """Emissive layer: eyes, chest core and antenna gem only."""
    pal = TIERS[tier]
    g = Image.new("RGBA", base.size, (0, 0, 0, 0))
    src, dst = base.load(), g.load()
    keep = {pal["eye"], pal["eye_glow"], pal["gem"], pal["gem_l"]}
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if src[x, y][:3] in keep:
                dst[x, y] = src[x, y]
    return g


# --------------------------------------------------------------------------- items
def item_minion(tier):
    pal = TIERS[tier]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    O = (28, 22, 30)
    # head 10x9 at (3,1)
    for y in range(1, 10):
        for x in range(3, 13):
            c = pal["base"]
            if y == 1 or x == 3: c = pal["light"]
            if y == 9 or x == 12: c = pal["dark"]
            p[x, y] = c + (255,)
    for x in range(3, 13): p[x, 0] = O + (255,); p[x, 10] = O + (255,)
    for y in range(1, 10): p[2, y] = O + (255,); p[13, y] = O + (255,)
    for y in range(3, 8):
        for x in range(4, 12):
            p[x, y] = mix((20, 22, 30), (44, 48, 62), (y - 3) / 4) + (255,)
    for ex in (5, 9):
        p[ex, 4] = pal["eye_glow"] + (255,); p[ex + 1, 4] = pal["eye"] + (255,)
        p[ex, 5] = pal["eye"] + (255,); p[ex + 1, 5] = pal["eye"] + (255,)
    p[10, 3] = (90, 96, 118, 255)
    # antenna
    p[8, 0] = IRON["dark"] + (255,)
    # body 6x4 at (5,11)
    for y in range(11, 15):
        for x in range(5, 11):
            c = pal["base"] if x < 10 else pal["dark"]
            if y == 11: c = pal["light"]
            p[x, y] = c + (255,)
    p[7, 12] = pal["gem_l"] + (255,); p[8, 12] = pal["gem"] + (255,)
    for x in range(5, 11): p[x, 15] = O + (255,)
    for y in range(11, 15): p[4, y] = O + (255,); p[11, y] = O + (255,)
    # hammer
    for y in range(9, 15): p[13, y] = WOOD["base"] + (255,)
    for x in range(12, 16): p[x, 9] = IRON["light"] + (255,); p[x, 10] = IRON["dark"] + (255,)
    if tier == "copper":
        p[4, 6] = pal["patina"] + (255,); p[11, 2] = pal["patina_l"] + (255,); p[6, 13] = pal["patina"] + (255,)
    return img


def item_core():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - cx, y - cy)
            if r < 7.2:
                if r > 5.6:
                    c = (60, 52, 70) if (x + y) % 2 else (88, 80, 98)
                    if r > 6.6: c = (28, 22, 34)
                else:
                    t = r / 5.6
                    c = mix((255, 240, 255), (168, 80, 230), min(1, t * 1.1))
                    if r > 4.6: c = (110, 40, 170)
                img.putpixel((x, y), c + (255,))
    for (x, y) in ((5, 5), (6, 5), (5, 6)):
        img.putpixel((x, y), (255, 255, 255, 255))
    for (x, y) in ((7, 1), (7, 14), (1, 7), (14, 7)):
        img.putpixel((x, y), (227, 60, 60, 255)); img.putpixel((x + 1, y), (170, 30, 30, 255))
    return img


# --------------------------------------------------------------------------- GUI
FRAME_D = (24, 26, 34); FRAME = (52, 58, 74); FRAME_L = (92, 102, 126); FRAME_H = (138, 150, 178)
BRASS = (214, 160, 72); BRASS_L = (250, 214, 130); BRASS_D = (140, 96, 40)
PAPER = (30, 62, 120); PAPER_L = (44, 84, 152); GRID = (58, 102, 176); GRID_L = (78, 128, 204)


def nine(img, rel, border, kind="nine_slice"):
    p = save(img, "gui/sprites/" + rel + ".png")
    meta = {"gui": {"scaling": {"type": kind, "width": img.size[0], "height": img.size[1]}}}
    if kind == "nine_slice":
        meta["gui"]["scaling"]["border"] = border
    with open(p + ".mcmeta", "w") as f:
        json.dump(meta, f, indent=2)


def sprite(img, rel):
    save(img, "gui/sprites/" + rel + ".png")


def panel():
    s = 48
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = img.load()
    for y in range(s):
        for x in range(s):
            e = min(x, y, s - 1 - x, s - 1 - y)
            if e == 0: c = FRAME_D
            elif e == 1: c = BRASS_L if (x < s - 2 and y < s - 2 and (x == 1 or y == 1)) else BRASS_D
            elif e == 2: c = BRASS
            elif e == 3: c = FRAME_D
            elif e < 7:
                c = FRAME_L if e == 4 else FRAME
                if (x + y) % 7 == 0 and e > 4: c = shade(FRAME, 1.08)
            elif e == 7: c = FRAME_D
            else: c = (20, 24, 34)
            p[x, y] = c + (255,)
    # corner rivets
    for (rx, ry) in ((5, 5), (s - 6, 5), (5, s - 6), (s - 6, s - 6)):
        p[rx, ry] = BRASS_L + (255,); p[rx + 1, ry] = BRASS + (255,); p[rx, ry + 1] = BRASS + (255,); p[rx + 1, ry + 1] = BRASS_D + (255,)
    # cut corners
    for (x, y) in ((0, 0), (s - 1, 0), (0, s - 1), (s - 1, s - 1)):
        p[x, y] = (0, 0, 0, 0)
    return img


def blueprint_tile():
    img = Image.new("RGBA", (16, 16), PAPER + (255,))
    p = img.load()
    rng = random.Random(3)
    for y in range(16):
        for x in range(16):
            c = PAPER if rng.random() > .12 else PAPER_L
            if x % 8 == 0 or y % 8 == 0: c = GRID
            if x % 16 == 0 or y % 16 == 0: c = GRID_L
            p[x, y] = c + (255,)
    return img


def inset():
    s = 16
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = img.load()
    for y in range(s):
        for x in range(s):
            e = min(x, y, s - 1 - x, s - 1 - y)
            if e == 0: c = (14, 18, 28) if (x == 0 or y == 0) else (96, 128, 186)
            else: c = (22, 42, 84, 235)
            p[x, y] = c if len(c) == 4 else c + (255,)
    return img


def button(state):
    s = 20
    base = {"normal": (72, 82, 104), "hover": (98, 116, 150), "disabled": (48, 50, 58), "pressed": (60, 68, 88)}[state]
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = img.load()
    for y in range(s):
        for x in range(s):
            e = min(x, y, s - 1 - x, s - 1 - y)
            if e == 0: c = (12, 12, 18)
            elif e == 1:
                if y == 1 or x == 1: c = shade(base, 1.5) if state != "disabled" else shade(base, 1.15)
                else: c = shade(base, 0.6)
            else:
                t = y / s
                c = mix(shade(base, 1.12), shade(base, 0.9), t)
            if state == "hover" and e == 0: c = BRASS_L
            p[x, y] = c + (255,)
    for (x, y) in ((0, 0), (s - 1, 0), (0, s - 1), (s - 1, s - 1)):
        p[x, y] = (0, 0, 0, 0)
    return img


def tab(selected):
    w, h = 24, 20
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    p = img.load()
    body = (52, 58, 74) if selected else (34, 38, 50)
    for y in range(h):
        for x in range(w):
            e = min(x, y, w - 1 - x)
            if e == 0: c = (16, 18, 24)
            elif e == 1: c = BRASS if selected else (70, 78, 98)
            else: c = mix(shade(body, 1.15), body, y / h)
            if selected and y == h - 1 and 0 < x < w - 1: c = body
            p[x, y] = c + (255,)
    p[0, 0] = p[w - 1, 0] = (0, 0, 0, 0)
    return img


def row(done):
    w, h = 32, 22
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    p = img.load()
    base = (28, 70, 52) if done else (24, 46, 90)
    edge = (70, 190, 120) if done else (80, 116, 180)
    for y in range(h):
        for x in range(w):
            e = min(x, y, w - 1 - x, h - 1 - y)
            if e == 0: c = (10, 14, 22)
            elif e == 1: c = edge if (y == 1 or x == 1) else shade(edge, .55)
            else: c = mix(shade(base, 1.18), base, y / h)
            p[x, y] = c + (235,)
    return img


def slot18():
    s = 18
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = img.load()
    for y in range(s):
        for x in range(s):
            if x == 0 or y == 0: c = (12, 14, 20)
            elif x == s - 1 or y == s - 1: c = (120, 140, 180)
            else: c = (30, 36, 50)
            p[x, y] = c + (255,)
    return img


def progress(kind):
    w, h = 16, 8
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    p = img.load()
    for y in range(h):
        for x in range(w):
            e = min(x, y, w - 1 - x, h - 1 - y)
            if kind == "bg":
                c = (10, 12, 18) if e == 0 else (34, 38, 52)
            else:
                cols = {"fill": ((120, 230, 140), (40, 150, 70)), "fill_warn": ((255, 210, 90), (200, 120, 20)),
                        "fill_blue": ((140, 210, 255), (40, 110, 200))}[kind]
                if e == 0: c = (0, 0, 0)
                else: c = mix(cols[0], cols[1], y / (h - 1))
                if e == 0:
                    p[x, y] = (0, 0, 0, 0); continue
                if y == 1: c = mix(c, (255, 255, 255), .35)
            p[x, y] = c + (255,)
    return img


def accept_slot(state):
    s = 40
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = img.load()
    c0 = {"ready": (60, 200, 110), "hover": (110, 255, 160), "locked": (90, 90, 100)}[state]
    for y in range(s):
        for x in range(s):
            dx, dy = x - 19.5, y - 19.5
            r = math.hypot(dx, dy)
            # gear outline: 8 teeth
            ang = math.atan2(dy, dx)
            tooth = 1.6 if (math.cos(ang * 8) > 0.35) else 0
            if r < 16.5 + tooth:
                if r > 15 + tooth * .9:
                    c = BRASS_D if state != "locked" else (70, 70, 78)
                elif r > 13:
                    c = BRASS if state != "locked" else (110, 110, 120)
                    if dy < 0 and dx < 0: c = BRASS_L if state != "locked" else (150, 150, 160)
                elif r > 11.5:
                    c = (16, 18, 24)
                else:
                    c = mix(shade(c0, 0.55), shade(c0, 0.28), (r / 11.5))
                    if state == "hover": c = mix(shade(c0, 0.7), shade(c0, 0.35), r / 11.5)
                p[x, y] = c + (255,)
    # check mark / lock
    if state != "locked":
        pts = [(13, 20), (17, 24), (26, 15)]
        col = (230, 255, 236) if state == "hover" else (190, 255, 205)
        d = ImageDraw.Draw(img)
        d.line(pts, fill=col + (255,), width=3, joint="curve")
    else:
        d = ImageDraw.Draw(img)
        d.rectangle((15, 19, 24, 26), fill=(160, 160, 170, 255), outline=(40, 40, 48, 255))
        d.arc((16, 12, 23, 22), 180, 360, fill=(160, 160, 170, 255), width=2)
        p[19, 22] = (40, 40, 48, 255); p[20, 22] = (40, 40, 48, 255); p[19, 23] = (40, 40, 48, 255)
    return img


def icon(name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    p = img.load()
    if name == "clock":
        d.ellipse((1, 1, 14, 14), fill=(232, 236, 245, 255), outline=(40, 44, 60, 255))
        d.ellipse((2, 2, 13, 13), outline=BRASS + (255,))
        d.line((7.5, 7.5, 7.5, 4), fill=(40, 44, 60, 255)); d.line((7.5, 7.5, 10.5, 9), fill=(200, 50, 50, 255))
    elif name == "blocks":
        for (ox, oy, c) in ((1, 7, (180, 120, 80)), (8, 7, (120, 160, 90)), (4, 1, (150, 156, 166))):
            d.rectangle((ox, oy, ox + 6, oy + 6), fill=c + (255,), outline=(30, 30, 38, 255))
            d.line((ox + 1, oy + 1, ox + 5, oy + 1), fill=shade(c, 1.3) + (255,))
    elif name == "heart":  # durability: wrench/heart hybrid -> spark
        d.polygon([(8, 14), (1, 7), (1, 4), (4, 1), (8, 4), (12, 1), (15, 4), (15, 7)], fill=(225, 60, 70, 255), outline=(70, 14, 22, 255))
        p[4, 3] = (255, 180, 186, 255); p[5, 3] = (255, 180, 186, 255); p[4, 4] = (255, 180, 186, 255)
    elif name == "size":
        d.polygon([(8, 1), (15, 4), (8, 7), (1, 4)], fill=(120, 190, 255, 255), outline=(20, 40, 80, 255))
        d.polygon([(1, 4), (8, 7), (8, 15), (1, 11)], fill=(60, 120, 210, 255), outline=(20, 40, 80, 255))
        d.polygon([(15, 4), (8, 7), (8, 15), (15, 11)], fill=(40, 90, 170, 255), outline=(20, 40, 80, 255))
    elif name == "warning":
        d.polygon([(8, 1), (15, 14), (1, 14)], fill=(255, 200, 40, 255), outline=(90, 60, 0, 255))
        d.line((8, 5, 8, 10), fill=(60, 40, 0, 255), width=2); d.point((8, 12), fill=(60, 40, 0, 255)); p[7, 12] = (60, 40, 0, 255)
    elif name == "check":
        d.line([(2, 8), (6, 12), (14, 3)], fill=(90, 230, 120, 255), width=3)
    elif name == "file":
        d.polygon([(3, 1), (10, 1), (13, 4), (13, 15), (3, 15)], fill=(60, 110, 210, 255), outline=(16, 30, 70, 255))
        d.polygon([(10, 1), (10, 4), (13, 4)], fill=(150, 190, 255, 255))
        for yy in (7, 9, 11): d.line((5, yy, 11, yy), fill=(200, 225, 255, 255))
    elif name == "arrow_left":
        d.polygon([(11, 2), (11, 13), (4, 7.5)], fill=(240, 220, 160, 255), outline=(60, 40, 10, 255))
    elif name == "arrow_right":
        d.polygon([(4, 2), (4, 13), (11, 7.5)], fill=(240, 220, 160, 255), outline=(60, 40, 10, 255))
    elif name == "rotate":
        d.arc((2, 2, 13, 13), 40, 320, fill=(240, 220, 160, 255), width=2)
        d.polygon([(12, 1), (14, 6), (9, 5)], fill=(240, 220, 160, 255))
    elif name == "chest":
        d.rectangle((1, 4, 14, 14), fill=(150, 100, 50, 255), outline=(50, 30, 10, 255))
        d.line((1, 8, 14, 8), fill=(50, 30, 10, 255)); d.rectangle((6, 7, 9, 10), fill=(220, 210, 180, 255))
    return img


def badge(tier):
    pal = TIERS[tier]
    s = 24
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    p = img.load()
    for y in range(s):
        for x in range(s):
            dx, dy = abs(x - 11.5), abs(y - 11.5)
            if dx + dy < 12:
                if dx + dy > 10.5: c = (16, 16, 22)
                elif dx + dy > 8.5: c = pal["light"] if (x < 12 and y < 12) else pal["dark"]
                else: c = mix(pal["light"], pal["base"], (y / s))
                p[x, y] = c + (255,)
    for y in range(9, 14):
        for x in range(8, 16):
            p[x, y] = (24, 26, 34, 255)
    for ex in (9, 13):
        p[ex, 11] = pal["eye_glow"] + (255,); p[ex + 1, 11] = pal["eye"] + (255,)
    return img


def header():
    w, h = 32, 16
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    p = img.load()
    for y in range(h):
        for x in range(w):
            e = min(x, y, w - 1 - x, h - 1 - y)
            if e == 0: c = (14, 12, 8)
            elif e == 1: c = BRASS_L if (y == 1) else BRASS_D
            else: c = mix(BRASS, BRASS_D, y / h)
            p[x, y] = c + (255,)
    return img


def logo():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    bg = Image.new("RGBA", (16, 16))
    t = blueprint_tile()
    for y in range(0, 128, 16):
        for x in range(0, 128, 16):
            img.paste(t, (x, y))
    m = Image.new("L", (128, 128), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, 127, 127), 22, fill=255)
    img.putalpha(m)
    for i, tier in enumerate(("copper", "diamond", "gold")):
        ic = item_minion(tier).resize((64, 64), Image.NEAREST)
        pos = [(4, 54), (32, 24), (60, 54)][i]
        img.alpha_composite(ic, pos)
    return img


def main():
    out = {}
    for tier in TIERS:
        sk = paint_entity(tier)
        save(sk, f"entity/minion_{tier}.png")
        save(paint_glow(tier, sk), f"entity/minion_{tier}_glow.png")
        save(item_minion(tier), f"item/{tier}_minion.png")
        sprite(badge(tier), f"badge_{tier}")
    save(item_core(), "item/minion_core.png")
    nine(panel(), "panel", 8)
    nine(blueprint_tile(), "blueprint", 0, "tile")
    nine(inset(), "inset", 2)
    for st in ("normal", "hover", "disabled"):
        nine(button(st), "button_" + st, 3)
    nine(tab(True), "tab_selected", 3)
    nine(tab(False), "tab", 3)
    nine(row(False), "row", 3)
    nine(row(True), "row_done", 3)
    nine(header(), "header", 3)
    for k in ("bg", "fill", "fill_warn", "fill_blue"):
        nine(progress(k), "progress_" + k, 2)
    sprite(slot18(), "slot")
    for st in ("ready", "hover", "locked"):
        sprite(accept_slot(st), "accept_" + st)
    for n in ("clock", "blocks", "heart", "size", "warning", "check", "file", "arrow_left", "arrow_right", "rotate", "chest"):
        sprite(icon(n), "icon_" + n)
    lg = logo()
    p = os.path.join(OUT, "assets", "buildminions", "icon.png"); lg.save(p)
    print("textures written to", A)


if __name__ == "__main__":
    main()
