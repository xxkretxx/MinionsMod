package dev.buildminions.forge;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.minion.MinionEntity;
import dev.buildminions.minion.MinionTier;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(BuildMinions.MOD_ID)
public class BuildMinionsForge {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, BuildMinions.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BuildMinions.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, BuildMinions.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BuildMinions.MOD_ID);

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(BuildMinions.id("main"), () -> "1", "1"::equals, "1"::equals);

    public BuildMinionsForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

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
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(ModContent.MINION.get(), MinionEntity.createAttributes().build()));

        int index = 0;
        for (ModNetwork.Serverbound<?> p : ModNetwork.SERVERBOUND) registerServerbound(index++, p);
        for (ModNetwork.Clientbound<?> p : ModNetwork.CLIENTBOUND) registerClientbound(index++, p);
        Services.toPlayer = (player, msg) -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);

        if (FMLEnvironment.dist == Dist.CLIENT) BuildMinionsForgeClient.init(modBus);
    }

    private static <T extends ModNetwork.Msg> void registerServerbound(int index, ModNetwork.Serverbound<T> p) {
        CHANNEL.messageBuilder(p.type(), index, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ModNetwork.Msg::write).decoder(p.decoder())
                .consumerMainThread((msg, ctx) -> {
                    if (ctx.get().getSender() != null) p.handler().accept(msg, ctx.get().getSender());
                }).add();
    }

    private static <T extends ModNetwork.Msg> void registerClientbound(int index, ModNetwork.Clientbound<T> p) {
        CHANNEL.messageBuilder(p.type(), index, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ModNetwork.Msg::write).decoder(p.decoder())
                .consumerMainThread((msg, ctx) -> p.handler().accept(msg)).add();
    }
}
