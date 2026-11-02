package dev.buildminions.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Draws the translucent hologram of the blueprint while it is being positioned. */
public final class GhostRenderer {
    private GhostRenderer() {}

    public static void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
        if (!PlacementController.active()) return;
        List<PlacementController.Ghost> ghosts = PlacementController.ghosts();
        BlockPos a = PlacementController.anchor();
        int[] b = PlacementController.bounds();
        float pulse = 0.42f + 0.12f * Mth.sin((System.currentTimeMillis() % 100_000L) / 240f);
        int alpha = (int) (pulse * 255);
        boolean locked = PlacementController.locked();

        poseStack.pushPose();
        poseStack.translate(a.getX() - camera.x, a.getY() - camera.y, a.getZ() - camera.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buf) -> {
            for (int i = 0; i < ghosts.size(); i++) {
                PlacementController.Ghost g = ghosts.get(i);
                int color;
                if (PlacementController.conflict(i)) {
                    color = ARGB.color(Math.min(255, alpha + 50), 255, 60, 60);
                } else {
                    // tint the block colour slightly towards blueprint blue
                    int r = ARGB.red(g.argb()), gg = ARGB.green(g.argb()), bl = ARGB.blue(g.argb());
                    color = ARGB.color(alpha, (r * 3 + 90) / 4, (gg * 3 + 170) / 4, (bl * 3 + 255) / 4);
                }
                box(pose, buf, g.x() + 0.03f, g.y() + 0.03f, g.z() + 0.03f, g.x() + 0.97f, g.y() + 0.97f, g.z() + 0.97f, color);
            }
            // bounding frame
            int frame = locked ? ARGB.color(230, 120, 255, 160) : ARGB.color(230, 250, 214, 130);
            frame(pose, buf, b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1, 0.04f, frame);
            // anchor marker
            box(pose, buf, -0.1f, -0.02f, -0.1f, 1.1f, 0.02f, 1.1f, ARGB.color(160, 250, 214, 130));
        });
        poseStack.popPose();
    }

    private static void frame(PoseStack.Pose pose, VertexConsumer buf, float x0, float y0, float z0, float x1, float y1, float z1, float t, int c) {
        // 12 edges as thin boxes
        box(pose, buf, x0 - t, y0 - t, z0 - t, x1 + t, y0 + t, z0 + t, c);
        box(pose, buf, x0 - t, y1 - t, z0 - t, x1 + t, y1 + t, z0 + t, c);
        box(pose, buf, x0 - t, y0 - t, z1 - t, x1 + t, y0 + t, z1 + t, c);
        box(pose, buf, x0 - t, y1 - t, z1 - t, x1 + t, y1 + t, z1 + t, c);
        box(pose, buf, x0 - t, y0 - t, z0 - t, x0 + t, y1 + t, z0 + t, c);
        box(pose, buf, x1 - t, y0 - t, z0 - t, x1 + t, y1 + t, z0 + t, c);
        box(pose, buf, x0 - t, y0 - t, z1 - t, x0 + t, y1 + t, z1 + t, c);
        box(pose, buf, x1 - t, y0 - t, z1 - t, x1 + t, y1 + t, z1 + t, c);
        box(pose, buf, x0 - t, y0 - t, z0 - t, x0 + t, y0 + t, z1 + t, c);
        box(pose, buf, x1 - t, y0 - t, z0 - t, x1 + t, y0 + t, z1 + t, c);
        box(pose, buf, x0 - t, y1 - t, z0 - t, x0 + t, y1 + t, z1 + t, c);
        box(pose, buf, x1 - t, y1 - t, z0 - t, x1 + t, y1 + t, z1 + t, c);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer b, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        // north
        b.addVertex(pose, x0, y0, z0).setColor(c); b.addVertex(pose, x1, y0, z0).setColor(c);
        b.addVertex(pose, x1, y1, z0).setColor(c); b.addVertex(pose, x0, y1, z0).setColor(c);
        // south
        b.addVertex(pose, x1, y0, z1).setColor(c); b.addVertex(pose, x0, y0, z1).setColor(c);
        b.addVertex(pose, x0, y1, z1).setColor(c); b.addVertex(pose, x1, y1, z1).setColor(c);
        // west
        b.addVertex(pose, x0, y0, z1).setColor(c); b.addVertex(pose, x0, y0, z0).setColor(c);
        b.addVertex(pose, x0, y1, z0).setColor(c); b.addVertex(pose, x0, y1, z1).setColor(c);
        // east
        b.addVertex(pose, x1, y0, z0).setColor(c); b.addVertex(pose, x1, y0, z1).setColor(c);
        b.addVertex(pose, x1, y1, z1).setColor(c); b.addVertex(pose, x1, y1, z0).setColor(c);
        // up
        b.addVertex(pose, x0, y1, z0).setColor(c); b.addVertex(pose, x1, y1, z0).setColor(c);
        b.addVertex(pose, x1, y1, z1).setColor(c); b.addVertex(pose, x0, y1, z1).setColor(c);
        // down
        b.addVertex(pose, x0, y0, z1).setColor(c); b.addVertex(pose, x1, y0, z1).setColor(c);
        b.addVertex(pose, x1, y0, z0).setColor(c); b.addVertex(pose, x0, y0, z0).setColor(c);
    }
}
