package com.itmecasey.modjs.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Registers the items and blocks scripts declared, under the {@code modjs} namespace. */
public final class ContentRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RegistryManager.NAMESPACE);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RegistryManager.NAMESPACE);

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RegistryManager.NAMESPACE);

    private record TabEntry(String tab, RegistryObject<Item> item) {
    }

    private static final List<TabEntry> TAB_ENTRIES = new ArrayList<>();

    private ContentRegistry() {
    }

    /** Turns the collected builders into registry entries; call from the mod constructor after {@link RegistryManager#collect()}. */
    public static void register(IEventBus modBus) {
        for (BlockBuilderJS builder : RegistryManager.blocks()) {
            BlockBuilderJS b = builder;
            RegistryObject<Block> block = BLOCKS.register(b.id(), () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .strength(b.hardness(), b.resistance())
                    .lightLevel(state -> b.lightLevel());
                if (b.requiresTool()) {
                    properties.requiresCorrectToolForDrops();
                }
                return new Block(properties);
            });
            if (!b.isNoItem()) {
                RegistryObject<Item> item = ITEMS.register(b.id(), () -> new BlockItem(block.get(), new Item.Properties()));
                TAB_ENTRIES.add(new TabEntry(b.tab(), item));
            }
        }
        for (ItemBuilderJS builder : RegistryManager.items()) {
            ItemBuilderJS b = builder;
            RegistryObject<Item> item = ITEMS.register(b.id(), () -> {
                Item.Properties properties = new Item.Properties().stacksTo(b.maxStackSize());
                if (b.rarity() != null) {
                    properties.rarity(rarity(b.rarity()));
                }
                if (b.nutrition() != null) {
                    FoodProperties.Builder food = new FoodProperties.Builder()
                        .nutrition(b.nutrition())
                        .saturationModifier(b.saturation());
                    if (b.isAlwaysEdible()) {
                        food.alwaysEdible();
                    }
                    properties.food(food.build());
                }
                return new Item(properties);
            });
            TAB_ENTRIES.add(new TabEntry(b.tab(), item));
        }
        for (CreativeTabBuilderJS builder : RegistryManager.tabs()) {
            CreativeTabBuilderJS t = builder;
            TABS.register(t.id(), () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + RegistryManager.NAMESPACE + "." + t.id()))
                .icon(() -> iconFor(t.icon()))
                .displayItems((parameters, output) -> {
                    for (TabEntry entry : TAB_ENTRIES) {
                        if (entry.tab().equals(t.tabId())) {
                            output.accept(entry.item().get());
                        }
                    }
                })
                .build());
        }
        TABS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ContentRegistry::fillTabs);
    }

    private static ItemStack iconFor(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? Items.STONE : BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item);
    }

    private static Rarity rarity(String name) {
        return switch (name.toLowerCase()) {
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case "epic" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    private static void fillTabs(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> key = event.getTabKey();
        String tabId = key.location().toString();
        for (TabEntry entry : TAB_ENTRIES) {
            if (entry.tab().equals(tabId)) {
                event.accept(entry.item().get());
            }
        }
    }
}
