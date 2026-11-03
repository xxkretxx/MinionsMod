"""Single source of truth for the Build Minion 3D model.

Coordinates use Minecraft entity-model space: pixels, Y points DOWN, the
entity faces -Z (north), the ground is at y = 24.  Every cube is given in
ABSOLUTE coordinates; pivots are absolute too.  The Java model, the
Blockbench file and the texture painter are all generated from this.
"""

TEX_W, TEX_H = 64, 64

# name, parent, pivot(x,y,z)
PARTS = [
    ("body",      None,       (0, 12, 0)),
    ("backpack",  "body",     (0, 12, 0)),
    ("head",      "body",     (0, 12, 0)),
    ("antenna",   "head",     (0, 2, 0)),
    ("right_arm", "body",     (-5.5, 13, 0)),
    ("hammer",    "right_arm", (-5.5, 18, -1)),
    ("left_arm",  "body",     (5.5, 13, 0)),
    ("right_leg", None,       (-2, 19, 0)),
    ("left_leg",  None,       (2, 19, 0)),
]

# cube: (part, name, from(x,y,z) absolute, size(w,h,d), uv(u,v), mirror)
CUBES = [
    ("body",      "torso",        (-4, 12, -3),     (8, 7, 6),  (0, 20),  False),
    ("backpack",  "pack",         (-3, 13, 3),      (6, 5, 2),  (0, 34),  False),
    ("head",      "skull",        (-5, 2, -5),      (10, 10, 10), (0, 0), False),
    ("head",      "bolt_r",       (-6, 6, -1),      (1, 2, 2),  (16, 34), False),
    ("head",      "bolt_l",       (5, 6, -1),       (1, 2, 2),  (16, 34), True),
    ("antenna",   "stalk",        (-0.5, -1, -0.5), (1, 3, 1),  (40, 20), False),
    ("antenna",   "gem",          (-1, -3, -1),     (2, 2, 2),  (44, 20), False),
    ("right_arm", "arm",          (-7, 12, -1.5),   (3, 7, 3),  (40, 0),  False),
    ("hammer",    "handle",       (-6, 17.5, -8),   (1, 1, 7),  (28, 26), False),
    ("hammer",    "head",         (-6.5, 16, -10),  (2, 4, 3),  (28, 34), False),
    ("left_arm",  "arm",          (4, 12, -1.5),    (3, 7, 3),  (40, 10), False),
    ("right_leg", "leg",          (-3.5, 19, -1.5), (3, 5, 3),  (52, 0),  False),
    ("left_leg",  "leg",          (0.5, 19, -1.5),  (3, 5, 3),  (52, 8),  False),
]

TIERS = {
    "copper": dict(
        base=(200, 111, 74), light=(232, 152, 110), dark=(146, 76, 48), darker=(100, 52, 33),
        patina=(84, 170, 142), patina_l=(118, 200, 170),
        eye=(93, 242, 198), eye_glow=(200, 255, 240), gem=(93, 242, 198), gem_l=(220, 255, 246), gem_d=(30, 150, 120),
        trim=(84, 170, 142)),
    "gold": dict(
        base=(242, 190, 48), light=(255, 228, 112), dark=(196, 140, 26), darker=(140, 94, 16),
        patina=None, patina_l=None,
        eye=(255, 120, 60), eye_glow=(255, 220, 170), gem=(235, 50, 72), gem_l=(255, 150, 160), gem_d=(140, 20, 40),
        trim=(235, 50, 72)),
    "diamond": dict(
        base=(78, 216, 210), light=(170, 255, 247), dark=(36, 160, 160), darker=(22, 112, 116),
        patina=None, patina_l=None,
        eye=(120, 190, 255), eye_glow=(235, 248, 255), gem=(190, 120, 255), gem_l=(240, 215, 255), gem_d=(100, 50, 170),
        trim=(255, 255, 255)),
}
