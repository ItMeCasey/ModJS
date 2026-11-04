package com.itmecasey.modjs.game;

import net.minecraft.server.MinecraftServer;
import org.mozilla.javascript.Function;

import java.util.concurrent.ThreadLocalRandom;

/** The {@code Utils} global. */
public final class UtilsBinding {
    /** The running server, or null before a world is loaded. */
    public MinecraftServer getServer() {
        return ServerState.get();
    }

    /** Runs {@code callback} once after {@code ticks} server ticks (20 ticks = 1 second). */
    public Scheduler.Task runAfter(int ticks, Function callback) {
        return Scheduler.after(ticks, callback);
    }

    /** Runs {@code callback} every {@code ticks} server ticks until the returned task is cancelled. */
    public Scheduler.Task runEvery(int ticks, Function callback) {
        return Scheduler.every(ticks, callback);
    }

    /** Random integer from {@code min} to {@code max}, both inclusive. */
    public int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(Math.min(min, max), Math.max(min, max) + 1);
    }

    public double random() {
        return ThreadLocalRandom.current().nextDouble();
    }

    /** True with probability {@code chance} (0.0 to 1.0). */
    public boolean rollChance(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /** Runs a command as the server console. Returns false when no server is running. */
    public boolean runCommand(String command) {
        MinecraftServer server = ServerState.get();
        if (server == null) {
            return false;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        return true;
    }

    /** The block entity at a position in a dimension ({@code 'minecraft:overworld'}), or null. */
    public BlockEntityJS blockEntity(String dimension, int x, int y, int z) {
        MinecraftServer server = ServerState.get();
        if (server == null) {
            return null;
        }
        for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return BlockEntityJS.at(level, new net.minecraft.core.BlockPos(x, y, z));
            }
        }
        return null;
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
