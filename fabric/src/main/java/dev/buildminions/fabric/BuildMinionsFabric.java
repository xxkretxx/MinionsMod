package dev.buildminions.fabric;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionTier;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class BuildMinionsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EntityType<MinionEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, BuildMinions.id("minion"), ModContent.createMinionType());
        ModContent.MINION = () -> type;
        FabricDefaultAttributeRegistry.register(type, MinionEntity.createAttributes());

        Block ws = Registry.register(BuiltInRegistries.BLOCK, BuildMinions.id("minion_workstation"), ModContent.createWorkstation());
        ModContent.WORKSTATION = () -> ws;
        Item wsItem = Registry.register(BuiltInRegistries.ITEM, BuildMinions.id("minion_workstation"), ModContent.createWorkstationItem(ws));
        ModContent.WORKSTATION_ITEM = () -> wsItem;
        Item core = Registry.register(BuiltInRegistries.ITEM, BuildMinions.id("minion_core"), ModContent.createCore());
        ModContent.MINION_CORE = () -> core;
        for (MinionTier tier : MinionTier.values()) {
            Item item = Registry.register(BuiltInRegistries.ITEM, BuildMinions.id(tier.itemName()), ModContent.createMinionItem(tier));
            ModContent.MINION_ITEMS.put(tier, () -> item);
        }
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BuildMinions.id("main"), ModContent.fillTab(FabricItemGroup.builder()).build());

        for (ModNetwork.Serverbound<?> p : ModNetwork.SERVERBOUND) registerServerbound(p);
        Services.toPlayer = (player, msg) -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            msg.write(buf);
            ServerPlayNetworking.send(player, msg.id(), buf);
        };
        BuildMinions.LOG.info("Build Minions ready – go craft a minion!");
    }

    private static <T extends ModNetwork.Msg> void registerServerbound(ModNetwork.Serverbound<T> p) {
        ServerPlayNetworking.registerGlobalReceiver(p.id(), (server, player, handler, buf, sender) -> {
            T msg = p.decoder().apply(buf);
            server.execute(() -> p.handler().accept(msg, player));
        });
    }
}
