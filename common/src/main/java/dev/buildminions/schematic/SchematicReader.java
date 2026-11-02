package dev.buildminions.schematic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads the three common schematic formats into a {@link Schematic}:
 * <ul>
 *   <li>Litematica {@code .litematic} (all regions merged)</li>
 *   <li>Sponge / WorldEdit {@code .schem} (v2 and v3)</li>
 *   <li>Vanilla structure block {@code .nbt}</li>
 * </ul>
 */
public final class SchematicReader {
    private SchematicReader() {}

    public static boolean isSupported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".nbt");
    }

    public static Schematic read(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        String name = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        CompoundTag root = readNbt(file);
        Schematic s;
        if (lower.endsWith(".litematic")) s = litematic(root, name);
        else if (lower.endsWith(".schem")) s = sponge(root, name);
        else s = vanilla(root, name);
        if (s.placeableCount() > Schematic.MAX_BLOCKS) throw new IOException("Blueprint has more than " + Schematic.MAX_BLOCKS + " blocks");
        return s;
    }

    /** Accepts gzip (normal), zlib-deflated and uncompressed NBT. */
    private static CompoundTag readNbt(Path file) throws IOException {
        byte[] data = java.nio.file.Files.readAllBytes(file);
        if (data.length < 3) throw new IOException("File is empty");
        NbtAccounter acc = NbtAccounter.create(256L * 1024 * 1024);
        int b0 = data[0] & 0xFF, b1 = data[1] & 0xFF;
        if (b0 == 0x1F && b1 == 0x8B) {
            return NbtIo.readCompressed(new java.io.ByteArrayInputStream(data), acc);
        }
        if (b0 == 0x0A) { // raw NBT starting with a compound tag
            return NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(data)), acc);
        }
        if (b0 == 0x78) { // zlib
            try (var in = new java.io.DataInputStream(new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(data)))) {
                return NbtIo.read(in, acc);
            }
        }
        if (b0 == 'P' && b1 == 'K') throw new IOException("This is a .zip archive – unpack it first and use the file inside");
        if (b0 == '<' || b0 == '{') throw new IOException("This is a web page / text file, not a schematic (download it again)");
        throw new IOException("Unknown file format – not a Litematica, WorldEdit or structure file");
    }

    // ------------------------------------------------------------------ Litematica

    private static Schematic litematic(CompoundTag root, String fallbackName) throws IOException {
        CompoundTag meta = root.getCompoundOrEmpty("Metadata");
        String name = meta.getStringOr("Name", fallbackName);
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.isEmpty()) throw new IOException("No regions in litematic");

        record Region(int minX, int minY, int minZ, int sx, int sy, int sz, List<BlockState> palette, long[] data, int bits) {}
        List<Region> list = new ArrayList<>();
        int gMinX = Integer.MAX_VALUE, gMinY = Integer.MAX_VALUE, gMinZ = Integer.MAX_VALUE;
        int gMaxX = Integer.MIN_VALUE, gMaxY = Integer.MIN_VALUE, gMaxZ = Integer.MIN_VALUE;
        for (String key : regions.keySet()) {
            CompoundTag r = regions.getCompoundOrEmpty(key);
            CompoundTag pos = r.getCompoundOrEmpty("Position");
            CompoundTag size = r.getCompoundOrEmpty("Size");
            int px = pos.getIntOr("x", 0), py = pos.getIntOr("y", 0), pz = pos.getIntOr("z", 0);
            int sx = size.getIntOr("x", 0), sy = size.getIntOr("y", 0), sz = size.getIntOr("z", 0);
            int minX = px + (sx < 0 ? sx + 1 : 0), minY = py + (sy < 0 ? sy + 1 : 0), minZ = pz + (sz < 0 ? sz + 1 : 0);
            sx = Math.abs(sx); sy = Math.abs(sy); sz = Math.abs(sz);
            if (sx == 0 || sy == 0 || sz == 0) continue;
            ListTag pal = r.getListOrEmpty("BlockStatePalette");
            List<BlockState> palette = new ArrayList<>();
            for (int i = 0; i < pal.size(); i++) palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, pal.getCompoundOrEmpty(i)));
            if (palette.isEmpty()) palette.add(Blocks.AIR.defaultBlockState());
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long[] data = r.getLongArray("BlockStates").orElse(new long[0]);
            list.add(new Region(minX, minY, minZ, sx, sy, sz, palette, data, bits));
            gMinX = Math.min(gMinX, minX); gMinY = Math.min(gMinY, minY); gMinZ = Math.min(gMinZ, minZ);
            gMaxX = Math.max(gMaxX, minX + sx); gMaxY = Math.max(gMaxY, minY + sy); gMaxZ = Math.max(gMaxZ, minZ + sz);
        }
        if (list.isEmpty()) throw new IOException("Empty litematic");
        int SX = gMaxX - gMinX, SY = gMaxY - gMinY, SZ = gMaxZ - gMinZ;
        checkSize(SX, SY, SZ);
        Builder b = new Builder(SX, SY, SZ);
        for (Region r : list) {
            long mask = (1L << r.bits) - 1L;
            long volume = (long) r.sx * r.sy * r.sz;
            for (long i = 0; i < volume; i++) {
                int id = (int) readPacked(r.data, i, r.bits, mask);
                if (id <= 0 || id >= r.palette.size()) continue;
                int x = (int) (i % r.sx);
                int z = (int) ((i / r.sx) % r.sz);
                int y = (int) (i / ((long) r.sx * r.sz));
                b.set(r.minX - gMinX + x, r.minY - gMinY + y, r.minZ - gMinZ + z, r.palette.get(id));
            }
        }
        return b.build(name);
    }

    /** Litematica's bit array: entries may span two longs. */
    private static long readPacked(long[] data, long index, int bits, long mask) {
        long start = index * bits;
        int startArr = (int) (start >> 6);
        int endArr = (int) (((index + 1) * bits - 1) >> 6);
        int startBit = (int) (start & 63);
        if (startArr >= data.length) return 0;
        if (startArr == endArr || endArr >= data.length) return (data[startArr] >>> startBit) & mask;
        return ((data[startArr] >>> startBit) | (data[endArr] << (64 - startBit))) & mask;
    }

    // ------------------------------------------------------------------ Sponge (.schem)

    private static Schematic sponge(CompoundTag root, String name) throws IOException {
        CompoundTag s = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
        int version = s.getIntOr("Version", 2);
        int w = s.getShortOr("Width", (short) 0) & 0xFFFF;
        int h = s.getShortOr("Height", (short) 0) & 0xFFFF;
        int l = s.getShortOr("Length", (short) 0) & 0xFFFF;
        checkSize(w, h, l);
        CompoundTag paletteTag;
        byte[] data;
        if (version >= 3) {
            CompoundTag blocks = s.getCompoundOrEmpty("Blocks");
            paletteTag = blocks.getCompoundOrEmpty("Palette");
            data = blocks.getByteArray("Data").orElse(new byte[0]);
        } else {
            paletteTag = s.getCompoundOrEmpty("Palette");
            data = s.getByteArray("BlockData").orElse(new byte[0]);
        }
        Map<Integer, BlockState> palette = new HashMap<>();
        for (String key : paletteTag.keySet()) {
            palette.put(paletteTag.getIntOr(key, 0), parseState(key));
        }
        Builder b = new Builder(w, h, l);
        int idx = 0, i = 0;
        while (i < data.length && idx < w * h * l) {
            int value = 0, shift = 0, cur;
            do {
                cur = data[i++];
                value |= (cur & 0x7F) << shift;
                shift += 7;
            } while ((cur & 0x80) != 0 && i < data.length);
            BlockState st = palette.get(value);
            if (st != null && !st.isAir()) {
                int y = idx / (w * l);
                int z = (idx % (w * l)) / w;
                int x = idx % w;
                b.set(x, y, z, st);
            }
            idx++;
        }
        return b.build(name);
    }

    // ------------------------------------------------------------------ Vanilla structure (.nbt)

    private static Schematic vanilla(CompoundTag root, String name) throws IOException {
        ListTag size = root.getListOrEmpty("size");
        int sx = size.getIntOr(0, 0), sy = size.getIntOr(1, 0), sz = size.getIntOr(2, 0);
        checkSize(sx, sy, sz);
        ListTag pal = root.getListOrEmpty("palette");
        if (pal.isEmpty()) {
            ListTag palettes = root.getListOrEmpty("palettes");
            if (!palettes.isEmpty()) pal = palettes.getListOrEmpty(0);
        }
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < pal.size(); i++) palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK, pal.getCompoundOrEmpty(i)));
        Builder b = new Builder(sx, sy, sz);
        ListTag blocks = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag bt = blocks.getCompoundOrEmpty(i);
            ListTag pos = bt.getListOrEmpty("pos");
            int state = bt.getIntOr("state", 0);
            if (state < 0 || state >= palette.size()) continue;
            b.set(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0), palette.get(state));
        }
        return b.build(name);
    }

    // ------------------------------------------------------------------ helpers

    private static BlockState parseState(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
        } catch (Exception e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    private static void checkSize(int x, int y, int z) throws IOException {
        if (x <= 0 || y <= 0 || z <= 0) throw new IOException("Empty blueprint");
        if (x > Schematic.MAX_DIMENSION || y > Schematic.MAX_DIMENSION || z > Schematic.MAX_DIMENSION)
            throw new IOException("Blueprint is larger than " + Schematic.MAX_DIMENSION + " blocks in one direction");
    }

    private static final class Builder {
        final int sx, sy, sz;
        final int[] blocks;
        final List<BlockState> palette = new ArrayList<>();
        final Map<BlockState, Integer> lookup = new HashMap<>();

        Builder(int sx, int sy, int sz) {
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.blocks = new int[sx * sy * sz];
            palette.add(Blocks.AIR.defaultBlockState());
            lookup.put(Blocks.AIR.defaultBlockState(), 0);
        }

        void set(int x, int y, int z, BlockState s) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz || s.isAir()) return;
            int id = lookup.computeIfAbsent(s, k -> { palette.add(k); return palette.size() - 1; });
            blocks[(y * sz + z) * sx + x] = id;
        }

        Schematic build(String name) {
            return new Schematic(name, sx, sy, sz, palette, blocks);
        }
    }
}
