package dev.buildminions.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Format-independent blueprint: a box of block states. Index 0 of the palette is always air.
 * This is what gets sent from the client to the server and saved inside the minion.
 */
public final class Schematic {
    public static final int MAX_DIMENSION = 256;
    public static final int MAX_BLOCKS = 200_000;

    public final String name;
    public final int sizeX, sizeY, sizeZ;
    public final List<BlockState> palette;
    public final int[] blocks;

    public record Placement(BlockPos pos, BlockState state) {}

    public record Cost(Item item, int count) {}

    public Schematic(String name, int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] blocks) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.blocks = blocks;
    }

    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public BlockState get(int x, int y, int z) {
        return palette.get(blocks[index(x, y, z)]);
    }

    // ------------------------------------------------------------------ stats

    public int placeableCount() {
        int n = 0;
        for (int b : blocks) if (isPlaceable(palette.get(b))) n++;
        return n;
    }

    public int distinctBlockTypes() {
        java.util.HashSet<Block> set = new java.util.HashSet<>();
        for (int b : blocks) {
            BlockState s = palette.get(b);
            if (isPlaceable(s)) set.add(s.getBlock());
        }
        return set.size();
    }

    /** Items needed to build everything, sorted by amount (largest first). */
    public Map<Item, Integer> requirements() {
        int[] perPalette = new int[palette.size()];
        for (int b : blocks) perPalette[b]++;
        Map<Item, Integer> req = new LinkedHashMap<>();
        for (int i = 0; i < palette.size(); i++) {
            if (perPalette[i] == 0) continue;
            Cost c = costOf(palette.get(i));
            if (c != null) req.merge(c.item(), c.count() * perPalette[i], Integer::sum);
        }
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(req.entrySet());
        list.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
        Map<Item, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : list) sorted.put(e.getKey(), e.getValue());
        return sorted;
    }

    /** Blocks that are put into the world (fluids and air are skipped). */
    public static boolean isPlaceable(BlockState s) {
        if (s.isAir()) return false;
        Block b = s.getBlock();
        return !(b instanceof LiquidBlock) && !(b instanceof MovingPistonBlock) && b != Blocks.STRUCTURE_VOID;
    }

    /** Item cost of one block state, or null when it is free (second half of doors/beds, etc.). */
    public static Cost costOf(BlockState s) {
        if (!isPlaceable(s)) return null;
        Block b = s.getBlock();
        if (b instanceof PistonHeadBlock) return null;
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return null;
        if (s.hasProperty(BlockStateProperties.BED_PART) && s.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return null;
        Item item = b.asItem();
        if (item == Items.AIR) return null;
        int count = 1;
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE) && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) count = 2;
        return new Cost(item, count);
    }

    // ------------------------------------------------------------------ placement

    /**
     * All placeable blocks moved to world space. The blueprint is centred on the anchor
     * (bottom-centre of its footprint), then mirrored and rotated around it.
     * Sorted bottom-up so supporting blocks are placed first.
     */
    public List<Placement> transformed(BlockPos anchor, Rotation rotation, Mirror mirror) {
        List<Placement> out = new ArrayList<>();
        int cx = sizeX / 2, cz = sizeZ / 2;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockState s = get(x, y, z);
                    if (!isPlaceable(s)) continue;
                    int rx = x - cx, rz = z - cz;
                    if (mirror == Mirror.FRONT_BACK) rx = -rx;
                    else if (mirror == Mirror.LEFT_RIGHT) rz = -rz;
                    BlockPos rel = new BlockPos(rx, y, rz).rotate(rotation);
                    out.add(new Placement(anchor.offset(rel), s.mirror(mirror).rotate(rotation)));
                }
            }
        }
        out.sort(Comparator.<Placement>comparingInt(p -> p.pos().getY())
                .thenComparingInt(p -> needsSupport(p.state()) ? 1 : 0)
                .thenComparingInt(p -> p.pos().getX())
                .thenComparingInt(p -> (p.pos().getX() & 1) == 0 ? p.pos().getZ() : -p.pos().getZ()));
        return out;
    }

    /** Torches, buttons, carpets... go after the solid blocks of the same layer. */
    private static boolean needsSupport(BlockState s) {
        return !s.isSolid();
    }

    // ------------------------------------------------------------------ NBT

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.put("Size", new IntArrayTag(new int[]{sizeX, sizeY, sizeZ}));
        ListTag pal = new ListTag();
        for (BlockState s : palette) pal.add(NbtUtils.writeBlockState(s));
        tag.put("Palette", pal);
        tag.put("Blocks", new IntArrayTag(blocks));
        return tag;
    }

    public static Schematic load(CompoundTag tag) {
        int[] size = tag.getIntArray("Size").orElse(new int[3]);
        if (size.length != 3) throw new IllegalArgumentException("Bad size");
        for (int s : size) if (s <= 0 || s > MAX_DIMENSION) throw new IllegalArgumentException("Blueprint too large");
        ListTag pal = tag.getListOrEmpty("Palette");
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < pal.size(); i++) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, pal.getCompoundOrEmpty(i)));
        }
        if (palette.isEmpty()) palette.add(Blocks.AIR.defaultBlockState());
        int[] blocks = tag.getIntArray("Blocks").orElse(new int[0]);
        if (blocks.length != size[0] * size[1] * size[2]) throw new IllegalArgumentException("Block data size mismatch");
        for (int b : blocks) if (b < 0 || b >= palette.size()) throw new IllegalArgumentException("Bad palette index");
        return new Schematic(tag.getStringOr("Name", "Blueprint"), size[0], size[1], size[2], palette, blocks);
    }
}
