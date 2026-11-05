package com.itmecasey.modjs.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Consumer;

/** Registers the fluids scripts declared: fluid type, source, flowing, liquid block and bucket. */
final class FluidRegistry {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, RegistryManager.NAMESPACE);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, RegistryManager.NAMESPACE);

    private static final java.util.Map<String, DeferredHolder<FluidType, ? extends FluidType>> TYPE_HOLDERS = new java.util.HashMap<>();

    private FluidRegistry() {
    }

    /** The fluid type; its client look is registered separately (see {@link #tintedExtensions}). */
    private static final class TintedType extends FluidType {
        final int tint;

        TintedType(Properties properties, int tint) {
            super(properties);
            this.tint = tint;
        }
    }

    // NeoForge 26.1.2 draws fluids from fluid models rather than the old client extension texture methods; the vanilla
    // water look is not wired up here yet, so a script fluid renders with the loader's default until that is done.

    static void register(IEventBus modBus, DeferredRegister<Block> blocks, DeferredRegister<Item> items, java.util.List<ContentRegistry.TabEntry> tabEntries) {
        for (FluidBuilderJS builder : RegistryManager.fluids()) {
            FluidBuilderJS b = builder;
            DeferredHolder<FluidType, ? extends FluidType> type = TYPES.register(b.id(), () -> new TintedType(FluidType.Properties.create()
                .descriptionId("fluid_type." + RegistryManager.NAMESPACE + "." + b.id())
                .density(b.density()).viscosity(b.viscosity()).temperature(b.temperature()).lightLevel(b.luminosity()), b.color()));
            DeferredHolder<Fluid, ? extends Fluid>[] holder = newHolder();
            DeferredHolder<Block, ? extends Block> block = blocks.register(b.id(), () -> new LiquidBlock((FlowingFluid) holder[0].get(), BlockBehaviour.Properties.of().setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, b.id())))
                .noCollision().strength(100.0f).noLootTable().replaceable().liquid().lightLevel(s -> b.luminosity())));
            DeferredHolder<Item, ? extends Item> bucket = null;
            if (!b.isNoBucket()) {
                bucket = items.register(b.id() + "_bucket", () -> new BucketItem(holder[0].get(), new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, b.id() + "_bucket")))
                    .craftRemainder(Items.BUCKET).stacksTo(1)));
                tabEntries.add(new ContentRegistry.TabEntry("minecraft:tools_and_utilities", bucket));
            }
            DeferredHolder<Item, ? extends Item> bucketRef = bucket;
            BaseFlowingFluid.Properties[] props = new BaseFlowingFluid.Properties[1];
            DeferredHolder<Fluid, ? extends Fluid> source = FLUIDS.register(b.id(), () -> new BaseFlowingFluid.Source(props[0]));
            DeferredHolder<Fluid, ? extends Fluid> flowing = FLUIDS.register(b.id() + "_flowing", () -> new BaseFlowingFluid.Flowing(props[0]));
            holder[0] = source;
            TYPE_HOLDERS.put(b.id(), type);
            props[0] = new BaseFlowingFluid.Properties(type, source, flowing).block(() -> (LiquidBlock) block.get());
            if (bucketRef != null) {
                props[0].bucket(bucketRef);
            }
        }
        TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    @SuppressWarnings("unchecked")
    private static DeferredHolder<Fluid, ? extends Fluid>[] newHolder() {
        return (DeferredHolder<Fluid, ? extends Fluid>[]) new DeferredHolder[1];
    }
}
