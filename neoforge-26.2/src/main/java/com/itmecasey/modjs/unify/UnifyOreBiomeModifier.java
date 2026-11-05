package com.itmecasey.modjs.unify;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Map;

/**
 * Swaps the ore features of every biome for ones that place the canonical ore block, when {@code Unify.ores()} is on. It is
 * applied by the data file {@code data/modjs/forge/biome_modifier/unify_ores.json}, when the world or server starts, after
 * recipes have loaded and the unified groups are known.
 */
public final class UnifyOreBiomeModifier implements BiomeModifier {
    public static final MapCodec<UnifyOreBiomeModifier> CODEC = MapCodec.unit(UnifyOreBiomeModifier::new);

    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "modjs");

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.AFTER_EVERYTHING || !UnifyConfig.oreWorldgen()) {
            return;
        }
        Map<Block, Block> map = OreUnifier.blockMap();
        if (map.isEmpty()) {
            return;
        }
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            List<Holder<PlacedFeature>> features = builder.getGenerationSettings().getFeatures(step);
            for (int i = 0; i < features.size(); i++) {
                features.set(i, OreUnifier.swap(features.get(i), map));
            }
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }

    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        SERIALIZERS.register("unify_ores", () -> CODEC);
        SERIALIZERS.register(modBus);
    }
}
