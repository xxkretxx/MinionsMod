package dev.buildminions.forge;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionTier;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(BuildMinions.MOD_ID)
public class BuildMinionsForge {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BuildMinions.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BuildMinions.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, BuildMinions.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildMinions.MOD_ID);

    public static net.minecraftforge.network.Channel<CustomPacketPayload> CHANNEL;

    public BuildMinionsForge(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();

        RegistryObject<EntityType<MinionEntity>> minion = ENTITIES.register("minion", ModContent::createMinionType);
        ModContent.MINION = minion::get;
        RegistryObject<Block> ws = BLOCKS.register("minion_workstation", ModContent::createWorkstation);
        ModContent.WORKSTATION = ws::get;
        RegistryObject<Item> wsItem = ITEMS.register("minion_workstation", () -> ModContent.createWorkstationItem(ws.get()));
        ModContent.WORKSTATION_ITEM = wsItem::get;
        RegistryObject<Item> core = ITEMS.register("minion_core", ModContent::createCore);
        ModContent.MINION_CORE = core::get;
        for (MinionTier tier : MinionTier.values()) {
            RegistryObject<Item> item = ITEMS.register(tier.itemName(), () -> ModContent.createMinionItem(tier));
            ModContent.MINION_ITEMS.put(tier, item::get);
        }
        TABS.register("main", () -> ModContent.fillTab(CreativeModeTab.builder()).build());

        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);

        EntityAttributeCreationEvent.getBus(modBus).addListener(e -> e.put(ModContent.MINION.get(), MinionEntity.createAttributes().build()));

        var play = ChannelBuilder.named(BuildMinions.id("main")).networkProtocolVersion(1).payloadChannel().play();
        var clientbound = play.clientbound();
        for (ModNetwork.Clientbound<?> p : ModNetwork.CLIENTBOUND) addClientbound(clientbound, p);
        var serverbound = clientbound.serverbound();
        for (ModNetwork.Serverbound<?> p : ModNetwork.SERVERBOUND) addServerbound(serverbound, p);
        CHANNEL = serverbound.build();
        Services.toPlayer = (player, payload) -> CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));

        if (FMLEnvironment.dist.isClient()) BuildMinionsForgeClient.init(modBus);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends CustomPacketPayload> void addClientbound(net.minecraftforge.network.payload.PayloadFlow flow, ModNetwork.Clientbound<T> p) {
        flow.addMain(p.type(), p.codec(), (java.util.function.BiConsumer<T, net.minecraftforge.event.network.CustomPayloadEvent.Context>) (msg, ctx) -> p.handler().accept(msg));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends CustomPacketPayload> void addServerbound(net.minecraftforge.network.payload.PayloadFlow flow, ModNetwork.Serverbound<T> p) {
        flow.addMain(p.type(), p.codec(), (java.util.function.BiConsumer<T, net.minecraftforge.event.network.CustomPayloadEvent.Context>) (msg, ctx) -> {
            if (ctx.getSender() != null) p.handler().accept(msg, ctx.getSender());
        });
    }
}
