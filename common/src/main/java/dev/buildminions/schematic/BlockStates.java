package dev.buildminions.schematic;

import java.util.Map;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Block state to/from "minecraft:oak_stairs[facing=east,half=top]" strings (robust across versions). */
public final class BlockStates {
    private BlockStates() {}

    public static BlockState parse(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
        } catch (Exception e) {
            int br = s.indexOf('[');
            if (br > 0) { // unknown property – keep the block, drop the bad properties
                try { return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s.substring(0, br), false).blockState(); }
                catch (Exception ignored) {}
            }
            return Blocks.AIR.defaultBlockState();
        }
    }

    public static String toString(BlockState state) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        boolean first = true;
        for (Property<?> p : state.getProperties()) {
            sb.append(first ? '[' : ',');
            sb.append(p.getName()).append('=').append(value(state, p));
            first = false;
        }
        if (!first) sb.append(']');
        return sb.toString();
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }
}
