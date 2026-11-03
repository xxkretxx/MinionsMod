package dev.buildminions.forge;

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
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

final class BuildMinionsForgeClient {
    private BuildMinionsForgeClient() {}

    static void init(IEventBus modBus) {
        BuildMinionsClient.init();
        Services.toServer = BuildMinionsForge.CHANNEL::sendToServer;
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(ModContent.MINION.get(), MinionRenderer::new));
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(MinionRenderer.LAYER, MinionModel::createBodyLayer));
        modBus.addListener((RegisterKeyMappingsEvent e) -> ModKeys.ALL.forEach(e::register));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) PlacementController.tick(Minecraft.getInstance());
        });
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Post e) -> {
            if (e.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) PlacementHud.extract(e.getGuiGraphics(), e.getPartialTick());
        });
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES)
                GhostRenderer.renderImmediate(e.getPoseStack(), e.getCamera().getPosition());
        });
    }
}
