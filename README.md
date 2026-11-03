# Build Minions (Minecraft Java 26.3, Fabric + NeoForge)

Craft a minion, give it a schematic, deliver the materials, then position the hologram. The minion flies over and builds it.

| Tier | Speed | Blocks before it breaks |
|---|---|---|
| Copper | slow, 0.8 s / block | 600 |
| Gold | fastest, 0.15 s / block | 300 |
| Diamond | fast, 0.3 s / block | 5000 |

The numbers live in `common/.../minion/MinionTier.java`, change them to rebalance.

## Crafting
`A` = amethyst shard, `R` = redstone, `E` = ender pearl, `C` = Minion Core

**Minion Core**
```
A R A
R E R
A R A
```
**Copper / Gold / Diamond Minion** (`i` = copper ingot / gold ingot / diamond, `B` = block of copper / gold / diamond)
```
i B i
i C i
i . i
```
The recipes unlock in the recipe book once you have redstone (core) or a Minion Core (minions).
They are also in the mod's creative tab.

## Upgrades
Right-click a minion with:
- **Golden Apple**: durability x2 (Copper 1,200 · Gold 600 · Diamond **10,000**)
- **Enchanted Golden Apple**: durability x4 (Copper 2,400 · Gold 1,200 · Diamond **20,000**)

The upgrade stays when you pick the minion up. Upgraded minions show ★ / ★★ in the GUI and give off sparkles.

## Workshop (auto-crafting)
Craft a **Minion Workstation** (iron ingots, crafting table, Minion Core, furnace, planks) and place it within 8 blocks of a minion.
The minion can then turn raw materials into the blocks a blueprint needs:
- logs → planks → stairs / slabs / fences / doors
- cobbled deepslate → deepslate (smelting), cobblestone → stone → stone bricks
- stonecutter recipes (stone → stairs, slabs, walls...)

In the **Workshop** tab: *Deposit Items* also takes the raw materials that can be crafted into missing blocks,
*Craft All* puts everything into a queue, and the minion works through it at the workstation (hammer, little furnace,
anvil sounds). Each step takes time, faster for better tiers.

## How to play
1. Right-click a block with the minion to place it. **Shift + right-click** picks it back up (it keeps its durability).
2. Put `.litematic` (Litematica), `.schem` (WorldEdit/Sponge) or `.nbt` (structure block) files into `.minecraft/schematics`.
   Compressed and uncompressed files both work.
3. Right-click the minion and the GUI opens:
   - **Blueprints**: file list with pages, a preview (size, block count, build time), *Load Blueprint*
   - **Materials**: required materials (have/need, pages), *Deposit from Inventory*
   - **Overview**: size, time, durability and the big **ACCEPT BUILD** slot in the middle
4. After accepting, the hologram appears (it works with shader packs too): **R** rotate, **M** mirror, **↑/↓** height,
   **K** lock, **Enter** build, **Backspace** cancel. Red blocks = something is in the way.
5. The minion flies around and builds from the bottom up. When its durability runs out it falls apart and drops the leftover materials.

In creative mode materials are not required.

## Building the jar
Requires **JDK 25**.
```
./gradlew build
```
The jars end up in `fabric/build/libs/` and `neoforge/build/libs/`.
You can also push the project to GitHub. The `.github/workflows/build.yml` workflow builds both jars on its own (Actions → artifacts).

## Status
The project **compiles** on Fabric and NeoForge 26.3 (GitHub Actions builds both jars on every push:
Actions tab -> latest run -> `build-minions-jars`). It has **not been play-tested in-game yet**, so runtime bugs
(rendering, GUI layout, balance) may still show up. The NeoForge hologram hook (`SubmitCustomGeometryEvent`) is the
part most worth checking in game.

## Compatibility (honestly)
- **Fabric 26.3**: yes (also runs on **Quilt**, which loads Fabric mods).
- **NeoForge 26.3**: yes.
- **Forge (MinecraftForge)**: there is no Forge for 26.x, so you can't build for it.
- **Older versions (1.21.x, 1.20.x…)**: the rendering, GUI and save-data APIs changed completely between versions,
  so each older version needs a separate port (a separate branch). The `common` code (schematic logic, entity, network packets)
  is written so that a port only touches the rendering and GUI layer.
- It's not a purely client-side mod: the minion places blocks in the world, so it has to be on the server too.
  In singleplayer that's automatic. On a multiplayer server it must be installed on the server.

## Project layout
```
common/      shared code + assets (textures, GUI, lang, recipes)
fabric/      Fabric entrypoints
neoforge/    NeoForge entrypoints
model/       Blockbench models (.bbmodel with animations) + .glb
tools/       generators for the textures, model and previews (Python + three.js)
```
