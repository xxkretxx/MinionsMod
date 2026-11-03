package dev.buildminions.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import dev.buildminions.BuildMinions;
import dev.buildminions.net.ModNetwork;
import dev.buildminions.platform.Services;
import dev.buildminions.schematic.Schematic;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;

/** Client-side glue: blueprint cache, uploads and packet reactions. */
public final class ClientState {
    /** Blueprint last uploaded to each minion (entity id → blueprint), used for the hologram. */
    public static final Map<Integer, Schematic> BLUEPRINTS = new HashMap<>();

    private ClientState() {}

    public static Path schematicsFolder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("schematics");
    }

    public static void upload(int entityId, Schematic schematic) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(schematic.save(), bos);
            byte[] all = bos.toByteArray();
            int total = Math.max(1, (all.length + ModNetwork.CHUNK_SIZE - 1) / ModNetwork.CHUNK_SIZE);
            for (int i = 0; i < total; i++) {
                byte[] part = Arrays.copyOfRange(all, i * ModNetwork.CHUNK_SIZE, Math.min(all.length, (i + 1) * ModNetwork.CHUNK_SIZE));
                Services.toServer.accept(new ModNetwork.UploadChunk(entityId, i, total, part));
            }
            BLUEPRINTS.put(entityId, schematic);
        } catch (IOException e) {
            BuildMinions.LOG.error("Could not upload blueprint", e);
        }
    }

    public static void onState(ModNetwork.MinionState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MinionScreen screen && screen.entityId() == state.entityId()) {
            screen.update(state);
        } else if (state.open()) {
            mc.setScreen(new MinionScreen(state));
        }
    }

    public static void onStartPlacement(ModNetwork.StartPlacement p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MinionScreen) mc.setScreen(null);
        if (!PlacementController.start(p.entityId()) && mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.buildminions.no_hologram"), false);
            Services.toServer.accept(new ModNetwork.Action(p.entityId(), ModNetwork.Action.CANCEL));
        }
    }
}
