package dev.buildminions.fabric;

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
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.FriendlyByteBuf;

public class BuildMinionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BuildMinionsClient.init();
        Services.toServer = msg -> {
            FriendlyByteBuf buf = PacketByteBufs.create();
            msg.write(buf);
            ClientPlayNetworking.send(msg.id(), buf);
        };
        for (ModNetwork.Clientbound<?> p : ModNetwork.CLIENTBOUND) registerClientbound(p);

        EntityRendererRegistry.register(ModContent.MINION.get(), MinionRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(MinionRenderer.LAYER, MinionModel::createBodyLayer);
        for (KeyMapping key : ModKeys.ALL) KeyBindingHelper.registerKeyBinding(key);
        ClientTickEvents.END_CLIENT_TICK.register(PlacementController::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> GhostRenderer.renderImmediate(ctx.matrixStack(), ctx.camera().getPosition()));
        HudRenderCallback.EVENT.register(PlacementHud::extract);
    }

    private static <T extends ModNetwork.Msg> void registerClientbound(ModNetwork.Clientbound<T> p) {
        ClientPlayNetworking.registerGlobalReceiver(p.id(), (client, handler, buf, sender) -> {
            T msg = p.decoder().apply(buf);
            client.execute(() -> p.handler().accept(msg));
        });
    }
}
