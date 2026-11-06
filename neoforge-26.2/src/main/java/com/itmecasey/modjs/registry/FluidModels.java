package com.itmecasey.modjs.registry;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;

/**
 * NeoForge 26 draws fluids from fluid models. A script fluid gets the vanilla water textures tinted with its color, which is what
 * the older client extension did. Kept apart from {@link FluidRegistry} because it names client-only classes: it is only
 * loaded on a client.
 */
final class FluidModels {
    record Entry(DeferredHolder<Fluid, ? extends Fluid> source, DeferredHolder<Fluid, ? extends Fluid> flowing, int color) {
    }

    private FluidModels() {
    }

    static void register(IEventBus modBus, List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        modBus.addListener((RegisterFluidModelsEvent event) -> {
            for (Entry entry : entries) {
                FluidModel.Unbaked model = new FluidModel.Unbaked(
                    new Material(Identifier.withDefaultNamespace("block/water_still")),
                    new Material(Identifier.withDefaultNamespace("block/water_flow")),
                    new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                    FluidTintSources.constant(0xFF000000 | entry.color()));
                event.register(model, entry.source(), entry.flowing());
            }
        });
    }
}
