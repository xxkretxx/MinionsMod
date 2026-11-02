package dev.buildminions.neoforge;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.client.BuildMinionsClient;
import dev.buildminions.client.GhostRenderer;
import dev.buildminions.client.MinionModel;
import dev.buildminions.client.MinionRenderer;
import dev.buildminions.client.ModKeys;
import dev.buildminions.client.PlacementController;
import dev.buildminions.client.PlacementHud;
import dev.buildminions.platform.Services;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = BuildMinions.MOD_ID, dist = Dist.CLIENT)
public class BuildMinionsNeoForgeClient {
    public BuildMinionsNeoForgeClient(IEventBus modBus) {
        BuildMinionsClient.init();
        Services.toServer = ClientPacketDistributor::sendToServer;

        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(ModContent.MINION.get(), MinionRenderer::new));
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class,
                e -> e.registerLayerDefinition(MinionRenderer.LAYER, MinionModel::createBodyLayer));
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> ModKeys.ALL.forEach(e::register));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAboveAll(BuildMinions.id("placement_hud"), PlacementHud::extract));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> PlacementController.tick(Minecraft.getInstance()));
        // Hologram. NeoForge exposes the level submit phase through SubmitCustomGeometryEvent.
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, e ->
                GhostRenderer.render(e.getPoseStack(), e.getSubmitNodeCollector(), e.getLevelRenderState().cameraRenderState.pos));
    }
}
