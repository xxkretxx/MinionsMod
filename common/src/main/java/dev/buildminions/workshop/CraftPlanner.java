package dev.buildminions.workshop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * Works out how to turn the raw materials a minion holds (logs, cobbled deepslate, ...) into the blocks a
 * blueprint needs (planks, stairs, fences, deepslate...). Supports crafting, smelting and stonecutting,
 * chained up to a few steps deep (log -> planks -> stairs).
 */
public final class CraftPlanner {
    public enum Kind { CRAFT, SMELT, CUT }

    /** One way of making an item. {@code ingredients} holds every slot (so 3 planks = 3 entries). */
    public record Option(ResourceKey<Recipe<?>> id, Kind kind, Item result, int resultCount, List<Ingredient> ingredients) {}

    /** A queued step: run {@code option} {@code times} times. */
    public record Job(Option option, int times) {}

    private static final int MAX_DEPTH = 4;
    private static RecipeManager cachedManager;
    private static Map<Item, List<Option>> cachedIndex = Map.of();
    private static Map<ResourceKey<Recipe<?>>, Option> cachedById = Map.of();

    private CraftPlanner() {}

    // ------------------------------------------------------------------ recipe index

    public static synchronized Map<Item, List<Option>> index(MinecraftServer server) {
        RecipeManager rm = server.getRecipeManager();
        if (rm == cachedManager) return cachedIndex;
        Map<Item, List<Option>> idx = new HashMap<>();
        Map<ResourceKey<Recipe<?>>, Option> byId = new HashMap<>();
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            try {
                Option o = toOption(holder, server.registryAccess());
                if (o == null) continue;
                idx.computeIfAbsent(o.result(), k -> new ArrayList<>()).add(o);
                byId.put(o.id(), o);
            } catch (Exception ignored) {
                // recipes from other mods with unusual inputs are simply skipped
            }
        }
        // prefer recipes with fewer ingredients per item (e.g. 1 log -> 4 planks over anything exotic)
        for (List<Option> list : idx.values())
            list.sort((a, b) -> Double.compare(a.ingredients().size() / (double) a.resultCount(), b.ingredients().size() / (double) b.resultCount()));
        cachedManager = rm;
        cachedIndex = idx;
        cachedById = byId;
        return idx;
    }

    public static Option byId(MinecraftServer server, ResourceKey<Recipe<?>> id) {
        index(server);
        return cachedById.get(id);
    }

    private static ItemStack sample(Ingredient ing) {
        return ing.items().findFirst().map(h -> new ItemStack(h)).orElse(ItemStack.EMPTY);
    }

    private static Option toOption(RecipeHolder<?> holder, net.minecraft.core.HolderLookup.Provider regs) {
        Recipe<?> r = holder.value();
        if (r.isSpecial()) return null;
        ItemStack result;
        Kind kind;
        List<Ingredient> ings = new ArrayList<>();
        if (r instanceof ShapedRecipe shaped) {
            List<ItemStack> grid = new ArrayList<>();
            for (var opt : shaped.getIngredients()) {
                grid.add(opt.map(CraftPlanner::sample).orElse(ItemStack.EMPTY));
                opt.ifPresent(ings::add);
            }
            result = shaped.assemble(CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid), regs);
            kind = Kind.CRAFT;
        } else if (r instanceof ShapelessRecipe shapeless) {
            List<Ingredient> list = shapeless.placementInfo().ingredients();
            if (list.isEmpty() || list.size() > 9) return null;
            List<ItemStack> grid = new ArrayList<>();
            for (Ingredient i : list) { grid.add(sample(i)); ings.add(i); }
            int w = list.size() <= 4 ? 2 : 3;
            while (grid.size() < w * w) grid.add(ItemStack.EMPTY);
            result = shapeless.assemble(CraftingInput.of(w, w, grid), regs);
            kind = Kind.CRAFT;
        } else if (r instanceof AbstractCookingRecipe cooking && cooking.getType() == RecipeType.SMELTING) {
            ings.add(cooking.input());
            result = cooking.assemble(new SingleRecipeInput(sample(cooking.input())), regs);
            kind = Kind.SMELT;
        } else if (r instanceof StonecutterRecipe cut) {
            ings.add(cut.input());
            result = cut.assemble(new SingleRecipeInput(sample(cut.input())), regs);
            kind = Kind.CUT;
        } else {
            return null;
        }
        if (result == null || result.isEmpty() || ings.isEmpty()) return null;
        if (!result.getComponentsPatch().isEmpty()) return null; // named / special items
        return new Option(holder.id(), kind, result.getItem(), result.getCount(), ings);
    }

    // ------------------------------------------------------------------ planning

    /** Result of planning: the jobs to run and the raw items that will be consumed. */
    public record Plan(List<Job> jobs, Map<Item, Integer> consumed, Map<Item, Integer> produced) {
        public boolean isEmpty() { return jobs.isEmpty(); }
    }

    /**
     * Plans how to cover {@code missing} using {@code available} raw items. Items that cannot be made are skipped.
     * {@code available} is not modified.
     */
    public static Plan plan(MinecraftServer server, Map<Item, Integer> missing, Map<Item, Integer> available) {
        Map<Item, List<Option>> idx = index(server);
        Map<Item, Integer> avail = new HashMap<>(available);
        List<Job> jobs = new ArrayList<>();
        Map<Item, Integer> produced = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : missing.entrySet()) {
            if (e.getValue() <= 0) continue;
            Map<Item, Integer> snapAvail = new HashMap<>(avail);
            int snapJobs = jobs.size();
            if (make(idx, e.getKey(), e.getValue(), avail, jobs, 0, new IdentityHashMap<>())) {
                produced.merge(e.getKey(), e.getValue(), Integer::sum);
                // the produced items are "reserved" for the blueprint, not available for other recipes
                avail.merge(e.getKey(), -e.getValue(), Integer::sum);
            } else {
                avail.clear(); avail.putAll(snapAvail);
                while (jobs.size() > snapJobs) jobs.remove(jobs.size() - 1);
            }
        }
        Map<Item, Integer> consumed = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : available.entrySet()) {
            int left = Math.max(0, avail.getOrDefault(e.getKey(), 0));
            int used = e.getValue() - left;
            if (used > 0) consumed.put(e.getKey(), used);
        }
        return new Plan(mergeJobs(jobs), consumed, produced);
    }

    /** Produces {@code count} of {@code item} (crafting only what is not already available). */
    private static boolean make(Map<Item, List<Option>> idx, Item item, int count, Map<Item, Integer> avail,
                                List<Job> jobs, int depth, Map<Item, Boolean> visiting) {
        int have = Math.max(0, avail.getOrDefault(item, 0));
        int fromStock = Math.min(have, count);
        if (fromStock > 0) avail.put(item, have - fromStock);
        int need = count - fromStock;
        if (need == 0) return true;
        if (depth >= MAX_DEPTH || visiting.containsKey(item)) return false;
        List<Option> options = idx.getOrDefault(item, List.of());
        visiting.put(item, true);
        try {
            for (Option o : options) {
                int times = (need + o.resultCount() - 1) / o.resultCount();
                Map<Item, Integer> snapAvail = new HashMap<>(avail);
                int snapJobs = jobs.size();
                if (satisfy(idx, o, times, avail, jobs, depth, visiting)) {
                    jobs.add(new Job(o, times));
                    int leftover = times * o.resultCount() - need;
                    if (leftover > 0) avail.merge(item, leftover, Integer::sum);
                    return true;
                }
                avail.clear(); avail.putAll(snapAvail);
                while (jobs.size() > snapJobs) jobs.remove(jobs.size() - 1);
            }
        } finally {
            visiting.remove(item);
        }
        if (fromStock > 0) avail.merge(item, fromStock, Integer::sum);
        return false;
    }

    private static boolean satisfy(Map<Item, List<Option>> idx, Option o, int times, Map<Item, Integer> avail,
                                   List<Job> jobs, int depth, Map<Item, Boolean> visiting) {
        // group identical slots ("3x any planks")
        Map<Ingredient, Integer> grouped = new LinkedHashMap<>();
        for (Ingredient i : o.ingredients()) grouped.merge(i, 1, Integer::sum);
        for (Map.Entry<Ingredient, Integer> g : grouped.entrySet()) {
            int amount = g.getValue() * times;
            if (!satisfyIngredient(idx, g.getKey(), amount, avail, jobs, depth, visiting)) return false;
        }
        return true;
    }

    private static boolean satisfyIngredient(Map<Item, List<Option>> idx, Ingredient ing, int amount, Map<Item, Integer> avail,
                                             List<Job> jobs, int depth, Map<Item, Boolean> visiting) {
        List<Item> variants = ing.items().map(Holder::value).toList();
        // 1) take straight from stock, spreading over variants (oak + spruce planks both count as "planks")
        int left = amount;
        for (Item v : variants) {
            int have = Math.max(0, avail.getOrDefault(v, 0));
            int take = Math.min(have, left);
            if (take > 0) { avail.put(v, have - take); left -= take; }
            if (left == 0) return true;
        }
        // 2) craft the rest from the variant we have the most raw material for
        for (Item v : variants) {
            if (v == Items.AIR) continue;
            Map<Item, Integer> snapAvail = new HashMap<>(avail);
            int snapJobs = jobs.size();
            if (make(idx, v, left, avail, jobs, depth + 1, visiting)) return true;
            avail.clear(); avail.putAll(snapAvail);
            while (jobs.size() > snapJobs) jobs.remove(jobs.size() - 1);
            if (variants.size() > 6 && v == variants.get(5)) break; // big tags: don't try everything
        }
        return false;
    }

    private static List<Job> mergeJobs(List<Job> jobs) {
        Map<ResourceKey<Recipe<?>>, Integer> order = new LinkedHashMap<>();
        Map<ResourceKey<Recipe<?>>, Option> opts = new HashMap<>();
        for (Job j : jobs) { order.merge(j.option().id(), j.times(), Integer::sum); opts.put(j.option().id(), j.option()); }
        List<Job> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<Recipe<?>>, Integer> e : order.entrySet()) out.add(new Job(opts.get(e.getKey()), e.getValue()));
        return out;
    }

    /** Takes the ingredients for one run of {@code o} out of {@code stored}. Returns false (and changes nothing) if missing. */
    public static boolean consumeOnce(Option o, Map<Item, Integer> stored) {
        Map<Item, Integer> copy = new HashMap<>(stored);
        for (Ingredient ing : o.ingredients()) {
            boolean ok = false;
            for (Holder<Item> h : (Iterable<Holder<Item>>) ing.items()::iterator) {
                int have = copy.getOrDefault(h.value(), 0);
                if (have > 0) { copy.put(h.value(), have - 1); ok = true; break; }
            }
            if (!ok) return false;
        }
        stored.clear();
        copy.forEach((k, v) -> { if (v > 0) stored.put(k, v); });
        return true;
    }
}
