package dev.buildminions.fabric;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionTier;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public class BuildMinionsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // entity
        EntityType<MinionEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, ModContent.MINION_KEY, ModContent.createMinionType());
        ModContent.MINION = () -> type;
        FabricDefaultAttributeRegistry.register(type, MinionEntity.createAttributes());

        // items
        Item core = Registry.register(BuiltInRegistries.ITEM, ModContent.itemKey("minion_core"), ModContent.createCore());
        ModContent.MINION_CORE = () -> core;
        for (MinionTier tier : MinionTier.values()) {
            Item item = Registry.register(BuiltInRegistries.ITEM, ModContent.itemKey(tier.itemName()), ModContent.createMinionItem(tier));
            ModContent.MINION_ITEMS.put(tier, () -> item);
        }
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BuildMinions.id("main"), ModContent.fillTab(FabricItemGroup.builder()).build());

        // networking
        for (ModNetwork.Serverbound<?> p : ModNetwork.SERVERBOUND) registerServerbound(p);
        for (ModNetwork.Clientbound<?> p : ModNetwork.CLIENTBOUND) registerClientboundType(p);
        Services.toPlayer = ServerPlayNetworking::send;

        BuildMinions.LOG.info("Build Minions ready – go craft a minion!");
    }

    private static <T extends CustomPacketPayload> void registerClientboundType(ModNetwork.Clientbound<T> p) {
        PayloadTypeRegistry.playS2C().register(p.type(), p.codec());
    }

    private static <T extends CustomPacketPayload> void registerServerbound(ModNetwork.Serverbound<T> p) {
        PayloadTypeRegistry.playC2S().register(p.type(), p.codec());
        ServerPlayNetworking.registerGlobalReceiver(p.type(), (payload, ctx) -> p.handler().accept(payload, ctx.player()));
    }
}
