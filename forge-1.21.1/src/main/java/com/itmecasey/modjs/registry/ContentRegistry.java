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

    record TabEntry(String tab, RegistryObject<Item> item) {
    }

    private static final List<TabEntry> TAB_ENTRIES = new ArrayList<>();

    private ContentRegistry() {
    }

    /** Turns the collected builders into registry entries; call from the mod constructor after {@link RegistryManager#collect()}. */
    public static void register(IEventBus modBus) {
        FluidRegistry.register(modBus, BLOCKS, ITEMS, TAB_ENTRIES);
        for (BlockBuilderJS builder : RegistryManager.blocks()) {
            BlockBuilderJS b = builder;
            RegistryObject<Block> block = BLOCKS.register(b.id(), () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .strength(b.hardness(), b.resistance())
                    .lightLevel(state -> b.lightLevel());
                if (b.requiresTool()) {
                    properties.requiresCorrectToolForDrops();
                }
                return switch (b.type()) {
                    case "slab" -> new net.minecraft.world.level.block.SlabBlock(properties);
                    case "stairs" -> new net.minecraft.world.level.block.StairBlock(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), properties);
                    case "wall" -> new net.minecraft.world.level.block.WallBlock(properties);
                    case "fence" -> new net.minecraft.world.level.block.FenceBlock(properties);
                    default -> new Block(properties);
                };
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
                if (b.toolType() != null) {
                    return tool(b, properties);
                }
                if (b.armorSlot() != null) {
                    return armor(b, properties);
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

    private static Item armor(ItemBuilderJS b, Item.Properties properties) {
        net.minecraft.world.item.ArmorItem.Type type = switch (b.armorSlot()) {
            case "chestplate" -> net.minecraft.world.item.ArmorItem.Type.CHESTPLATE;
            case "leggings" -> net.minecraft.world.item.ArmorItem.Type.LEGGINGS;
            case "boots" -> net.minecraft.world.item.ArmorItem.Type.BOOTS;
            default -> net.minecraft.world.item.ArmorItem.Type.HELMET;
        };
        net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material = switch (b.armorTier()) {
            case "leather" -> net.minecraft.world.item.ArmorMaterials.LEATHER;
            case "chainmail" -> net.minecraft.world.item.ArmorMaterials.CHAIN;
            case "gold" -> net.minecraft.world.item.ArmorMaterials.GOLD;
            case "diamond" -> net.minecraft.world.item.ArmorMaterials.DIAMOND;
            case "netherite" -> net.minecraft.world.item.ArmorMaterials.NETHERITE;
            default -> net.minecraft.world.item.ArmorMaterials.IRON;
        };
        int multiplier = switch (b.armorTier()) {
            case "leather" -> 5;
            case "chainmail", "iron" -> 15;
            case "gold" -> 7;
            case "diamond" -> 33;
            default -> 37;
        };
        return new net.minecraft.world.item.ArmorItem(material, type, properties.durability(type.getDurability(multiplier)));
    }

    private static Item tool(ItemBuilderJS b, Item.Properties properties) {
        net.minecraft.world.item.Tier tier = switch (b.toolTier()) {
            case "wood" -> net.minecraft.world.item.Tiers.WOOD;
            case "stone" -> net.minecraft.world.item.Tiers.STONE;
            case "gold" -> net.minecraft.world.item.Tiers.GOLD;
            case "diamond" -> net.minecraft.world.item.Tiers.DIAMOND;
            case "netherite" -> net.minecraft.world.item.Tiers.NETHERITE;
            default -> net.minecraft.world.item.Tiers.IRON;
        };
        return switch (b.toolType()) {
            case "axe" -> new net.minecraft.world.item.AxeItem(tier, properties.attributes(net.minecraft.world.item.AxeItem.createAttributes(tier, 6.0f, -3.1f)));
            case "shovel" -> new net.minecraft.world.item.ShovelItem(tier, properties.attributes(net.minecraft.world.item.ShovelItem.createAttributes(tier, 1.5f, -3.0f)));
            case "hoe" -> new net.minecraft.world.item.HoeItem(tier, properties.attributes(net.minecraft.world.item.HoeItem.createAttributes(tier, -2.0f, -1.0f)));
            case "sword" -> new net.minecraft.world.item.SwordItem(tier, properties.attributes(net.minecraft.world.item.SwordItem.createAttributes(tier, 3, -2.4f)));
            default -> new net.minecraft.world.item.PickaxeItem(tier, properties.attributes(net.minecraft.world.item.PickaxeItem.createAttributes(tier, 1.0f, -2.8f)));
        };
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
        if (com.itmecasey.modjs.event.EventGroups.STARTUP.hasHandlers("modifyCreativeTab")) {
            ModifyTabEventJS script = new ModifyTabEventJS(tabId);
            com.itmecasey.modjs.event.EventGroups.STARTUP.post("modifyCreativeTab", tabId, script);
            for (String itemId : script.added()) {
                event.accept(iconFor(itemId).getItem());
            }
        }
    }
}
