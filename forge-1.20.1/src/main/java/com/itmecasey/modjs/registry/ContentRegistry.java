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

    private static final java.util.Map<String, net.minecraft.world.item.Tier> CUSTOM_TIERS = new java.util.HashMap<>();
    private static final java.util.Map<String, net.minecraft.world.item.ArmorMaterial> CUSTOM_ARMOR = new java.util.HashMap<>();
    private static final java.util.List<RegistryObject<Block>> CONTAINER_BLOCKS = new java.util.ArrayList<>();
    private static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RegistryManager.NAMESPACE);

    record TabEntry(String tab, RegistryObject<Item> item) {
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
            CUSTOM_ARMOR.put(RegistryManager.NAMESPACE + ":" + material.id(), armorMaterial(material));
        }
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
                        .saturationMod(b.saturation());
                    if (b.isAlwaysEdible()) {
                        food.alwaysEat();
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
                // The items of this tab are added by fillTabs, which also runs for ModJS tabs; adding them here too listed each twice.
                .displayItems((parameters, output) -> { })
                .build());
        }
        if (!CONTAINER_BLOCKS.isEmpty()) {
            BLOCK_ENTITIES.register("container", () -> {
                net.minecraft.world.level.block.entity.BlockEntityType<ContainerBlocks.ContainerEntity> type =
                    net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(ContainerBlocks.ContainerEntity::new,
                        CONTAINER_BLOCKS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null);
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

    private static final int[] BASE_DURABILITY = {11, 16, 15, 13}; // helmet, chestplate, leggings, boots

    private static net.minecraft.world.item.ArmorMaterial armorMaterial(ArmorMaterialBuilderJS m) {
        return new net.minecraft.world.item.ArmorMaterial() {
            @Override
            public int getDurabilityForType(net.minecraft.world.item.ArmorItem.Type type) {
                return BASE_DURABILITY[type.ordinal()] * m.durability();
            }

            @Override
            public int getDefenseForType(net.minecraft.world.item.ArmorItem.Type type) {
                return m.defense(switch (type) {
                    case CHESTPLATE -> "chestplate";
                    case LEGGINGS -> "leggings";
                    case BOOTS -> "boots";
                    default -> "helmet";
                });
            }

            @Override
            public int getEnchantmentValue() {
                return m.enchantability();
            }

            @Override
            public net.minecraft.sounds.SoundEvent getEquipSound() {
                return net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON;
            }

            @Override
            public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
                return m.repair() == null ? net.minecraft.world.item.crafting.Ingredient.EMPTY
                    : net.minecraft.world.item.crafting.Ingredient.of(BuiltInRegistries.ITEM.get(new ResourceLocation(m.repair())));
            }

            @Override
            public String getName() {
                return RegistryManager.NAMESPACE + ":" + m.id();
            }

            @Override
            public float getToughness() {
                return m.toughness();
            }

            @Override
            public float getKnockbackResistance() {
                return m.knockbackResistance();
            }
        };
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
        public int getLevel() {
            return switch (tier.level()) {
                case "wood", "gold" -> 0;
                case "stone" -> 1;
                case "diamond" -> 3;
                case "netherite" -> 4;
                default -> 2;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return tier.enchantability();
        }

        @Override
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return tier.repair() == null ? net.minecraft.world.item.crafting.Ingredient.EMPTY
                : net.minecraft.world.item.crafting.Ingredient.of(BuiltInRegistries.ITEM.get(new ResourceLocation(tier.repair())));
        }
    }

    private static Item armor(ItemBuilderJS b, Item.Properties properties) {
        net.minecraft.world.item.ArmorItem.Type type = switch (b.armorSlot()) {
            case "chestplate" -> net.minecraft.world.item.ArmorItem.Type.CHESTPLATE;
            case "leggings" -> net.minecraft.world.item.ArmorItem.Type.LEGGINGS;
            case "boots" -> net.minecraft.world.item.ArmorItem.Type.BOOTS;
            default -> net.minecraft.world.item.ArmorItem.Type.HELMET;
        };
        String custom = b.armorTier().contains(":") ? b.armorTier() : RegistryManager.NAMESPACE + ":" + b.armorTier();
        net.minecraft.world.item.ArmorMaterial customMaterial = CUSTOM_ARMOR.get(custom);
        if (customMaterial != null) {
            return new net.minecraft.world.item.ArmorItem(customMaterial, type, properties);
        }
        if (!java.util.List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite").contains(b.armorTier())) {
            throw new IllegalStateException("Item '" + b.id() + "' uses unknown armor material '" + b.armorTier() + "'");
        }
        net.minecraft.world.item.ArmorMaterial material = switch (b.armorTier()) {
            case "leather" -> net.minecraft.world.item.ArmorMaterials.LEATHER;
            case "chainmail" -> net.minecraft.world.item.ArmorMaterials.CHAIN;
            case "gold" -> net.minecraft.world.item.ArmorMaterials.GOLD;
            case "diamond" -> net.minecraft.world.item.ArmorMaterials.DIAMOND;
            case "netherite" -> net.minecraft.world.item.ArmorMaterials.NETHERITE;
            default -> net.minecraft.world.item.ArmorMaterials.IRON;
        };
        return new net.minecraft.world.item.ArmorItem(material, type, properties);
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
            case "axe" -> new net.minecraft.world.item.AxeItem(tier, 6.0f, -3.1f, properties);
            case "shovel" -> new net.minecraft.world.item.ShovelItem(tier, 1.5f, -3.0f, properties);
            case "hoe" -> new net.minecraft.world.item.HoeItem(tier, -2, -1.0f, properties);
            case "sword" -> new net.minecraft.world.item.SwordItem(tier, 3, -2.4f, properties);
            default -> new net.minecraft.world.item.PickaxeItem(tier, 1, -2.8f, properties);
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
