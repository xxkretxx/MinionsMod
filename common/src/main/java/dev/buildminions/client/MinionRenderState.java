package dev.buildminions.client;

import dev.buildminions.minion.MinionPhase;
import dev.buildminions.minion.MinionTier;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class MinionRenderState extends LivingEntityRenderState {
    public MinionTier tier = MinionTier.COPPER;
    public MinionPhase phase = MinionPhase.IDLE;
    public int phaseTicks;
    /** 1 → 0 while the hammer swing plays. */
    public float swing;
}
