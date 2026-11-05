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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;

/** Registers the items and blocks scripts declared, under the {@code modjs} namespace. */
public final class ContentRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, RegistryManager.NAMESPACE);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, RegistryManager.NAMESPACE);

    private static final java.util.List<DeferredHolder<Block, ? extends Block>> CONTAINER_BLOCKS = new java.util.ArrayList<>();
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RegistryManager.NAMESPACE);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RegistryManager.NAMESPACE);

    record TabEntry(String tab, DeferredHolder<Item, ? extends Item> item) {
    }

    private static final List<TabEntry> TAB_ENTRIES = new ArrayList<>();

    private ContentRegistry() {
    }

    /** Turns the collected builders into registry entries; call from the mod constructor after {@link RegistryManager#collect()}. */
    public static void register(IEventBus modIEventBus) {
        FluidRegistry.register(modIEventBus, BLOCKS, ITEMS, TAB_ENTRIES);
        for (BlockBuilderJS builder : RegistryManager.blocks()) {
            BlockBuilderJS b = builder;
            DeferredHolder<Block, ? extends Block> block = BLOCKS.register(b.id(), () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                    .setId(net.minecraft.resources.ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, b.id())))
                    .strength(b.hardness(), b.resistance())
                    .lightLevel(state -> b.lightLevel());
                if (b.requiresTool()) {
                    properties.requiresCorrectToolForDrops();
                }
                if (b.containerRows() > 0) {
                    return new ContainerBlocks.ContainerBlock(properties, b.containerRows());
                }
                return switch (b.type()) {
                    case "slab" -> new net.minecraft.world.level.block.SlabBlock(properties);
                    case "stairs" -> new net.minecraft.world.level.block.StairBlock(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), properties);
                    case "wall" -> new net.minecraft.world.level.block.WallBlock(properties);
                    case "fence" -> new net.minecraft.world.level.block.FenceBlock(properties);
                    default -> new Block(properties);
                };
            });
            if (b.containerRows() > 0) {
                CONTAINER_BLOCKS.add(block);
            }
            if (!b.isNoItem()) {
                DeferredHolder<Item, ? extends Item> item = ITEMS.register(b.id(), () ->
                    new BlockItem(block.get(), new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, b.id())))));
                TAB_ENTRIES.add(new TabEntry(b.tab(), item));
            }
        }
        for (ItemBuilderJS builder : RegistryManager.items()) {
            ItemBuilderJS b = builder;
            DeferredHolder<Item, ? extends Item> item = ITEMS.register(b.id(), () -> {
                Item.Properties properties = new Item.Properties()
                    .setId(net.minecraft.resources.ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, b.id())))
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
                    net.minecraft.world.item.equipment.ArmorMaterial armorMaterial = customArmor(b.armorTier());
                    if (armorMaterial == null) {
                        armorMaterial = switch (b.armorTier()) {
                        case "leather" -> net.minecraft.world.item.equipment.ArmorMaterials.LEATHER;
                        case "chainmail" -> net.minecraft.world.item.equipment.ArmorMaterials.CHAINMAIL;
                        case "gold" -> net.minecraft.world.item.equipment.ArmorMaterials.GOLD;
                        case "diamond" -> net.minecraft.world.item.equipment.ArmorMaterials.DIAMOND;
                        case "netherite" -> net.minecraft.world.item.equipment.ArmorMaterials.NETHERITE;
                        default -> net.minecraft.world.item.equipment.ArmorMaterials.IRON;
                        };
                    }
                    properties.humanoidArmor(armorMaterial, type);
                }
                if (b.toolType() != null) {
                    net.minecraft.world.item.ToolMaterial material = customTool(b.toolTier());
                    if (material == null) {
                        material = switch (b.toolTier()) {
                        case "wood" -> net.minecraft.world.item.ToolMaterial.WOOD;
                        case "stone" -> net.minecraft.world.item.ToolMaterial.STONE;
                        case "gold" -> net.minecraft.world.item.ToolMaterial.GOLD;
                        case "diamond" -> net.minecraft.world.item.ToolMaterial.DIAMOND;
                        case "netherite" -> net.minecraft.world.item.ToolMaterial.NETHERITE;
                        default -> net.minecraft.world.item.ToolMaterial.IRON;
                        };
                    }
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
                // The items of this tab are added by fillTabs, which also runs for ModJS tabs; adding them here too listed each twice.
                .displayItems((parameters, output) -> { })
                .build());
        }
        if (!CONTAINER_BLOCKS.isEmpty()) {
            BLOCK_ENTITIES.register("container", () -> {
                net.minecraft.world.level.block.entity.BlockEntityType<ContainerBlocks.ContainerEntity> type =
                    new net.minecraft.world.level.block.entity.BlockEntityType<>(ContainerBlocks.ContainerEntity::new,
                        CONTAINER_BLOCKS.stream().map(DeferredHolder::get).collect(java.util.stream.Collectors.toSet()));
                ContainerBlocks.setType(type);
                return type;
            });
            BLOCK_ENTITIES.register(modIEventBus);
        }
        TABS.register(modIEventBus);
        BLOCKS.register(modIEventBus);
        ITEMS.register(modIEventBus);
        modIEventBus.addListener(ContentRegistry::fillTabs);
    }

    private static ItemStack iconFor(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? Items.STONE : BuiltInRegistries.ITEM.getValue(id);
        return new ItemStack(item);
    }

    private static net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> incorrectFor(String level) {
        return switch (level) {
            case "wood" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case "stone" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_STONE_TOOL;
            case "gold" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_GOLD_TOOL;
            case "diamond" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
            case "netherite" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
            default -> net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL;
        };
    }

    /** The script-defined tool material, or null when {@code name} is a vanilla tier name. */
    private static net.minecraft.world.item.ToolMaterial customTool(String name) {
        String full = name.contains(":") ? name : RegistryManager.NAMESPACE + ":" + name;
        for (ToolTierBuilderJS tier : RegistryManager.toolTiers()) {
            if ((RegistryManager.NAMESPACE + ":" + tier.id()).equals(full)) {
                return new net.minecraft.world.item.ToolMaterial(incorrectFor(tier.level()), tier.uses(), tier.speed(), tier.attackDamage(),
                    tier.enchantability(), net.minecraft.tags.TagKey.create(Registries.ITEM,
                        Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, "repairs_tool_" + tier.id())));
            }
        }
        if (!List.of("wood", "stone", "iron", "gold", "diamond", "netherite").contains(name)) {
            throw new IllegalStateException("Unknown tool tier '" + name + "'");
        }
        return null;
    }

    /** The script-defined armor material, or null when {@code name} is a vanilla material name. */
    private static net.minecraft.world.item.equipment.ArmorMaterial customArmor(String name) {
        String full = name.contains(":") ? name : RegistryManager.NAMESPACE + ":" + name;
        for (ArmorMaterialBuilderJS m : RegistryManager.armorMaterials()) {
            if ((RegistryManager.NAMESPACE + ":" + m.id()).equals(full)) {
                java.util.Map<net.minecraft.world.item.equipment.ArmorType, Integer> defense = new java.util.EnumMap<>(net.minecraft.world.item.equipment.ArmorType.class);
                defense.put(net.minecraft.world.item.equipment.ArmorType.HELMET, m.defense("helmet"));
                defense.put(net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, m.defense("chestplate"));
                defense.put(net.minecraft.world.item.equipment.ArmorType.LEGGINGS, m.defense("leggings"));
                defense.put(net.minecraft.world.item.equipment.ArmorType.BOOTS, m.defense("boots"));
                return new net.minecraft.world.item.equipment.ArmorMaterial(m.durability(), defense, m.enchantability(),
                    net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON, m.toughness(), m.knockbackResistance(),
                    net.minecraft.tags.TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, "repairs_armor_" + m.id())),
                    ResourceKey.create(net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(RegistryManager.NAMESPACE, m.id())));
            }
        }
        if (!List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite").contains(name)) {
            throw new IllegalStateException("Unknown armor material '" + name + "'");
        }
        return null;
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
