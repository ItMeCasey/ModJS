package com.itmecasey.modjs.game;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Script-safe stand-ins for {@link Block} and {@link BlockState}. On 26.1.2 the script engine cannot inspect those
 * classes on a dedicated server (some of their methods mention client-only types), so scripts get these instead.
 */
public final class BlockFacades {
    private BlockFacades() {
    }

    /** Wrap adapter: swaps blocks and block states for facades, leaves everything else alone. */
    public static Object adapt(Object value) {
        if (value instanceof Block block) {
            return new BlockFacade(block);
        }
        if (value instanceof BlockState state) {
            return new StateFacade(state);
        }
        return null;
    }

    /** A block as scripts see it. */
    public static final class BlockFacade {
        private final Block block;

        BlockFacade(Block block) {
            this.block = block;
        }

        /** The id, for example {@code minecraft:stone}. */
        public String getId() {
            return BuiltInRegistries.BLOCK.getKey(block).toString();
        }

        public String getDescriptionId() {
            return block.getDescriptionId();
        }

        public StateFacade getDefaultState() {
            return new StateFacade(block.defaultBlockState());
        }

        @Override
        public String toString() {
            return getId();
        }
    }

    /** A block state as scripts see it. */
    public static final class StateFacade {
        private final BlockState state;

        StateFacade(BlockState state) {
            this.state = state;
        }

        public String getBlockId() {
            return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        }

        public boolean isAir() {
            return state.isAir();
        }

        /** Property names to their values, for example {@code {facing: 'north'}}. */
        public Map<String, String> getProperties() {
            Map<String, String> out = new LinkedHashMap<>();
            for (Property<?> property : state.getProperties()) {
                out.put(property.getName(), String.valueOf(state.getValue(property)));
            }
            return out;
        }

        @Override
        public String toString() {
            return getBlockId() + getProperties();
        }
    }
}
