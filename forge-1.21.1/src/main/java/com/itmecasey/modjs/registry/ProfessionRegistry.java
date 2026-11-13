package com.itmecasey.modjs.registry;

import com.google.common.collect.ImmutableSet;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Registers the villager professions scripts declared, each with a point of interest of the same id that the profession's job site blocks
 * belong to. Runs from {@link ContentRegistry#register}.
 */
final class ProfessionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final NsRegisters<PoiType> POIS = new NsRegisters<>(ns -> DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, ns));
    private static final NsRegisters<net.minecraft.world.entity.npc.VillagerProfession> PROFESSIONS = new NsRegisters<>(ns -> DeferredRegister.create(Registries.VILLAGER_PROFESSION, ns));

    private ProfessionRegistry() {
    }

    static void register(IEventBus bus) {
        for (ProfessionBuilderJS profession : RegistryManager.professions()) {
            ProfessionBuilderJS p = profession;
            ResourceKey<PoiType> poiKey = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ResourceLocation.fromNamespaceAndPath(p.namespace(), p.id()));
            POIS.register(p.namespace(), p.id(), () -> new PoiType(states(p.jobSites(), p), 1, 1));
            PROFESSIONS.register(p.namespace(), p.id(), () -> profession(p, poiKey));
        }
        POIS.registerAll(r -> r.register(bus));
        PROFESSIONS.registerAll(r -> r.register(bus));
    }

    private static ResourceLocation id(String text) {
        return ResourceLocation.tryParse(text);
    }

    private static Set<BlockState> states(List<String> blocks, ProfessionBuilderJS p) {
        Set<BlockState> out = new HashSet<>();
        for (Block block : blocks(blocks, p)) {
            out.addAll(block.getStateDefinition().getPossibleStates());
        }
        return out;
    }

    private static List<Block> blocks(List<String> ids, ProfessionBuilderJS p) {
        List<Block> out = new java.util.ArrayList<>();
        for (String text : ids) {
            ResourceLocation id = id(text);
            var block = id == null ? java.util.Optional.<Block>empty() : BuiltInRegistries.BLOCK.getOptional(id);
            if (block.isPresent()) {
                out.add(block.get());
            } else {
                LOGGER.warn("[villagers] {}: the block {} does not exist and is left out", p.fullId(), text);
            }
        }
        return out;
    }

    private static net.minecraft.world.entity.npc.VillagerProfession profession(ProfessionBuilderJS p, ResourceKey<PoiType> poiKey) {
        ImmutableSet.Builder<Item> items = ImmutableSet.builder();
        for (String text : p.requestedItems()) {
            ResourceLocation id = id(text);
            var item = id == null ? java.util.Optional.<Item>empty() : BuiltInRegistries.ITEM.getOptional(id);
            item.ifPresent(items::add);
        }
        ImmutableSet<Block> secondary = ImmutableSet.copyOf(blocks(p.secondarySites(), p));
        SoundEvent sound = null;
        if (p.workSoundId() != null) {
            ResourceLocation id = id(p.workSoundId());
            sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
            if (sound == null) {
                LOGGER.warn("[villagers] {}: the sound {} does not exist", p.fullId(), p.workSoundId());
            }
        }
        java.util.function.Predicate<Holder<PoiType>> site = holder -> holder.is(poiKey);
        return new net.minecraft.world.entity.npc.VillagerProfession(p.id(), site, site, items.build(), secondary, sound);
    }
}
