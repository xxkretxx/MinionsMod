package dev.buildminions.fabric;

import dev.buildminions.BuildMinions;
import dev.buildminions.ModContent;
import dev.buildminions.client.BuildMinionsClient;
import dev.buildminions.client.GhostRenderer;
import dev.buildminions.client.MinionModel;
import dev.buildminions.client.MinionRenderer;
import dev.buildminions.client.ModKeys;
import dev.buildminions.client.PlacementController;
import dev.buildminions.client.PlacementHud;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class BuildMinionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BuildMinionsClient.init();
        Services.toServer = ClientPlayNetworking::send;
        for (ModNetwork.Clientbound<?> p : ModNetwork.CLIENTBOUND) registerClientbound(p);

        EntityRendererRegistry.register(ModContent.MINION.get(), MinionRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(MinionRenderer.LAYER, MinionModel::createBodyLayer);

        for (KeyMapping key : ModKeys.ALL) KeyBindingHelper.registerKeyBinding(key);
        ClientTickEvents.END_CLIENT_TICK.register(PlacementController::tick);

        WorldRenderEvents.AFTER_ENTITIES.register(ctx ->
                GhostRenderer.render(ctx.matrices(), ctx.commandQueue(), ctx.worldState().cameraRenderState.pos));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, BuildMinions.id("placement_hud"), PlacementHud::extract);
    }

    private static <T extends CustomPacketPayload> void registerClientbound(ModNetwork.Clientbound<T> p) {
        ClientPlayNetworking.registerGlobalReceiver(p.type(), (payload, ctx) -> p.handler().accept(payload));
    }
}
