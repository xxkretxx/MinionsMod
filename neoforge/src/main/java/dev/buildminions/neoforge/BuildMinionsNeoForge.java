package dev.buildminions.neoforge;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionTier;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BuildMinions.MOD_ID)
public class BuildMinionsNeoForge {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BuildMinions.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BuildMinions.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildMinions.MOD_ID);

    public BuildMinionsNeoForge(IEventBus modBus) {
        DeferredHolder<EntityType<?>, EntityType<MinionEntity>> minion = ENTITIES.register("minion", ModContent::createMinionType);
        ModContent.MINION = minion::get;

        DeferredHolder<Item, Item> core = ITEMS.register("minion_core", ModContent::createCore);
        ModContent.MINION_CORE = core::get;
        for (MinionTier tier : MinionTier.values()) {
            DeferredHolder<Item, Item> item = ITEMS.register(tier.itemName(), () -> ModContent.createMinionItem(tier));
            ModContent.MINION_ITEMS.put(tier, item::get);
        }
        TABS.register("main", () -> ModContent.fillTab(CreativeModeTab.builder()).build());

        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);

        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(ModContent.MINION.get(), MinionEntity.createAttributes().build()));
        modBus.addListener(RegisterPayloadHandlersEvent.class, BuildMinionsNeoForge::registerPayloads);
        Services.toPlayer = PacketDistributor::sendToPlayer;
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        for (ModNetwork.Serverbound<?> p : ModNetwork.SERVERBOUND) serverbound(registrar, p);
        for (ModNetwork.Clientbound<?> p : ModNetwork.CLIENTBOUND) clientbound(registrar, p);
    }

    private static <T extends CustomPacketPayload> void serverbound(PayloadRegistrar r, ModNetwork.Serverbound<T> p) {
        r.playToServer(p.type(), p.codec(), (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) p.handler().accept(payload, sp);
        });
    }

    private static <T extends CustomPacketPayload> void clientbound(PayloadRegistrar r, ModNetwork.Clientbound<T> p) {
        // handler only calls into ModNetwork hooks, which the client init fills in – safe on dedicated servers
        r.playToClient(p.type(), p.codec(), (payload, ctx) -> p.handler().accept(payload));
    }
}
