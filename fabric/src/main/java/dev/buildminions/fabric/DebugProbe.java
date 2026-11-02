package dev.buildminions.fabric;

import java.nio.file.*;
import dev.buildminions.schematic.*;
import net.minecraft.nbt.*;

final class DebugProbe {
    static void run() {
        try (var s = Files.list(Path.of("testschem"))) {
            for (Path p : (Iterable<Path>) s::iterator) {
                try {
                    CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
                    CompoundTag regions = root.getCompoundOrEmpty("Regions");
                    for (String k : regions.keySet()) {
                        CompoundTag r = regions.getCompoundOrEmpty(k);
                        Tag bs = r.get("BlockStates");
                        ListTag pal = r.getListOrEmpty("BlockStatePalette");
                        System.out.println("PROBE " + p + " region=" + k + " BlockStatesType=" + (bs == null ? "null" : bs.getClass().getSimpleName())
                                + " longs=" + r.getLongArray("BlockStates").map(a -> a.length).orElse(-1) + " palette=" + pal.size()
                                + " pal1=" + (pal.size() > 1 ? pal.getCompoundOrEmpty(1) : "") 
                                + " state1=" + (pal.size() > 1 ? NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK, pal.getCompoundOrEmpty(1)) : ""));
                    }
                    Schematic sc = SchematicReader.read(p);
                    System.out.println("PROBE " + p + " size=" + sc.sizeX + "x" + sc.sizeY + "x" + sc.sizeZ + " placeable=" + sc.placeableCount() + " palette=" + sc.palette.size());
                } catch (Throwable t) { System.out.println("PROBE FAIL " + p + " " + t); t.printStackTrace(System.out); }
            }
        } catch (Exception e) { System.out.println("PROBE FAIL list " + e); }
    }
}
