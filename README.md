# Build Minions (Minecraft Java 26.3, Fabric + NeoForge)

Craft a minion, give it a schematic, deliver the materials, then position the hologram. The minion flies over and builds it.

| Tier | Speed | Blocks before it breaks |
|---|---|---|
| Copper | slow, 0.8 s / block | 600 |
| Gold | fastest, 0.15 s / block | 300 |
| Diamond | fast, 0.3 s / block | 5000 |

The numbers live in `common/.../minion/MinionTier.java`, change them to rebalance.

## How to play
1. **Crafting:** `Minion Core` = 4× amethyst shard + 4× redstone + ender pearl in the middle.
   A minion = core in the middle, the tier's block on top, 6 ingots/diamonds around it (see `data/buildminions/recipe`).
2. Right-click a block with the minion to place it. **Shift + right-click** picks it back up (it keeps its durability).
3. Put `.litematic` (Litematica), `.schem` (WorldEdit/Sponge) or `.nbt` (structure block) files into `.minecraft/schematics`.
4. Right-click the minion and the GUI opens:
   - **Blueprints**: file list with pages, a preview (size, block count, build time), *Load Blueprint*
   - **Materials**: list of required materials (have/need, pages), *Deposit from Inventory*
   - **Overview**: size, time, durability, and the big **ACCEPT BUILD** slot in the middle
5. After accepting, the hologram appears: **R** rotate, **M** mirror, **↑/↓** height, **K** lock, **Enter** build, **Backspace** cancel.
   Red hologram blocks = something is in the way.
6. The minion flies around and places blocks from the bottom up, with a hammer animation. When its durability runs out
   it shakes, sparks and falls apart, and the leftover materials drop on the ground.

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
