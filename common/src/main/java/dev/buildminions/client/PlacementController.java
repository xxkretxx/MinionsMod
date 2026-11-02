package dev.buildminions.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import dev.buildminions.schematic.Schematic;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Litematica-style hologram placement. The hologram follows the crosshair; R rotates, M mirrors,
 * arrows move it up/down, K locks it in place, Enter starts the build, Backspace cancels.
 */
public final class PlacementController {
    /** One ghost block, relative to the anchor. */
    public record Ghost(int x, int y, int z, int argb) {}

    public static final int MAX_RENDERED = 40_000;

    private static int entityId = -1;
    private static Schematic schematic;
    private static Rotation rotation = Rotation.NONE;
    private static Mirror mirror = Mirror.NONE;
    private static int yOffset;
    private static BlockPos locked;
    private static BlockPos anchor = BlockPos.ZERO;

    private static List<Schematic.Placement> relative = List.of();
    private static List<Ghost> surface = List.of();
    private static boolean[] conflicts = new boolean[0];
    private static int conflictCount;
    private static int minX, minY, minZ, maxX, maxY, maxZ;
    private static int conflictTimer;
    private static BlockPos conflictAnchor;

    private PlacementController() {}

    public static boolean active() { return schematic != null; }
    public static Schematic schematic() { return schematic; }
    public static BlockPos anchor() { return anchor; }
    public static Rotation rotation() { return rotation; }
    public static Mirror mirror() { return mirror; }
    public static boolean locked() { return locked != null; }
    public static List<Ghost> ghosts() { return surface; }
    public static boolean conflict(int i) { return i < conflicts.length && conflicts[i]; }
    public static int conflictCount() { return conflictCount; }
    public static int[] bounds() { return new int[]{minX, minY, minZ, maxX, maxY, maxZ}; }

    public static boolean start(int id) {
        Schematic s = ClientState.BLUEPRINTS.get(id);
        if (s == null) return false;
        entityId = id;
        schematic = s;
        rotation = Rotation.NONE;
        mirror = Mirror.NONE;
        yOffset = 0;
        locked = null;
        rebuild();
        return true;
    }

    public static void stop() {
        schematic = null;
        entityId = -1;
        surface = List.of();
        relative = List.of();
    }

    private static void rebuild() {
        relative = schematic.transformed(BlockPos.ZERO, rotation, mirror);
        Set<Long> occupied = new HashSet<>(relative.size() * 2);
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        for (Schematic.Placement p : relative) {
            occupied.add(p.pos().asLong());
            minX = Math.min(minX, p.pos().getX()); minY = Math.min(minY, p.pos().getY()); minZ = Math.min(minZ, p.pos().getZ());
            maxX = Math.max(maxX, p.pos().getX()); maxY = Math.max(maxY, p.pos().getY()); maxZ = Math.max(maxZ, p.pos().getZ());
        }
        List<Ghost> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        for (Schematic.Placement p : relative) {
            boolean hidden = true;
            for (Direction d : Direction.values()) {
                if (!occupied.contains(p.pos().relative(d).asLong())) { hidden = false; break; }
            }
            if (hidden) continue;
            list.add(new Ghost(p.pos().getX(), p.pos().getY(), p.pos().getZ(), colorOf(mc, p.state())));
            if (list.size() >= MAX_RENDERED) break;
        }
        surface = list;
        conflicts = new boolean[list.size()];
        conflictAnchor = null;
    }

    private static int colorOf(Minecraft mc, BlockState state) {
        int rgb = 0x9FD8FF;
        try {
            MapColor mc2 = state.getMapColor(mc.level, BlockPos.ZERO);
            if (mc2 != null && mc2.col != 0) rgb = mc2.col;
        } catch (Exception ignored) {}
        return 0xFF000000 | rgb;
    }

    public static void tick(Minecraft mc) {
        if (!active()) {
            for (KeyMapping k : ModKeys.ALL) while (k.consumeClick()) { /* drain */ }
            return;
        }
        if (mc.player == null || mc.level == null) { stop(); return; }
        boolean changed = false;
        while (ModKeys.ROTATE.consumeClick()) { rotation = rotation.getRotated(Rotation.CLOCKWISE_90); changed = true; click(mc); }
        while (ModKeys.MIRROR.consumeClick()) {
            mirror = switch (mirror) { case NONE -> Mirror.LEFT_RIGHT; case LEFT_RIGHT -> Mirror.FRONT_BACK; case FRONT_BACK -> Mirror.NONE; };
            changed = true; click(mc);
        }
        while (ModKeys.RAISE.consumeClick()) { yOffset++; if (locked != null) locked = locked.above(); click(mc); }
        while (ModKeys.LOWER.consumeClick()) { yOffset--; if (locked != null) locked = locked.below(); click(mc); }
        while (ModKeys.LOCK.consumeClick()) { locked = locked == null ? anchor : null; click(mc); }
        if (changed) rebuild();

        if (locked != null) {
            anchor = locked;
        } else {
            HitResult hr = mc.hitResult;
            BlockPos base;
            if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK) {
                base = bhr.getBlockPos().relative(bhr.getDirection());
            } else {
                Vec3 eye = mc.player.getEyePosition();
                base = BlockPos.containing(eye.add(mc.player.getLookAngle().scale(12)));
            }
            anchor = base.above(yOffset);
        }

        // check for blocks in the way (red ghosts), spread over ticks
        if (--conflictTimer <= 0 || !anchor.equals(conflictAnchor)) {
            conflictTimer = 10;
            conflictAnchor = anchor;
            int count = 0;
            for (int i = 0; i < surface.size(); i++) {
                Ghost g = surface.get(i);
                BlockState s = mc.level.getBlockState(anchor.offset(g.x(), g.y(), g.z()));
                conflicts[i] = !s.isAir() && !s.canBeReplaced();
                if (conflicts[i]) count++;
            }
            conflictCount = count;
        }

        while (ModKeys.CONFIRM.consumeClick()) {
            Services.toServer.accept(new ModNetwork.Place(entityId, anchor, rotation.ordinal(), mirror.ordinal()));
            mc.player.playSound(SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.4f);
            stop();
            return;
        }
        while (ModKeys.CANCEL.consumeClick()) {
            Services.toServer.accept(new ModNetwork.Action(entityId, ModNetwork.Action.CANCEL));
            stop();
            return;
        }
    }

    private static void click(Minecraft mc) {
        if (mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.6f);
    }
}
