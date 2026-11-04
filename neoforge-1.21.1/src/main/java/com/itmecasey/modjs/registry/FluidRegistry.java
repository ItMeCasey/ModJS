package com.itmecasey.modjs.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockAndTintGetter;
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

    private FluidRegistry() {
    }

    /** Tint-only client extension using the vanilla water textures. */
    private static final class TintedType extends FluidType {
        private final int tint;

        TintedType(Properties properties, int tint) {
            super(properties);
            this.tint = tint;
        }

        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return ResourceLocation.withDefaultNamespace("block/water_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return ResourceLocation.withDefaultNamespace("block/water_flow");
                }

                @Override
                public ResourceLocation getOverlayTexture() {
                    return ResourceLocation.withDefaultNamespace("block/water_overlay");
                }

                @Override
                public int getTintColor() {
                    return tint;
                }

                @Override
                public int getTintColor(net.minecraft.world.level.material.FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                    return tint;
                }
            });
        }
    }

    static void register(IEventBus modBus, DeferredRegister<Block> blocks, DeferredRegister<Item> items, java.util.List<ContentRegistry.TabEntry> tabEntries) {
        for (FluidBuilderJS builder : RegistryManager.fluids()) {
            FluidBuilderJS b = builder;
            DeferredHolder<FluidType, ? extends FluidType> type = TYPES.register(b.id(), () -> new TintedType(FluidType.Properties.create()
                .descriptionId("fluid_type." + RegistryManager.NAMESPACE + "." + b.id())
                .density(b.density()).viscosity(b.viscosity()).temperature(b.temperature()).lightLevel(b.luminosity()), b.color()));
            DeferredHolder<Fluid, ? extends Fluid>[] holder = newHolder();
            DeferredHolder<Block, ? extends Block> block = blocks.register(b.id(), () -> new LiquidBlock((FlowingFluid) holder[0].get(), BlockBehaviour.Properties.of()
                .noCollission().strength(100.0f).noLootTable().replaceable().liquid().lightLevel(s -> b.luminosity())));
            DeferredHolder<Item, ? extends Item> bucket = null;
            if (!b.isNoBucket()) {
                bucket = items.register(b.id() + "_bucket", () -> new BucketItem(holder[0].get(), new Item.Properties()
                    .craftRemainder(Items.BUCKET).stacksTo(1)));
                tabEntries.add(new ContentRegistry.TabEntry("minecraft:tools_and_utilities", bucket));
            }
            DeferredHolder<Item, ? extends Item> bucketRef = bucket;
            BaseFlowingFluid.Properties[] props = new BaseFlowingFluid.Properties[1];
            DeferredHolder<Fluid, ? extends Fluid> source = FLUIDS.register(b.id(), () -> new BaseFlowingFluid.Source(props[0]));
            DeferredHolder<Fluid, ? extends Fluid> flowing = FLUIDS.register(b.id() + "_flowing", () -> new BaseFlowingFluid.Flowing(props[0]));
            holder[0] = source;
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
