package com.itmecasey.modjs.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code Network} global. Client scripts use {@code sendToServer}; server scripts use {@code sendToPlayer} and
 * {@code sendToAll}. Receivers register with {@code NetworkEvents.dataReceived('name', event => ...)}.
 */
public final class NetworkBinding {
    public void sendToServer(String channel, Object data) {
        ModJSNetwork.sendToServer(channel, data);
    }

    public void sendToPlayer(Object player, String channel, Object data) {
        if (player instanceof ServerPlayer serverPlayer) {
            ModJSNetwork.sendToPlayer(serverPlayer, channel, data);
        }
    }

    public void sendToAll(String channel, Object data) {
        ModJSNetwork.sendToAll(channel, data);
    }
}
