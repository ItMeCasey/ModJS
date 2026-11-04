package com.itmecasey.modjs.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
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
    public static void register(BusGroup modBusGroup) {
        FluidRegistry.register(modBusGroup, BLOCKS, ITEMS, TAB_ENTRIES);
        for (BlockBuilderJS builder : RegistryManager.blocks()) {
            BlockBuilderJS b = builder;
            RegistryObject<Block> block = BLOCKS.register(b.id(), () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key(b.id()))
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
                RegistryObject<Item> item = ITEMS.register(b.id(), () ->
                    new BlockItem(block.get(), new Item.Properties().setId(ITEMS.key(b.id()))));
                TAB_ENTRIES.add(new TabEntry(b.tab(), item));
            }
        }
        for (ItemBuilderJS builder : RegistryManager.items()) {
            ItemBuilderJS b = builder;
            RegistryObject<Item> item = ITEMS.register(b.id(), () -> {
                Item.Properties properties = new Item.Properties()
                    .setId(ITEMS.key(b.id()))
                    .stacksTo(b.maxStackSize());
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
                if (b.armorSlot() != null) {
                    net.minecraft.world.item.equipment.ArmorType type = switch (b.armorSlot()) {
                        case "chestplate" -> net.minecraft.world.item.equipment.ArmorType.CHESTPLATE;
                        case "leggings" -> net.minecraft.world.item.equipment.ArmorType.LEGGINGS;
                        case "boots" -> net.minecraft.world.item.equipment.ArmorType.BOOTS;
                        default -> net.minecraft.world.item.equipment.ArmorType.HELMET;
                    };
                    net.minecraft.world.item.equipment.ArmorMaterial armorMaterial = switch (b.armorTier()) {
                        case "leather" -> net.minecraft.world.item.equipment.ArmorMaterials.LEATHER;
                        case "chainmail" -> net.minecraft.world.item.equipment.ArmorMaterials.CHAINMAIL;
                        case "gold" -> net.minecraft.world.item.equipment.ArmorMaterials.GOLD;
                        case "diamond" -> net.minecraft.world.item.equipment.ArmorMaterials.DIAMOND;
                        case "netherite" -> net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE;
                        default -> net.minecraft.world.item.equipment.ArmorMaterials.IRON;
                    };
                    properties.humanoidArmor(armorMaterial, type);
                }
                if (b.toolType() != null) {
                    net.minecraft.world.item.ToolMaterial material = switch (b.toolTier()) {
                        case "wood" -> net.minecraft.world.item.ToolMaterial.WOOD;
                        case "stone" -> net.minecraft.world.item.ToolMaterial.STONE;
                        case "gold" -> net.minecraft.world.item.ToolMaterial.GOLD;
                        case "diamond" -> net.minecraft.world.item.ToolMaterial.DIAMOND;
                        case "netherite" -> net.minecraft.world.item.ToolMaterial.NETHERITE;
                        default -> net.minecraft.world.item.ToolMaterial.IRON;
                    };
                    switch (b.toolType()) {
                        case "axe" -> properties.axe(material, 6.0f, -3.1f);
                        case "shovel" -> properties.shovel(material, 1.5f, -3.0f);
                        case "hoe" -> properties.hoe(material, -2.0f, -1.0f);
                        case "sword" -> properties.sword(material, 3.0f, -2.4f);
                        default -> properties.pickaxe(material, 1.0f, -2.8f);
                    }
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
        TABS.register(modBusGroup);
        BLOCKS.register(modBusGroup);
        ITEMS.register(modBusGroup);
        BuildCreativeModeTabContentsEvent.BUS.addListener(ContentRegistry::fillTabs);
    }

    private static ItemStack iconFor(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? Items.STONE : BuiltInRegistries.ITEM.getValue(id);
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
        String tabId = key.identifier().toString();
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
