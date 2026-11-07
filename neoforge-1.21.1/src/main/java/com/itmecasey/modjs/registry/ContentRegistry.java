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
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Registers the items and blocks scripts declared, under the {@code modjs} namespace. */
public final class ContentRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, RegistryManager.NAMESPACE);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, RegistryManager.NAMESPACE);

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RegistryManager.NAMESPACE);

    private static final java.util.Map<String, net.minecraft.world.item.Tier> CUSTOM_TIERS = new java.util.HashMap<>();
    private static final java.util.Map<String, DeferredHolder<net.minecraft.world.item.ArmorMaterial, ? extends net.minecraft.world.item.ArmorMaterial>> CUSTOM_ARMOR = new java.util.HashMap<>();
    private static final java.util.List<DeferredHolder<Block, ? extends Block>> CONTAINER_BLOCKS = new java.util.ArrayList<>();
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RegistryManager.NAMESPACE);
    private static final DeferredRegister<net.minecraft.world.item.ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, RegistryManager.NAMESPACE);

    record TabEntry(String tab, DeferredHolder<Item, ? extends Item> item) {
    }

    private static final List<TabEntry> TAB_ENTRIES = new ArrayList<>();

    private ContentRegistry() {
    }

    /** Turns the collected builders into registry entries; call from the mod constructor after {@link RegistryManager#collect()}. */
    public static void register(IEventBus modBus) {
        for (ToolTierBuilderJS tier : RegistryManager.toolTiers()) {
            CUSTOM_TIERS.put(RegistryManager.NAMESPACE + ":" + tier.id(), new CustomTier(tier));
        }
        for (ArmorMaterialBuilderJS material : RegistryManager.armorMaterials()) {
            CUSTOM_ARMOR.put(RegistryManager.NAMESPACE + ":" + material.id(), ARMOR_MATERIALS.register(material.id(), () -> armorMaterial(material)));
        }
        ARMOR_MATERIALS.register(modBus);
        FluidRegistry.register(modBus, BLOCKS, ITEMS, TAB_ENTRIES);
        for (BlockBuilderJS builder : RegistryManager.blocks()) {
            BlockBuilderJS b = builder;
            DeferredHolder<Block, ? extends Block> block = BLOCKS.register(b.id(), () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
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
                DeferredHolder<Item, ? extends Item> item = ITEMS.register(b.id(), () -> new BlockItem(block.get(), new Item.Properties()));
                TAB_ENTRIES.add(new TabEntry(b.tab(), item));
            }
        }
        for (ItemBuilderJS builder : RegistryManager.items()) {
            ItemBuilderJS b = builder;
            DeferredHolder<Item, ? extends Item> item = ITEMS.register(b.id(), () -> {
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
                if (b.glow()) {
                    return new Item(properties) {
                        @Override
                        public boolean isFoil(net.minecraft.world.item.ItemStack stack) {
                            return true;
                        }
                    };
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
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(ContainerBlocks.ContainerEntity::new,
                        CONTAINER_BLOCKS.stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null);
                ContainerBlocks.setType(type);
                return type;
            });
            BLOCK_ENTITIES.register(modBus);
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

    private static net.minecraft.world.item.ArmorMaterial armorMaterial(ArmorMaterialBuilderJS m) {
        java.util.Map<net.minecraft.world.item.ArmorItem.Type, Integer> defense = new java.util.EnumMap<>(net.minecraft.world.item.ArmorItem.Type.class);
        defense.put(net.minecraft.world.item.ArmorItem.Type.HELMET, m.defense("helmet"));
        defense.put(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, m.defense("chestplate"));
        defense.put(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, m.defense("leggings"));
        defense.put(net.minecraft.world.item.ArmorItem.Type.BOOTS, m.defense("boots"));
        return new net.minecraft.world.item.ArmorMaterial(defense, m.enchantability(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON,
            () -> m.repair() == null ? net.minecraft.world.item.crafting.Ingredient.EMPTY
                : net.minecraft.world.item.crafting.Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(m.repair()))),
            java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(RegistryManager.NAMESPACE, m.id()))),
            m.toughness(), m.knockbackResistance());
    }

    /** A script-defined tool tier. */
    private static final class CustomTier implements net.minecraft.world.item.Tier {
        private final ToolTierBuilderJS tier;

        CustomTier(ToolTierBuilderJS tier) {
            this.tier = tier;
        }

        @Override
        public int getUses() {
            return tier.uses();
        }

        @Override
        public float getSpeed() {
            return tier.speed();
        }

        @Override
        public float getAttackDamageBonus() {
            return tier.attackDamage();
        }

        @Override
        public net.minecraft.tags.TagKey<Block> getIncorrectBlocksForDrops() {
            return switch (tier.level()) {
                case "wood" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_WOODEN_TOOL;
                case "stone" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_STONE_TOOL;
                case "gold" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_GOLD_TOOL;
                case "diamond" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
                case "netherite" -> net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
                default -> net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return tier.enchantability();
        }

        @Override
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return tier.repair() == null ? net.minecraft.world.item.crafting.Ingredient.EMPTY
                : net.minecraft.world.item.crafting.Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(tier.repair())));
        }
    }

    private static Item armor(ItemBuilderJS b, Item.Properties properties) {
        String custom = b.armorTier().contains(":") ? b.armorTier() : RegistryManager.NAMESPACE + ":" + b.armorTier();
        DeferredHolder<net.minecraft.world.item.ArmorMaterial, ? extends net.minecraft.world.item.ArmorMaterial> customMaterial = CUSTOM_ARMOR.get(custom);
        if (customMaterial != null) {
            net.minecraft.world.item.ArmorItem.Type type = switch (b.armorSlot()) {
                case "chestplate" -> net.minecraft.world.item.ArmorItem.Type.CHESTPLATE;
                case "leggings" -> net.minecraft.world.item.ArmorItem.Type.LEGGINGS;
                case "boots" -> net.minecraft.world.item.ArmorItem.Type.BOOTS;
                default -> net.minecraft.world.item.ArmorItem.Type.HELMET;
            };
            int multiplier = RegistryManager.armorMaterials().stream().filter(m -> (RegistryManager.NAMESPACE + ":" + m.id()).equals(custom))
                .findFirst().map(ArmorMaterialBuilderJS::durability).orElse(15);
            // the registry may not have the material yet when items register, so fall back to a direct holder
            net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> holder = customMaterial;
            return new net.minecraft.world.item.ArmorItem(holder, type, properties.durability(type.getDurability(multiplier)));
        }
        if (!java.util.List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite").contains(b.armorTier())) {
            throw new IllegalStateException("Item '" + b.id() + "' uses unknown armor material '" + b.armorTier() + "'");
        }
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
        String custom = b.toolTier().contains(":") ? b.toolTier() : RegistryManager.NAMESPACE + ":" + b.toolTier();
        net.minecraft.world.item.Tier customTier = CUSTOM_TIERS.get(custom);
        if (customTier == null && !java.util.List.of("wood", "stone", "iron", "gold", "diamond", "netherite").contains(b.toolTier())) {
            throw new IllegalStateException("Item '" + b.id() + "' uses unknown tool tier '" + b.toolTier() + "'");
        }
        net.minecraft.world.item.Tier tier = customTier != null ? customTier : switch (b.toolTier()) {
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
