package dev.buildminions.forge;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.client.BuildMinionsClient;
import dev.buildminions.client.MinionModel;
import dev.buildminions.client.MinionRenderer;
import dev.buildminions.client.ModKeys;
import dev.buildminions.client.PlacementController;
import dev.buildminions.client.PlacementHud;
import dev.buildminions.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.network.PacketDistributor;

final class BuildMinionsForgeClient {
    private BuildMinionsForgeClient() {}

    static void init(BusGroup modBus) {
        BuildMinionsClient.init();
        Services.toServer = payload -> BuildMinionsForge.CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
        EntityRenderersEvent.RegisterRenderers.getBus(modBus).addListener(e -> e.registerEntityRenderer(ModContent.MINION.get(), MinionRenderer::new));
        EntityRenderersEvent.RegisterLayerDefinitions.getBus(modBus).addListener(e -> e.registerLayerDefinition(MinionRenderer.LAYER, MinionModel::createBodyLayer));
        RegisterKeyMappingsEvent.getBus(modBus).addListener(e -> ModKeys.ALL.forEach(e::register));
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(e -> e.getLayeredDraw().add(BuildMinions.id("placement_hud"), (g, dt) -> PlacementHud.extract(g, dt)));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            PlacementController.tick(Minecraft.getInstance());
            GhostParticles.draw(Minecraft.getInstance()); // Forge 1.21.11: particle hologram (no level-render hook)
        });
    }
}
