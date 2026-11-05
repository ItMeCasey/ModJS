package com.itmecasey.modjs.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.MobSpawnSettingsModifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.MobSpawnSettingsBuilder;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Forge's own {@code forge:remove_spawns} fails with a null pointer in 26.3 for any biome that has no spawns in one of the
 * mob categories, so scripts on that version get this copy with the missing categories skipped.
 */
public record RemoveSpawnsBiomeModifier(HolderSet<Biome> biomes, HolderSet<EntityType<?>> entityTypes) implements BiomeModifier {
    public static final MapCodec<RemoveSpawnsBiomeModifier> CODEC = RecordCodecBuilder.mapCodec(builder ->
        builder.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(RemoveSpawnsBiomeModifier::biomes),
            RegistryCodecs.holderSet(ForgeRegistries.Keys.ENTITY_TYPES).fieldOf("entity_types").forGetter(RemoveSpawnsBiomeModifier::entityTypes)
        ).apply(builder, RemoveSpawnsBiomeModifier::new));

    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
        DeferredRegister.create(ForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, "modjs");

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE || !biomes.contains(biome)) {
            return;
        }
        var entry = builder.attributes().get(EnvironmentAttributes.NATURAL_MOB_SPAWNS);
        if (entry == null || entry.modifier() != MobSpawnSettingsModifier.overlay()) {
            return;
        }
        MobSpawnSettingsBuilder spawns = new MobSpawnSettingsBuilder(entry.cast(MobSpawnSettingsModifier.overlay()));
        for (MobCategory category : MobCategory.values()) {
            var spawner = spawns.getSpawner(category);
            if (spawner != null) {
                spawner.removeIf(data -> entityTypes.contains(ForgeRegistries.ENTITY_TYPES.getHolder(data.type()).orElseThrow()));
            }
        }
        builder.attributes().modify(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettingsModifier.overlay(), spawns.build());
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }

    public static void register(net.minecraftforge.eventbus.api.bus.BusGroup modBus) {
        SERIALIZERS.register("remove_spawns", () -> CODEC);
        SERIALIZERS.register(modBus);
    }
}
