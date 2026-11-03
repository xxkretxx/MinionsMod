"""Exports the minion as Blockbench .bbmodel files (one per tier) with animations."""
import base64, json, os, sys, uuid
sys.path.insert(0, os.path.dirname(__file__))
from model_def import PARTS, CUBES, TIERS, TEX_W, TEX_H

TEX_DIR, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
U = lambda: str(uuid.uuid4())


def bb_point(p):
    return [-p[0], 24 - p[1], p[2]]


def face_uvs(u, v, w, h, d):
    return {
        "north": [u + d, v + d, u + d + w, v + d + h], "east": [u, v + d, u + d, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "west": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "up": [u + d + w, v + d, u + d, v], "down": [u + d + 2 * w, v, u + d + w, v + d],
    }


def build(tier):
    elements, groups = [], {}
    for name, parent, pivot in PARTS:
        groups[name] = {"name": name, "origin": bb_point(pivot), "rotation": [0, 0, 0], "color": 0, "uuid": U(),
                        "export": True, "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True,
                        "autouv": 0, "children": []}
    for part, cname, frm, size, uv, mirror in CUBES:
        x, y, z = frm; w, h, d = size
        el = {"name": cname, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
              "allow_mirror_modeling": True, "from": [-(x + w), 24 - (y + h), z], "to": [-x, 24 - y, z + d],
              "autouv": 0, "color": 0, "origin": groups[part]["origin"], "uv_offset": list(uv), "mirror_uv": mirror,
              "faces": {k: {"uv": v, "texture": 0} for k, v in face_uvs(uv[0], uv[1], w, h, d).items()},
              "type": "cube", "uuid": U()}
        elements.append(el)
        groups[part]["children"].append(el["uuid"])
    roots = []
    for name, parent, pivot in PARTS:
        (groups[parent]["children"] if parent else roots).append(groups[name])

    def anim(name, length, loop, tracks):
        animators = {}
        for bone, channel, keys in tracks:
            g = groups[bone]
            a = animators.setdefault(g["uuid"], {"name": bone, "type": "bone", "keyframes": []})
            for t, (ax, ay, az) in keys:
                a["keyframes"].append({"channel": channel, "data_points": [{"x": str(ax), "y": str(ay), "z": str(az)}],
                                       "uuid": U(), "time": t, "color": -1, "interpolation": "catmullrom"})
        return {"uuid": U(), "name": name, "loop": loop, "override": False, "length": length, "snapping": 24,
                "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                "animators": animators}

    animations = [
        anim("animation.minion.idle", 2.0, "loop", [
            ("body", "position", [(0, (0, 0, 0)), (1.0, (0, 0.5, 0)), (2.0, (0, 0, 0))]),
            ("antenna", "rotation", [(0, (0, 0, -8)), (1.0, (0, 0, 8)), (2.0, (0, 0, -8))]),
            ("head", "rotation", [(0, (0, -6, 0)), (1.0, (2, 6, 0)), (2.0, (0, -6, 0))]),
        ]),
        anim("animation.minion.walk", 0.6, "loop", [
            ("right_leg", "rotation", [(0, (35, 0, 0)), (0.3, (-35, 0, 0)), (0.6, (35, 0, 0))]),
            ("left_leg", "rotation", [(0, (-35, 0, 0)), (0.3, (35, 0, 0)), (0.6, (-35, 0, 0))]),
            ("left_arm", "rotation", [(0, (30, 0, 0)), (0.3, (-30, 0, 0)), (0.6, (30, 0, 0))]),
            ("body", "position", [(0, (0, 0, 0)), (0.15, (0, 0.6, 0)), (0.3, (0, 0, 0)), (0.45, (0, 0.6, 0)), (0.6, (0, 0, 0))]),
        ]),
        anim("animation.minion.build", 0.5, "loop", [
            ("right_arm", "rotation", [(0, (110, 0, 0)), (0.2, (150, 0, 0)), (0.32, (20, 0, 0)), (0.5, (110, 0, 0))]),
            ("head", "rotation", [(0, (-10, 0, 0)), (0.32, (8, 0, 0)), (0.5, (-10, 0, 0))]),
            ("left_arm", "rotation", [(0, (-15, 0, -10)), (0.5, (-15, 0, -10))]),
        ]),
        anim("animation.minion.break", 1.0, "hold_on_last_frame", [
            ("body", "rotation", [(0, (0, 0, 0)), (0.1, (0, 0, 6)), (0.2, (0, 0, -6)), (0.3, (0, 0, 8)), (0.4, (0, 0, -8)), (1.0, (0, 0, 0))]),
            ("head", "position", [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (1.0, (0, 6, 0))]),
            ("antenna", "position", [(0, (0, 0, 0)), (0.6, (0, 0, 0)), (1.0, (0, 10, 0))]),
        ]),
    ]
    src = open(os.path.join(TEX_DIR, f"minion_{tier}.png"), "rb").read()
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": f"build_minion_{tier}", "model_identifier": "", "modded_entity_entity_class": "MinionModel",
        "modded_entity_version": "Fabric 1.17+", "modded_entity_flip_y": True, "visible_box": [1, 1.7, 0],
        "variable_placeholders": "", "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": TEX_W, "height": TEX_H}, "elements": elements, "outliner": roots,
        "textures": [{"path": "", "name": f"minion_{tier}.png", "folder": "", "namespace": "buildminions", "id": "0",
                      "width": TEX_W, "height": TEX_H, "uv_width": TEX_W, "uv_height": TEX_H, "particle": False,
                      "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                      "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                      "uuid": U(), "source": "data:image/png;base64," + base64.b64encode(src).decode()}],
        "animations": animations,
    }


for tier in TIERS:
    with open(os.path.join(OUT, f"build_minion_{tier}.bbmodel"), "w") as f:
        json.dump(build(tier), f, indent=1)
print("bbmodels ->", OUT)
