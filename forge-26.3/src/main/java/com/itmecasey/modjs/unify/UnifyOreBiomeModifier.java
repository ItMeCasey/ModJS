package com.itmecasey.modjs.unify;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;

/**
 * Swaps the ore features of every biome for ones that place the canonical ore block, when {@code Unify.ores()} is on. It is
 * applied by the biome modifier that {@code UnifyOreData} writes into the generated data pack, when the world or server starts, after
 * recipes have loaded and the unified groups are known.
 */
public final class UnifyOreBiomeModifier implements BiomeModifier {
    public static final MapCodec<UnifyOreBiomeModifier> CODEC = MapCodec.unit(UnifyOreBiomeModifier::new);

    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "modjs");

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.AFTER_EVERYTHING || !UnifyConfig.oreWorldgen()) {
            return;
        }
        Map<Block, Block> map = OreUnifier.blockMap();
        if (map.isEmpty()) {
            return;
        }
        for (List<Holder<PlacedFeature>> features : features(builder.generationSettings())) {
            for (int i = 0; i < features.size(); i++) {
                features.set(i, OreUnifier.swap(features.get(i), map));
            }
        }
    }

    /** The per-step feature lists of the builder; the vanilla builder keeps them in a protected field. */
    @SuppressWarnings("unchecked")
    private static List<List<Holder<PlacedFeature>>> features(net.minecraft.world.level.biome.BiomeGenerationSettings.PlainBuilder settings) {
        try {
            java.lang.reflect.Field field = net.minecraft.world.level.biome.BiomeGenerationSettings.PlainBuilder.class.getDeclaredField("features");
            field.setAccessible(true);
            return (List<List<Holder<PlacedFeature>>>) field.get(settings);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read the biome feature lists", e);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }

    public static void register(net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        SERIALIZERS.register("unify_ores", () -> CODEC);
        SERIALIZERS.register(modBus);
    }
}
