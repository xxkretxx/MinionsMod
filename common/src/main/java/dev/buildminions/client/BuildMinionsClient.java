package dev.buildminions.client;

import dev.buildminions.net.ModNetwork;

/** Loader-independent part of client init. */
public final class BuildMinionsClient {
    private BuildMinionsClient() {}

    public static void init() {
        ModNetwork.onState = ClientState::onState;
        ModNetwork.onStartPlacement = ClientState::onStartPlacement;
    }
}
