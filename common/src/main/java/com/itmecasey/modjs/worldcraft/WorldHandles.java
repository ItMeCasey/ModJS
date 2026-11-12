package com.itmecasey.modjs.worldcraft;

import java.util.Map;

/**
 * What the in-world crafting engine needs from the game, so the engine itself stays free of Minecraft classes. Every Minecraft
 * build implements these small interfaces over its own level, player and item stack types.
 */
public final class WorldHandles {
    private WorldHandles() {
    }

    /** An item stack the engine may read and use up. */
    public interface ItemHandle {
        String id();

        int count();

        /** True for an item id ({@code minecraft:stick}) or an item tag ({@code #c:ingots}). */
        boolean matches(String matcher);

        void shrink(int amount);

        /** Wears the item down by that much damage (does nothing for an item without durability). */
        void damage(int amount);

        default boolean isEmpty() {
            return count() <= 0;
        }
    }

    /** A player who right-clicked, left-clicked or broke a block. */
    public interface PlayerHandle {
        boolean sneaking();

        boolean creative();

        /** The stack in the main hand, or the off hand. */
        ItemHandle held(boolean offHand);

        /** Puts an item in the inventory, or drops it at the player's feet when it does not fit. */
        void give(String itemId, int count);
    }

    /** A dropped item in the world. */
    public interface ItemEntityHandle {
        ItemHandle stack();

        double x();

        double y();

        double z();

        /** Called after the engine used items up: the game removes the entity when its stack is empty. */
        void changed();
    }

    /** The level the recipe happens in. */
    public interface LevelHandle {
        /**
         * Whether the block at a position matches: {@code minecraft:stone}, a tag {@code #minecraft:logs}, optionally with block state
         * values in brackets ({@code minecraft:cauldron[level=3]}). Fluids match by their block ({@code minecraft:water}).
         */
        boolean blockMatches(int x, int y, int z, String matcher);

        /** Replaces the block with the given block (and optional bracketed state values). Returns false for an unknown block. */
        boolean setBlock(int x, int y, int z, String spec);

        /** Removes the block without dropping anything. */
        void removeBlock(int x, int y, int z);

        void spawnItem(double x, double y, double z, String itemId, int count);

        /** Runs a command as the server with the position as its origin. */
        void runCommand(int x, int y, int z, String command);

        double random();

        /** The id of the block at a position ({@code minecraft:stone}), or null when that chunk is not loaded. */
        default String blockId(int x, int y, int z) {
            return null;
        }

        /** Breaks the block like the world does: with its drops when asked, without a tool. False when unloaded or already air. */
        default boolean destroyBlock(int x, int y, int z, boolean drop) {
            return false;
        }

        /** Applies bone meal growth to the block without using an item. True when something grew. */
        default boolean bonemeal(int x, int y, int z) {
            return false;
        }

        /** The dimension id ({@code minecraft:overworld}). */
        default String dimension() {
            return null;
        }

        /** Block state values as text, for tests and logs. */
        default Map<String, String> describe(int x, int y, int z) {
            return Map.of();
        }
    }
}
