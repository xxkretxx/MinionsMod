package dev.buildminions.minion;

import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The three minion tiers.
 * <ul>
 *   <li>Copper  – slow (0.8 s / block), breaks after 600 blocks.</li>
 *   <li>Gold    – very fast (0.15 s / block), but fragile: breaks after 300 blocks.</li>
 *   <li>Diamond – fast (0.3 s / block, a bit slower than gold), lasts 5000 blocks.</li>
 * </ul>
 */
public enum MinionTier {
    COPPER("copper", 16, 600, 0.20, 0xFFE5946B, ChatFormatting.GOLD),
    GOLD("gold", 3, 300, 0.55, 0xFFFFE16A, ChatFormatting.YELLOW),
    DIAMOND("diamond", 6, 5000, 0.40, 0xFF8CF5EE, ChatFormatting.AQUA);

    public final String id;
    /** Ticks between two placed blocks. */
    public final int ticksPerBlock;
    /** How many blocks the minion can place before it falls apart. */
    public final int maxBlocks;
    /** Flight speed in blocks per tick. */
    public final double flySpeed;
    /** ARGB accent colour used by the GUI. */
    public final int color;
    public final ChatFormatting chat;

    MinionTier(String id, int ticksPerBlock, int maxBlocks, double flySpeed, int color, ChatFormatting chat) {
        this.id = id;
        this.ticksPerBlock = ticksPerBlock;
        this.maxBlocks = maxBlocks;
        this.flySpeed = flySpeed;
        this.color = color;
        this.chat = chat;
    }

    public String itemName() {
        return id + "_minion";
    }

    public String translationKey() {
        return "entity.buildminions.minion." + id;
    }

    public Block particleBlock() {
        return switch (this) {
            case COPPER -> Blocks.COPPER_BLOCK.asList().getFirst();
            case GOLD -> Blocks.GOLD_BLOCK;
            case DIAMOND -> Blocks.DIAMOND_BLOCK;
        };
    }

    /** Rough build time in seconds, including a little travel overhead. */
    public int estimateSeconds(int blocks) {
        return (int) Math.ceil(blocks * ticksPerBlock * 1.1 / 20.0);
    }

    public static MinionTier byId(int ordinal) {
        MinionTier[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, ordinal))];
    }
}
