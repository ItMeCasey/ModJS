package com.itmecasey.modjs.registry;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * Forge 26 draws fluids from fluid models. A script fluid gets the vanilla water textures tinted with its color, which is what
 * the older client extension did. Kept apart from {@link FluidRegistry} because it names client-only classes: it is only
 * loaded on a client.
 */
final class FluidModels {
    record Entry(RegistryObject<? extends Fluid> source, RegistryObject<? extends Fluid> flowing, int color) {
    }

    private FluidModels() {
    }

    static void register(BusGroup modBus, List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        ModelEvent.BakeFluidModels.BUS.addListener(event -> {
            for (Entry entry : entries) {
                int color = 0xFF000000 | entry.color();
                BlockTintSource tint = new BlockTintSource() {
                    @Override
                    public int color(BlockState state) {
                        return color;
                    }
                };
                FluidModel.Unbaked model = new FluidModel.Unbaked(
                    new Material(Identifier.withDefaultNamespace("block/water_still")),
                    new Material(Identifier.withDefaultNamespace("block/water_flow")),
                    new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                    tint);
                FluidModel baked = model.bake(event.materials(), () -> "modjs fluid");
                event.register(entry.source().get(), baked);
                event.register(entry.flowing().get(), baked);
            }
        });
    }
}
