package dev.buildminions.forge;

import java.util.List;

import dev.buildminions.client.PlacementController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;

/**
 * Forge 1.21.11 has no level-render hook, so the hologram is drawn with particles there:
 * a golden frame around the blueprint, red dots where something is in the way, and the anchor.
 */
final class GhostParticles {
    private static final DustParticleOptions FRAME = new DustParticleOptions(0xFAD682, 0.9f);
    private static final DustParticleOptions LOCKED = new DustParticleOptions(0x78FFA0, 0.9f);
    private static final DustParticleOptions BLOCKED = new DustParticleOptions(0xFF4646, 1.0f);
    private static final DustParticleOptions GHOST = new DustParticleOptions(0x6FA8FF, 0.6f);
    private static int tick;

    private GhostParticles() {}

    static void draw(Minecraft mc) {
        if (!PlacementController.active() || mc.level == null || ++tick % 3 != 0) return;
        BlockPos a = PlacementController.anchor();
        int[] b = PlacementController.bounds();
        double x0 = a.getX() + b[0], y0 = a.getY() + b[1], z0 = a.getZ() + b[2];
        double x1 = a.getX() + b[3] + 1, y1 = a.getY() + b[4] + 1, z1 = a.getZ() + b[5] + 1;
        DustParticleOptions frame = PlacementController.locked() ? LOCKED : FRAME;
        double step = Math.max(0.5, Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0)) / 40.0);
        for (double t = 0; t <= 1.0001; t += step / Math.max(1, x1 - x0)) {
            double x = x0 + (x1 - x0) * t;
            p(mc, frame, x, y0, z0); p(mc, frame, x, y1, z0); p(mc, frame, x, y0, z1); p(mc, frame, x, y1, z1);
        }
        for (double t = 0; t <= 1.0001; t += step / Math.max(1, y1 - y0)) {
            double y = y0 + (y1 - y0) * t;
            p(mc, frame, x0, y, z0); p(mc, frame, x1, y, z0); p(mc, frame, x0, y, z1); p(mc, frame, x1, y, z1);
        }
        for (double t = 0; t <= 1.0001; t += step / Math.max(1, z1 - z0)) {
            double z = z0 + (z1 - z0) * t;
            p(mc, frame, x0, y0, z); p(mc, frame, x1, y0, z); p(mc, frame, x0, y1, z); p(mc, frame, x1, y1, z);
        }
        List<PlacementController.Ghost> ghosts = PlacementController.ghosts();
        int shown = 0;
        for (int i = 0; i < ghosts.size() && shown < 600; i++) {
            PlacementController.Ghost g = ghosts.get(i);
            boolean blocked = PlacementController.conflict(i);
            if (!blocked && (i + tick / 3) % 6 != 0) continue; // sample the hologram surface
            p(mc, blocked ? BLOCKED : GHOST, a.getX() + g.x() + 0.5, a.getY() + g.y() + 0.5, a.getZ() + g.z() + 0.5);
            shown++;
        }
    }

    private static void p(Minecraft mc, DustParticleOptions o, double x, double y, double z) {
        mc.level.addParticle(o, x, y, z, 0, 0, 0);
    }
}
