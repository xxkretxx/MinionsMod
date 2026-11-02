"""Textures, block model, blockstates and data for the Minion Workstation."""
import json, os, random, sys
from PIL import Image, ImageDraw
R = sys.argv[1] if len(sys.argv) > 1 else 'common/src/main/resources'
A = R + '/assets/buildminions/'; D = R + '/data/'
def mk(p): os.makedirs(os.path.dirname(p), exist_ok=True); return p
def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
rng = random.Random(7)
def noise(base, var, w=16, h=16):
    im = Image.new('RGBA', (w, h))
    for y in range(h):
        for x in range(w):
            k = 1 + (rng.random() - .5) * var
            im.putpixel((x, y), tuple(max(0, min(255, int(c * k))) for c in base) + (255,))
    return im
WOOD = (150, 108, 62); WOOD_D = (92, 62, 34); IRON = (120, 124, 134); STONE = (118, 118, 122)
# top: planks + crafting grid (left) + leather mat (right)
top = noise(WOOD, .18)
d = ImageDraw.Draw(top)
for y in (3, 7, 11, 15): d.line((0, y, 15, y), fill=WOOD_D + (255,))
d.rectangle((1, 1, 7, 7), fill=(84, 58, 32, 255))
for gx in range(3):
    for gy in range(3):
        d.rectangle((2 + gx * 2, 2 + gy * 2, 2 + gx * 2, 2 + gy * 2), fill=(176, 132, 80, 255))
d.rectangle((9, 9, 14, 14), fill=(120, 40, 36, 255)); d.rectangle((10, 10, 13, 13), fill=(150, 56, 48, 255))
top.save(mk(A + 'textures/block/workstation_top.png'))
side = noise(WOOD_D, .2)
d = ImageDraw.Draw(side)
d.line((0, 0, 15, 0), fill=WOOD + (255,)); d.line((0, 15, 15, 15), fill=(60, 40, 22, 255))
d.rectangle((5, 6, 10, 9), outline=(60, 40, 22, 255)); d.point((7, 7), fill=(210, 170, 90, 255)); d.point((8, 7), fill=(210, 170, 90, 255))
side.save(mk(A + 'textures/block/workstation_side.png'))
noise((70, 48, 26), .25).save(mk(A + 'textures/block/workstation_leg.png'))
fs = noise(STONE, .3); ImageDraw.Draw(fs).rectangle((0, 0, 15, 15), outline=(80, 80, 84, 255)); fs.save(mk(A + 'textures/block/workstation_furnace_side.png'))
for lit in (False, True):
    f = noise(STONE, .3); d = ImageDraw.Draw(f)
    d.rectangle((0, 0, 15, 15), outline=(80, 80, 84, 255))
    d.rectangle((3, 7, 12, 13), fill=(28, 26, 26, 255))
    if lit:
        for x in range(4, 12):
            hgt = rng.randint(2, 5)
            for y in range(13 - hgt, 13):
                f.putpixel((x, y), (255, 200 - (13 - y) * 30, 40, 255))
    d.rectangle((3, 3, 12, 4), fill=(90, 90, 94, 255))
    f.save(mk(A + f'textures/block/workstation_furnace_front{"_on" if lit else ""}.png'))
an = noise(IRON, .15); ImageDraw.Draw(an).line((0, 0, 15, 0), fill=(170, 174, 184, 255)); an.save(mk(A + 'textures/block/workstation_anvil.png'))

def cube(frm, to, tex, faces=('north', 'south', 'east', 'west', 'up', 'down'), special=None):
    f = {}
    for fc in faces:
        t = (special or {}).get(fc, tex)
        if fc in ('up', 'down'): uv = [frm[0], frm[2], to[0], to[2]]
        elif fc in ('north', 'south'): uv = [frm[0], 16 - to[1], to[0], 16 - frm[1]]
        else: uv = [frm[2], 16 - to[1], to[2], 16 - frm[1]]
        f[fc] = {"texture": t, "uv": uv}
    return {"from": frm, "to": to, "faces": f}
def model(lit):
    els = [
        cube([0, 9, 0], [16, 11, 16], '#side', special={'up': '#top'}),
        cube([1, 0, 1], [3, 9, 3], '#leg'), cube([13, 0, 1], [15, 9, 3], '#leg'),
        cube([1, 0, 13], [3, 9, 15], '#leg'), cube([13, 0, 13], [15, 9, 15], '#leg'),
        cube([2, 3, 2], [14, 4, 14], '#leg'),  # lower shelf
        cube([10, 11, 9], [15, 16, 14], '#fside', special={'north': '#ffront', 'up': '#fside'}),   # mini furnace
        cube([2, 11, 10], [6, 12, 14], '#anvil'), cube([3, 12, 11], [5, 13, 13], '#anvil'),       # mini anvil
        cube([1, 13, 10.5], [7, 15, 13.5], '#anvil'),
        cube([7, 11, 3], [11, 11.6, 3.8], '#leg'), cube([10.4, 11, 2.4], [11.8, 12.4, 4.4], '#anvil'),  # little hammer
    ]
    return {"parent": "minecraft:block/block", "textures": {
        "particle": "buildminions:block/workstation_side", "top": "buildminions:block/workstation_top",
        "side": "buildminions:block/workstation_side", "leg": "buildminions:block/workstation_leg",
        "fside": "buildminions:block/workstation_furnace_side", "anvil": "buildminions:block/workstation_anvil",
        "ffront": "buildminions:block/workstation_furnace_front" + ("_on" if lit else "")}, "elements": els}
json.dump(model(False), open(mk(A + 'models/block/minion_workstation.json'), 'w'), indent=1)
json.dump(model(True), open(mk(A + 'models/block/minion_workstation_on.json'), 'w'), indent=1)
variants = {}
for facing, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    for lit in ('false', 'true'):
        v = {"model": "buildminions:block/minion_workstation" + ("_on" if lit == 'true' else "")}
        if y: v["y"] = y
        variants[f"facing={facing},lit={lit}"] = v
json.dump({"variants": variants}, open(mk(A + 'blockstates/minion_workstation.json'), 'w'), indent=1)
json.dump({"model": {"type": "minecraft:model", "model": "buildminions:block/minion_workstation"}}, open(mk(A + 'items/minion_workstation.json'), 'w'), indent=1)
json.dump({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "buildminions:minion_workstation"}],
           "conditions": [{"condition": "minecraft:survives_explosion"}]}]}, open(mk(D + 'buildminions/loot_table/blocks/minion_workstation.json'), 'w'), indent=1)
json.dump({"values": ["buildminions:minion_workstation"]}, open(mk(D + 'minecraft/tags/block/mineable/axe.json'), 'w'), indent=1)
json.dump({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["III", "TCF", "P P"],
           "key": {"I": "minecraft:iron_ingot", "T": "minecraft:crafting_table", "C": "buildminions:minion_core", "F": "minecraft:furnace", "P": "#minecraft:planks"},
           "result": {"id": "buildminions:minion_workstation"}}, open(mk(D + 'buildminions/recipe/minion_workstation.json'), 'w'), indent=1)
json.dump({"parent": "minecraft:recipes/root", "criteria": {
    "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": "buildminions:minion_core"}]}},
    "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": "buildminions:minion_workstation"}}},
    "requirements": [["has_the_recipe", "has_item"]], "rewards": {"recipes": ["buildminions:minion_workstation"]}},
    open(mk(D + 'buildminions/advancement/recipes/misc/minion_workstation.json'), 'w'), indent=1)
# GUI icons
ic = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(ic)
d.rectangle((1, 4, 14, 7), fill=(150, 156, 168, 255), outline=(40, 42, 50, 255)); d.polygon([(1, 4), (0, 5), (1, 6)], fill=(150, 156, 168, 255))
d.rectangle((5, 8, 10, 10), fill=(110, 114, 124, 255), outline=(40, 42, 50, 255)); d.rectangle((3, 11, 12, 14), fill=(120, 124, 134, 255), outline=(40, 42, 50, 255))
d.line((2, 5, 13, 5), fill=(200, 205, 215, 255))
ic.save(mk(A + 'textures/gui/sprites/icon_anvil.png'))
fl = Image.new('RGBA', (16, 16)); d = ImageDraw.Draw(fl)
d.polygon([(8, 1), (13, 8), (12, 13), (8, 15), (4, 13), (3, 8), (6, 5), (7, 8)], fill=(255, 140, 30, 255), outline=(120, 40, 0, 255))
d.polygon([(8, 7), (11, 11), (8, 14), (5, 11)], fill=(255, 230, 90, 255))
fl.save(mk(A + 'textures/gui/sprites/icon_flame.png'))
p = A + 'lang/en_us.json'; lang = json.load(open(p))
lang.update({
 "block.buildminions.minion_workstation": "Minion Workstation", "item.buildminions.minion_workstation": "Minion Workstation",
 "gui.buildminions.tab.workshop": "Workshop", "gui.buildminions.phase.crafting": "Crafting",
 "gui.buildminions.no_workstation": "Place a Minion Workstation within 8 blocks of the minion. It will craft, smelt and cut missing materials from the raw ones you give it (logs → planks → stairs, cobbled deepslate → deepslate...).",
 "gui.buildminions.workstation_recipe": "Recipe: iron ingots, crafting table, Minion Core, furnace, planks",
 "gui.buildminions.can_craft": "Can make now", "gui.buildminions.nothing_craftable": "Nothing to craft yet. Deposit raw materials (logs, stone, cobbled deepslate...) and they will show up here.",
 "gui.buildminions.queue": "Queue", "gui.buildminions.queue_empty": "Empty", "gui.buildminions.spare": "Raw materials",
 "gui.buildminions.craft_all": "Craft All", "gui.buildminions.clear_queue": "Clear Queue", "gui.buildminions.deposit_raw": "Deposit Items",
 "gui.buildminions.crafting": "Working at the workstation...",
 "message.buildminions.nothing_to_craft": "§7Nothing can be crafted from the materials the minion has."})
json.dump(lang, open(p, 'w'), indent=2, ensure_ascii=False)
print('workstation assets done')
