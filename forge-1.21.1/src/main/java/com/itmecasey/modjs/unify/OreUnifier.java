package com.itmecasey.modjs.unify;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Swaps other mods' ore blocks for the canonical ore block in world generation and in chunks that already exist. */
public final class OreUnifier {
    private OreUnifier() {
    }

    /** Non-canonical ore block to canonical ore block, from the current unified {@code c:ores/*} groups. */
    public static Map<Block, Block> blockMap() {
        Map<Block, Block> out = new HashMap<>();
        for (Map.Entry<String, String> entry : Unifier.current().oreReplacements().entrySet()) {
            Block from = block(entry.getKey());
            Block to = block(entry.getValue());
            if (from != null && to != null && from != to) {
                out.put(from, to);
            }
        }
        return out;
    }

    private static Block block(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
        return block == null || block == Blocks.AIR ? null : block;
    }

    // -- world generation ---------------------------------------------------------------------------------------------

    /** A copy of the placed feature with its ore targets swapped, or the same holder when nothing matches. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Holder<PlacedFeature> swap(Holder<PlacedFeature> holder, Map<Block, Block> map) {
        PlacedFeature placed = holder.value();
        ConfiguredFeature<?, ?> configured = placed.feature().value();
        if (!(configured.config() instanceof OreConfiguration ore)) {
            return holder;
        }
        boolean changed = false;
        List<OreConfiguration.TargetBlockState> targets = new ArrayList<>();
        for (OreConfiguration.TargetBlockState target : ore.targetStates) {
            Block replacement = map.get(target.state.getBlock());
            if (replacement != null) {
                targets.add(OreConfiguration.target(target.target, replacement.defaultBlockState()));
                changed = true;
            } else {
                targets.add(target);
            }
        }
        if (!changed) {
            return holder;
        }
        OreConfiguration swapped = new OreConfiguration(targets, ore.size, ore.discardChanceOnAirExposure);
        ConfiguredFeature<?, ?> feature = new ConfiguredFeature(configured.feature(), swapped);
        return Holder.direct(new PlacedFeature(Holder.direct(feature), placed.placement()));
    }

    // -- chunks ------------------------------------------------------------------------------------------------------

    /** Replaces non-canonical ore blocks in a loaded chunk; returns how many blocks changed. */
    public static int convertChunk(LevelChunk chunk, Map<Block, Block> map) {
        if (map.isEmpty()) {
            return 0;
        }
        int changed = 0;
        int minY = chunk.getMinBuildHeight();
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(state -> map.containsKey(state.getBlock()))) {
                continue;
            }
            int baseY = minY + index * 16;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block replacement = map.get(state.getBlock());
                        if (replacement != null) {
                            BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                            chunk.setBlockState(pos, replacement.defaultBlockState(), false);
                            changed++;
                        }
                    }
                }
            }
        }
        if (changed > 0) {
            chunk.setUnsaved(true);
        }
        return changed;
    }
}
