package dev.buildminions.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.buildminions.BuildMinions;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the translucent hologram of the blueprint while it is being positioned.
 * Uses the entity-translucent render type (with a small outlined texture) instead of debug geometry,
 * because shader packs (Iris / Oculus) render entity geometry but drop debug render types.
 */
public final class GhostRenderer {
    private static final ResourceLocation TEXTURE = BuildMinions.id("textures/misc/ghost.png");
    private static final float C = 0.5f; // texture centre = solid colour (used for frame edges)

    private GhostRenderer() {}

    public static void render(PoseStack poseStack, SubmitNodeCollector collector, Vec3 camera) {
        if (!PlacementController.active()) return;
        List<PlacementController.Ghost> ghosts = PlacementController.ghosts();
        BlockPos a = PlacementController.anchor();
        int[] b = PlacementController.bounds();
        float pulse = 0.55f + 0.15f * Mth.sin((System.currentTimeMillis() % 100_000L) / 240f);
        int alpha = (int) (pulse * 255);
        boolean locked = PlacementController.locked();
        RenderType type = RenderTypes.entityTranslucent(TEXTURE);

        poseStack.pushPose();
        poseStack.translate(a.getX() - camera.x, a.getY() - camera.y, a.getZ() - camera.z);
        collector.submitCustomGeometry(poseStack, type, (pose, buf) -> drawAll(pose, buf, ghosts, b, alpha, locked));
        poseStack.popPose();
    }

    /** Draws through the game's buffer source (used where no submit collector is available). */
    public static void renderImmediate(PoseStack poseStack, Vec3 camera) {
        if (!PlacementController.active()) return;
        var src = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
        BlockPos a = PlacementController.anchor();
        float pulse = 0.55f + 0.15f * Mth.sin((System.currentTimeMillis() % 100_000L) / 240f);
        RenderType type = RenderTypes.entityTranslucent(TEXTURE);
        poseStack.pushPose();
        poseStack.translate(a.getX() - camera.x, a.getY() - camera.y, a.getZ() - camera.z);
        drawAll(poseStack.last(), src.getBuffer(type), PlacementController.ghosts(), PlacementController.bounds(), (int) (pulse * 255), PlacementController.locked());
        poseStack.popPose();
        src.endBatch(type);
    }

    private static void drawAll(PoseStack.Pose pose, VertexConsumer buf, List<PlacementController.Ghost> ghosts, int[] b, int alpha, boolean locked) {
        {
            for (int i = 0; i < ghosts.size(); i++) {
                PlacementController.Ghost g = ghosts.get(i);
                int color;
                if (PlacementController.conflict(i)) {
                    color = ARGB.color(Math.min(255, alpha + 40), 255, 70, 70);
                } else {
                    int r = ARGB.red(g.argb()), gg = ARGB.green(g.argb()), bl = ARGB.blue(g.argb());
                    color = ARGB.color(alpha, (r * 3 + 90) / 4, (gg * 3 + 170) / 4, (bl * 3 + 255) / 4);
                }
                box(pose, buf, g.x() + 0.02f, g.y() + 0.02f, g.z() + 0.02f, g.x() + 0.98f, g.y() + 0.98f, g.z() + 0.98f, color, false);
            }
            int frame = locked ? ARGB.color(240, 120, 255, 160) : ARGB.color(240, 250, 214, 130);
            frame(pose, buf, b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1, 0.04f, frame);
            box(pose, buf, -0.1f, -0.02f, -0.1f, 1.1f, 0.02f, 1.1f, ARGB.color(170, 250, 214, 130), true);
        }
    }

    private static void frame(PoseStack.Pose pose, VertexConsumer buf, float x0, float y0, float z0, float x1, float y1, float z1, float t, int c) {
        box(pose, buf, x0 - t, y0 - t, z0 - t, x1 + t, y0 + t, z0 + t, c, true);
        box(pose, buf, x0 - t, y1 - t, z0 - t, x1 + t, y1 + t, z0 + t, c, true);
        box(pose, buf, x0 - t, y0 - t, z1 - t, x1 + t, y0 + t, z1 + t, c, true);
        box(pose, buf, x0 - t, y1 - t, z1 - t, x1 + t, y1 + t, z1 + t, c, true);
        box(pose, buf, x0 - t, y0 - t, z0 - t, x0 + t, y1 + t, z0 + t, c, true);
        box(pose, buf, x1 - t, y0 - t, z0 - t, x1 + t, y1 + t, z0 + t, c, true);
        box(pose, buf, x0 - t, y0 - t, z1 - t, x0 + t, y1 + t, z1 + t, c, true);
        box(pose, buf, x1 - t, y0 - t, z1 - t, x1 + t, y1 + t, z1 + t, c, true);
        box(pose, buf, x0 - t, y0 - t, z0 - t, x0 + t, y0 + t, z1 + t, c, true);
        box(pose, buf, x1 - t, y0 - t, z0 - t, x1 + t, y0 + t, z1 + t, c, true);
        box(pose, buf, x0 - t, y1 - t, z0 - t, x0 + t, y1 + t, z1 + t, c, true);
        box(pose, buf, x1 - t, y1 - t, z0 - t, x1 + t, y1 + t, z1 + t, c, true);
    }

    private static void box(PoseStack.Pose p, VertexConsumer b, float x0, float y0, float z0, float x1, float y1, float z1, int c, boolean solid) {
        // north (-z)
        quad(p, b, c, solid, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        // south (+z)
        quad(p, b, c, solid, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        // west (-x)
        quad(p, b, c, solid, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        // east (+x)
        quad(p, b, c, solid, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        // up
        quad(p, b, c, solid, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        // down
        quad(p, b, c, solid, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer b, int c, boolean solid, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        v(p, b, ax, ay, az, solid ? C : 0, solid ? C : 1, c, nx, ny, nz);
        v(p, b, bx, by, bz, solid ? C : 1, solid ? C : 1, c, nx, ny, nz);
        v(p, b, cx, cy, cz, solid ? C : 1, solid ? C : 0, c, nx, ny, nz);
        v(p, b, dx, dy, dz, solid ? C : 0, solid ? C : 0, c, nx, ny, nz);
    }

    private static void v(PoseStack.Pose p, VertexConsumer b, float x, float y, float z, float u, float vv, int c, float nx, float ny, float nz) {
        b.addVertex(p, x, y, z).setColor(c).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }
}
