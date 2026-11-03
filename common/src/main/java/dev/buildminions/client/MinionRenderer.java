package dev.buildminions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.buildminions.BuildMinions;
import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionTier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MinionRenderer extends MobRenderer<MinionEntity, MinionModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BuildMinions.id("minion"), "main");
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[MinionTier.values().length];

    static {
        for (MinionTier t : MinionTier.values())
            TEXTURES[t.ordinal()] = BuildMinions.id("textures/entity/minion_" + t.id + ".png");
    }

    public MinionRenderer(EntityRendererProvider.Context context) {
        super(context, new MinionModel(context.bakeLayer(LAYER)), 0.35f);
    }

    @Override
    public void render(MinionEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        this.model.partialTick = partialTick;
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MinionEntity entity) {
        return TEXTURES[entity.getTier().ordinal()];
    }

    @Override
    protected void scale(MinionEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.62f, 0.62f, 0.62f);
    }
}
