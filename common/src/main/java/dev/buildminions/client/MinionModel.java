package dev.buildminions.client;

import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionPhase;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The Build Minion model (64x64 texture), 1.21.1 edition (entity-based animation). */
public class MinionModel extends HierarchicalModel<MinionEntity> {
    private final ModelPart root, body, head, antenna, rightArm, leftArm, hammer, rightLeg, leftLeg;
    public float partialTick;

    public MinionModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.antenna = head.getChild("antenna");
        this.rightArm = body.getChild("right_arm");
        this.hammer = rightArm.getChild("hammer");
        this.leftArm = body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    @Override
    public ModelPart root() { return root; }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20).addBox(-4F, 0F, -3F, 8F, 7F, 6F), PartPose.offset(0F, 12F, 0F));
        body.addOrReplaceChild("backpack", CubeListBuilder.create().texOffs(0, 34).addBox(-3F, 1F, 3F, 6F, 5F, 2F), PartPose.offset(0F, 0F, 0F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -10F, -5F, 10F, 10F, 10F)
                .texOffs(16, 34).addBox(-6F, -6F, -1F, 1F, 2F, 2F)
                .mirror().texOffs(16, 34).addBox(5F, -6F, -1F, 1F, 2F, 2F).mirror(false), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("antenna", CubeListBuilder.create()
                .texOffs(40, 20).addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F)
                .texOffs(44, 20).addBox(-1F, -5F, -1F, 2F, 2F, 2F), PartPose.offset(0F, -10F, 0F));
        PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 0).addBox(-1.5F, -1F, -1.5F, 3F, 7F, 3F), PartPose.offset(-5.5F, 1F, 0F));
        rightArm.addOrReplaceChild("hammer", CubeListBuilder.create()
                .texOffs(28, 26).addBox(-0.5F, -0.5F, -7F, 1F, 1F, 7F)
                .texOffs(28, 34).addBox(-1F, -2F, -9F, 2F, 4F, 3F), PartPose.offset(0F, 5F, -1F));
        body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 10).addBox(-1.5F, -1F, -1.5F, 3F, 7F, 3F), PartPose.offset(5.5F, 1F, 0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(52, 0).addBox(-1.5F, 0F, -1.5F, 3F, 5F, 3F), PartPose.offset(-2F, 19F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(52, 8).addBox(-1.5F, 0F, -1.5F, 3F, 5F, 3F), PartPose.offset(2F, 19F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(MinionEntity e, float walkPos, float walkSpeed, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        walkSpeed = Math.min(1f, walkSpeed);
        MinionPhase phase = e.getPhase();
        int phaseTicks = e.getPhaseTicks();
        float swing = e.getSwing(partialTick);

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.7f;
        antenna.zRot = Mth.sin(age * 0.15f) * 0.18f;
        antenna.xRot = Mth.cos(age * 0.11f) * 0.08f;
        body.y += Mth.sin(age * 0.1f) * 0.35f;
        rightLeg.xRot = Mth.cos(walkPos * 0.6662f) * 1.3f * walkSpeed;
        leftLeg.xRot = Mth.cos(walkPos * 0.6662f + Mth.PI) * 1.3f * walkSpeed;
        leftArm.xRot = Mth.cos(walkPos * 0.6662f) * 0.9f * walkSpeed;
        rightArm.xRot = -0.25f + Mth.cos(walkPos * 0.6662f + Mth.PI) * 0.5f * walkSpeed;
        leftArm.zRot = -0.06f - Mth.sin(age * 0.067f) * 0.04f;
        rightArm.zRot = 0.06f + Mth.sin(age * 0.067f) * 0.04f;

        switch (phase) {
            case BUILDING, CRAFTING -> {
                rightLeg.xRot = 0.25f + Mth.sin(age * 0.2f) * 0.15f;
                leftLeg.xRot = 0.15f - Mth.sin(age * 0.2f) * 0.15f;
                leftArm.xRot = -0.35f;
                leftArm.zRot = -0.45f - Mth.sin(age * 0.3f) * 0.08f;
                rightArm.xRot = -1.2f;
                if (swing > 0f) {
                    float t = 1f - swing;
                    float strike = t < 0.4f ? -t / 0.4f : -1f + (t - 0.4f) / 0.6f * 2.4f;
                    rightArm.xRot = -1.2f + strike * 1.3f;
                    head.xRot += 0.25f * Mth.sin(t * Mth.PI);
                    body.xRot = 0.12f * Mth.sin(t * Mth.PI);
                }
            }
            case AWAITING_PLACEMENT -> {
                leftArm.xRot = -1.5f;
                rightLeg.xRot = Math.max(0, Mth.sin(age * 0.5f)) * -0.3f;
            }
            case DONE -> {
                if (phaseTicks < 60) {
                    rightArm.xRot = -2.8f + Mth.sin(age * 0.8f) * 0.3f;
                    leftArm.xRot = -2.8f - Mth.sin(age * 0.8f) * 0.3f;
                    body.y -= Math.abs(Mth.sin(age * 0.4f)) * 1.5f;
                    hammer.xRot = Mth.sin(age * 0.8f) * 0.5f;
                }
            }
            case BREAKING -> {
                float p = Math.min(1f, phaseTicks / (float) MinionEntity.BREAK_DURATION);
                float shake = Mth.sin(age * 2.6f) * 0.18f * p;
                body.zRot = shake;
                head.zRot = -shake * 1.5f;
                head.y -= p * p * 3f;
                antenna.y -= p * p * 4f;
                rightArm.zRot = 0.6f * p;
                leftArm.zRot = -0.6f * p;
            }
            default -> {}
        }
    }
}
