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
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/** Registers the fluids scripts declared: fluid type, source, flowing, liquid block and bucket. */
final class FluidRegistry {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, RegistryManager.NAMESPACE);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, RegistryManager.NAMESPACE);

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
                    return new ResourceLocation("block/water_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return new ResourceLocation("block/water_flow");
                }

                @Override
                public ResourceLocation getOverlayTexture() {
                    return new ResourceLocation("block/water_overlay");
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
            RegistryObject<FluidType> type = TYPES.register(b.id(), () -> new TintedType(FluidType.Properties.create()
                .descriptionId("fluid_type." + RegistryManager.NAMESPACE + "." + b.id())
                .density(b.density()).viscosity(b.viscosity()).temperature(b.temperature()).lightLevel(b.luminosity()), b.color()));
            RegistryObject<FlowingFluid>[] holder = newHolder();
            RegistryObject<Block> block = blocks.register(b.id(), () -> new LiquidBlock(holder[0].get(), BlockBehaviour.Properties.of()
                .noCollission().strength(100.0f).noLootTable().replaceable().liquid().lightLevel(s -> b.luminosity())));
            RegistryObject<Item> bucket = null;
            if (!b.isNoBucket()) {
                bucket = items.register(b.id() + "_bucket", () -> new BucketItem(holder[0].get(), new Item.Properties()
                    .craftRemainder(Items.BUCKET).stacksTo(1)));
                tabEntries.add(new ContentRegistry.TabEntry("minecraft:tools_and_utilities", bucket));
            }
            RegistryObject<Item> bucketRef = bucket;
            ForgeFlowingFluid.Properties[] props = new ForgeFlowingFluid.Properties[1];
            RegistryObject<FlowingFluid> source = FLUIDS.register(b.id(), () -> new ForgeFlowingFluid.Source(props[0]));
            RegistryObject<FlowingFluid> flowing = FLUIDS.register(b.id() + "_flowing", () -> new ForgeFlowingFluid.Flowing(props[0]));
            holder[0] = source;
            props[0] = new ForgeFlowingFluid.Properties(type, source, flowing).block(() -> (LiquidBlock) block.get());
            if (bucketRef != null) {
                props[0].bucket(bucketRef);
            }
        }
        TYPES.register(modBus);
        FLUIDS.register(modBus);
    }

    @SuppressWarnings("unchecked")
    private static RegistryObject<FlowingFluid>[] newHolder() {
        return (RegistryObject<FlowingFluid>[]) new RegistryObject[1];
    }
}
