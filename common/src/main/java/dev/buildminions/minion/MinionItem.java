package dev.buildminions.minion;

import dev.buildminions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** Right-click a block to deploy the minion. Item damage = blocks already placed. */
public class MinionItem extends Item {
    public final MinionTier tier;

    public MinionItem(MinionTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        MinionEntity minion = ModContent.MINION.get().create(level);
        if (minion == null) return InteractionResult.FAIL;
        float yaw = player != null ? player.getYRot() + 180f : 0f;
        minion.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        minion.setYHeadRot(yaw);
        minion.setYBodyRot(yaw);
        minion.setup(tier, stack.getDamageValue(), tier.upgradeFromMaxDamage(stack.getMaxDamage()), player != null ? player.getUUID() : null);
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) minion.setCustomName(stack.getHoverName());
        level.addFreshEntity(minion);
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1f, 1.4f);
        if (player == null || !player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
