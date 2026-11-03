package dev.buildminions.fabric;

import java.util.*;
import dev.buildminions.workshop.CraftPlanner;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;

final class DebugProbe {
    static void run(MinecraftServer server) {
        try {
            var idx = CraftPlanner.index(server);
            System.out.println("PROBE recipes indexed for " + idx.size() + " items");
            test(server, Map.of(Items.DARK_OAK_STAIRS, 12, Items.DARK_OAK_FENCE, 6, Items.DARK_OAK_PLANKS, 10), Map.of(Items.DARK_OAK_LOG, 16));
            test(server, Map.of(Items.DEEPSLATE, 8, Items.POLISHED_DEEPSLATE_STAIRS, 4), Map.of(Items.COBBLED_DEEPSLATE, 20));
            test(server, Map.of(Items.STONE_BRICK_STAIRS, 8, Items.GLASS_PANE, 16, Items.CHAIN, 2), Map.of(Items.COBBLESTONE, 20, Items.SAND, 8, Items.IRON_INGOT, 5));
            test(server, Map.of(Items.DIAMOND_BLOCK, 1), Map.of(Items.DIRT, 64));
        } catch (Throwable t) { System.out.println("PROBE FAIL " + t); t.printStackTrace(System.out); }
    }
    static void test(MinecraftServer s, Map<Item, Integer> missing, Map<Item, Integer> avail) {
        var p = CraftPlanner.plan(s, new LinkedHashMap<>(missing), avail);
        StringBuilder sb = new StringBuilder("PROBE plan missing=" + missing + " avail=" + avail + "\n");
        for (var j : p.jobs()) sb.append("PROBE   job ").append(j.option().kind()).append(" ").append(j.option().id()).append(" x").append(j.times()).append("\n");
        sb.append("PROBE   produced=").append(p.produced()).append(" consumed=").append(p.consumed());
        System.out.println(sb);
        // simulate execution
        Map<Item, Integer> stored = new HashMap<>(avail);
        for (var j : p.jobs()) for (int i = 0; i < j.times(); i++) {
            if (!CraftPlanner.consumeOnce(j.option(), stored)) { System.out.println("PROBE   EXEC FAIL at " + j.option().id()); return; }
            stored.merge(j.option().result(), j.option().resultCount(), Integer::sum);
        }
        boolean ok = true;
        for (var e : p.produced().entrySet()) if (stored.getOrDefault(e.getKey(), 0) < e.getValue()) ok = false;
        System.out.println("PROBE   exec ok=" + ok + " final=" + stored);
    }
}
