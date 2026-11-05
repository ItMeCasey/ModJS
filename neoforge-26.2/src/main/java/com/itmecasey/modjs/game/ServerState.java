package com.itmecasey.modjs.game;

import net.minecraft.server.MinecraftServer;

/** The running server, for scripts that need it outside an event (the {@code Utils.server} property). */
public final class ServerState {
    private static volatile MinecraftServer server;

    private ServerState() {
    }

    static void set(MinecraftServer value) {
        server = value;
    }

    static void clear() {
        server = null;
        Scheduler.clear();
    }

    public static MinecraftServer get() {
        return server;
    }
}
