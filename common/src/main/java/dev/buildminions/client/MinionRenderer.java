package dev.buildminions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.buildminions.BuildMinions;
import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionTier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

public class MinionRenderer extends MobRenderer<MinionEntity, MinionRenderState, MinionModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BuildMinions.id("minion"), "main");
    private static final Identifier[] TEXTURES = new Identifier[MinionTier.values().length];

    static {
        for (MinionTier t : MinionTier.values())
            TEXTURES[t.ordinal()] = BuildMinions.id("textures/entity/minion_" + t.id + ".png");
    }

    public MinionRenderer(EntityRendererProvider.Context context) {
        super(context, new MinionModel(context.bakeLayer(LAYER)), 0.35f);
    }

    @Override
    public MinionRenderState createRenderState() {
        return new MinionRenderState();
    }

    @Override
    public void extractRenderState(MinionEntity entity, MinionRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.tier = entity.getTier();
        state.phase = entity.getPhase();
        state.phaseTicks = entity.getPhaseTicks();
        state.swing = entity.getSwing(partialTick);
    }

    @Override
    public Identifier getTextureLocation(MinionRenderState state) {
        return TEXTURES[state.tier.ordinal()];
    }

    @Override
    protected void scale(MinionRenderState state, PoseStack poseStack) {
        poseStack.scale(0.62f, 0.62f, 0.62f);
    }
}
