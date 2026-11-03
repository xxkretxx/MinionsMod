package dev.buildminions.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.buildminions.net.ModNetwork.Msg;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tiny bridge between the shared code and the mod loader. Each loader fills these in during init,
 * so the common code never imports Fabric or NeoForge classes.
 */
public final class Services {
    /** Sends a payload from the server to one player. Set by the loader's common init. */
    public static BiConsumer<ServerPlayer, Msg> toPlayer = (p, payload) -> {};
    /** Sends a payload from the client to the server. Set by the loader's client init. */
    public static Consumer<Msg> toServer = payload -> {};

    private Services() {}
}
