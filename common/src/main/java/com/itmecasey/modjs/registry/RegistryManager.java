package com.itmecasey.modjs.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.data.DataBuilder;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.pack.GeneratedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects the content scripts declare in startup scripts and writes the assets and data it needs (models,
 * blockstates, lang, block loot tables). The actual game registration lives in {@code ContentRegistry}.
 */
public final class RegistryManager {
    public static final String NAMESPACE = "modjs";

    private static final Map<String, ItemBuilderJS> ITEMS = new LinkedHashMap<>();
    private static final Map<String, BlockBuilderJS> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, ToolTierBuilderJS> TIERS = new LinkedHashMap<>();
    private static final Map<String, ArmorMaterialBuilderJS> ARMOR_MATERIALS = new LinkedHashMap<>();
    private static final Map<String, FluidBuilderJS> FLUIDS = new LinkedHashMap<>();
    private static final Map<String, CreativeTabBuilderJS> TABS = new LinkedHashMap<>();
    private static final Map<String, com.itmecasey.modjs.unify.UnifyMaterials.Entry> UNIFY_ENTRIES = new LinkedHashMap<>();
    private static boolean contributorsInstalled;
    private static boolean UNIFY_FILE_READ;

    private RegistryManager() {
    }

    /** Posts the registry events. Call once, after startup scripts have loaded. */
    public static synchronized void collect() {
        ITEMS.clear();
        BLOCKS.clear();
        TABS.clear();
        FLUIDS.clear();
        TIERS.clear();
        ARMOR_MATERIALS.clear();
        UNIFY_ENTRIES.clear();
        com.itmecasey.modjs.item.ItemModifications.clear();
        com.itmecasey.modjs.block.BlockModifications.clear();
        EventGroups.BLOCK.post("modification", new com.itmecasey.modjs.block.BlockModifications.ModificationEventJS());
        EventGroups.ITEM.post("modification", new com.itmecasey.modjs.item.ItemModifications.ModificationEventJS());
        EventGroups.STARTUP.postExact("registry", "creative_tab", new RegistryEventJS<>("creative tab", TABS, CreativeTabBuilderJS::new));
        EventGroups.STARTUP.postExact("registry", "tool_tier", new RegistryEventJS<>("tool tier", TIERS, ToolTierBuilderJS::new));
        EventGroups.STARTUP.postExact("registry", "armor_material", new RegistryEventJS<>("armor material", ARMOR_MATERIALS, ArmorMaterialBuilderJS::new));
        EventGroups.STARTUP.postExact("registry", "fluid", new RegistryEventJS<>("fluid", FLUIDS, FluidBuilderJS::new));
        EventGroups.STARTUP.postExact("registry", "block", new RegistryEventJS<>("block", BLOCKS, BlockBuilderJS::new));
        EventGroups.STARTUP.postExact("registry", "item", new RegistryEventJS<>("item", ITEMS, ItemBuilderJS::new));
        if (!UNIFY_FILE_READ) {
            UNIFY_FILE_READ = true;
            com.itmecasey.modjs.unify.UnifyFile.applyStartup(com.itmecasey.modjs.script.ScriptManager.root());
        }
        addUnifiedMaterials();
        for (ItemBuilderJS item : ITEMS.values()) {
            if (item.unifyTag() != null) {
                com.itmecasey.modjs.unify.UnifyConfig.addCanonicalItem(item.unifyTag(), NAMESPACE + ":" + item.id());
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            if (block.unifyTag() != null) {
                com.itmecasey.modjs.unify.UnifyConfig.addCanonicalItem(block.unifyTag(), NAMESPACE + ":" + block.id());
            }
        }
        for (FluidBuilderJS fluid : FLUIDS.values()) {
            if (fluid.unifyTag() != null) {
                com.itmecasey.modjs.unify.UnifyConfig.addCanonicalFluid(fluid.unifyTag(), NAMESPACE + ":" + fluid.id());
            }
        }
        if (!contributorsInstalled) {
            contributorsInstalled = true;
            DataBuilder.addClientContributor(RegistryManager::writeAssets);
            DataBuilder.addServerContributor(RegistryManager::writeData);
        }
    }

    /** Registers the canonical items {@code Unify.materials} asked for, unless a script already made the same id. */
    private static void addUnifiedMaterials() {
        for (com.itmecasey.modjs.unify.UnifyMaterials.Spec spec : com.itmecasey.modjs.unify.UnifyConfig.materialSpecs()) {
            for (com.itmecasey.modjs.unify.UnifyMaterials.Entry entry : com.itmecasey.modjs.unify.UnifyMaterials.expand(spec)) {
                if (ITEMS.containsKey(entry.id()) || BLOCKS.containsKey(entry.id())) {
                    continue;
                }
                if (entry.block()) {
                    BlockBuilderJS block = new BlockBuilderJS(entry.id()).displayName(entry.displayName()).hardness(3).resistance(6).unify(entry.tag());
                    if (entry.dropId() != null) {
                        block.drops(entry.dropId()).requiresTool(true);
                    }
                    BLOCKS.put(entry.id(), block);
                } else {
                    ItemBuilderJS item = new ItemBuilderJS(entry.id()).displayName(entry.displayName()).unify(entry.tag());
                    if (entry.kind().equals("rod")) {
                        item.parentModel("minecraft:item/handheld");
                    }
                    ITEMS.put(entry.id(), item);
                }
                UNIFY_ENTRIES.put(entry.id(), entry);
            }
        }
    }

    public static Collection<ItemBuilderJS> items() {
        return ITEMS.values();
    }

    public static Collection<BlockBuilderJS> blocks() {
        return BLOCKS.values();
    }

    public static Collection<ToolTierBuilderJS> toolTiers() {
        return TIERS.values();
    }

    public static Collection<ArmorMaterialBuilderJS> armorMaterials() {
        return ARMOR_MATERIALS.values();
    }

    public static Collection<FluidBuilderJS> fluids() {
        return FLUIDS.values();
    }

    public static Collection<CreativeTabBuilderJS> tabs() {
        return TABS.values();
    }

    static void writeAssets(GeneratedData data) {
        JsonObject lang = new JsonObject();
        for (ItemBuilderJS item : ITEMS.values()) {
            lang.addProperty("item." + NAMESPACE + "." + item.id(), item.displayName());
            if (VersionFormat.current().usesItemDefinitions()) {
                writeItemDefinition(data, item.id(), NAMESPACE + ":item/" + item.id());
            }
            JsonObject model = new JsonObject();
            model.addProperty("parent", item.parentModel());
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", item.texture());
            model.add("textures", textures);
            data.putJson(NAMESPACE, "models/item/" + item.id(), model);
        }
        for (CreativeTabBuilderJS tab : TABS.values()) {
            lang.addProperty("itemGroup." + NAMESPACE + "." + tab.id(), tab.displayName());
        }
        for (FluidBuilderJS fluid : FLUIDS.values()) {
            lang.addProperty("block." + NAMESPACE + "." + fluid.id(), fluid.displayName());
            lang.addProperty("fluid_type." + NAMESPACE + "." + fluid.id(), fluid.displayName());
            JsonObject particle = new JsonObject();
            JsonObject particleTextures = new JsonObject();
            particleTextures.addProperty("particle", "minecraft:block/water_still");
            particle.add("textures", particleTextures);
            data.putJson(NAMESPACE, "models/block/" + fluid.id(), particle);
            JsonObject variant = new JsonObject();
            variant.addProperty("model", NAMESPACE + ":block/" + fluid.id());
            JsonObject variants = new JsonObject();
            variants.add("", variant);
            JsonObject state = new JsonObject();
            state.add("variants", variants);
            data.putJson(NAMESPACE, "blockstates/" + fluid.id(), state);
            if (!fluid.isNoBucket()) {
                lang.addProperty("item." + NAMESPACE + "." + fluid.id() + "_bucket", fluid.displayName() + " Bucket");
                if (VersionFormat.current().usesItemDefinitions()) {
                    writeItemDefinition(data, fluid.id() + "_bucket", NAMESPACE + ":item/" + fluid.id() + "_bucket");
                }
                JsonObject bucket = new JsonObject();
                bucket.addProperty("parent", "minecraft:item/generated");
                JsonObject bucketTextures = new JsonObject();
                bucketTextures.addProperty("layer0", "minecraft:item/water_bucket");
                bucket.add("textures", bucketTextures);
                data.putJson(NAMESPACE, "models/item/" + fluid.id() + "_bucket", bucket);
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            lang.addProperty("block." + NAMESPACE + "." + block.id(), block.displayName());
            if (!block.isNoItem()) {
                lang.addProperty("item." + NAMESPACE + "." + block.id(), block.displayName());
                if (VersionFormat.current().usesItemDefinitions()) {
                    writeItemDefinition(data, block.id(), BlockShapes.itemModel(block));
                } else {
                    JsonObject itemModel = new JsonObject();
                    itemModel.addProperty("parent", BlockShapes.itemModel(block));
                    data.putJson(NAMESPACE, "models/item/" + block.id(), itemModel);
                }
            }
            if (!block.type().equals("basic")) {
                BlockShapes.write(data, block);
                continue;
            }
            JsonObject variant = new JsonObject();
            variant.addProperty("model", NAMESPACE + ":block/" + block.id());
            JsonObject variants = new JsonObject();
            variants.add("", variant);
            JsonObject state = new JsonObject();
            state.add("variants", variants);
            data.putJson(NAMESPACE, "blockstates/" + block.id(), state);

            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:block/cube_all");
            JsonObject textures = new JsonObject();
            textures.addProperty("all", block.texture());
            model.add("textures", textures);
            data.putJson(NAMESPACE, "models/block/" + block.id(), model);
        }
        for (ArmorMaterialBuilderJS material : ARMOR_MATERIALS.values()) {
            ArmorTextures.write(data, material, VersionFormat.current().usesItemDefinitions());
        }
        for (com.itmecasey.modjs.unify.UnifyMaterials.Entry entry : UNIFY_ENTRIES.values()) {
            String path = "textures/" + (entry.block() ? "block/" : "item/") + entry.id() + ".png";
            if (!data.has(NAMESPACE, path)) {
                data.putBytes(NAMESPACE, path, com.itmecasey.modjs.unify.UnifyMaterials.texture(entry.kind(), entry.color()));
            }
        }
        if (lang.size() > 0) {
            data.putJson(NAMESPACE, "lang/en_us", lang);
        }
    }

    private static void writeItemDefinition(GeneratedData data, String id, String model) {
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", model);
        JsonObject definition = new JsonObject();
        definition.add("model", inner);
        data.putJson(NAMESPACE, "items/" + id, definition);
    }

    static void writeData(GeneratedData data) {
        Map<String, java.util.List<String>> itemTags = new LinkedHashMap<>();
        Map<String, java.util.List<String>> blockTags = new LinkedHashMap<>();
        for (ItemBuilderJS item : ITEMS.values()) {
            if (item.unifyTag() != null) {
                itemTags.computeIfAbsent(item.unifyTag(), k -> new java.util.ArrayList<>()).add(NAMESPACE + ":" + item.id());
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            if (block.unifyTag() != null) {
                itemTags.computeIfAbsent(block.unifyTag(), k -> new java.util.ArrayList<>()).add(NAMESPACE + ":" + block.id());
                blockTags.computeIfAbsent(block.unifyTag(), k -> new java.util.ArrayList<>()).add(NAMESPACE + ":" + block.id());
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            if (block.requiresTool()) {
                blockTags.computeIfAbsent("minecraft:mineable/pickaxe", k -> new java.util.ArrayList<>()).add(NAMESPACE + ":" + block.id());
                if (block.dropItem() != null) {
                    blockTags.computeIfAbsent("minecraft:needs_stone_tool", k -> new java.util.ArrayList<>()).add(NAMESPACE + ":" + block.id());
                }
            }
        }
        Map<String, java.util.List<String>> fluidTags = new LinkedHashMap<>();
        for (FluidBuilderJS fluid : FLUIDS.values()) {
            if (fluid.unifyTag() != null) {
                fluidTags.computeIfAbsent(fluid.unifyTag(), k -> new java.util.ArrayList<>()).add(NAMESPACE + ":" + fluid.id());
            }
        }
        writeTags(data, "fluid", fluidTags);
        Map<String, java.util.List<String>> repairTags = new LinkedHashMap<>();
        for (ArmorMaterialBuilderJS material : ARMOR_MATERIALS.values()) {
            if (material.repair() != null) {
                repairTags.computeIfAbsent(NAMESPACE + ":repairs_armor_" + material.id(), k -> new java.util.ArrayList<>()).add(material.repair());
            }
        }
        for (ToolTierBuilderJS tier : TIERS.values()) {
            if (tier.repair() != null) {
                repairTags.computeIfAbsent(NAMESPACE + ":repairs_tool_" + tier.id(), k -> new java.util.ArrayList<>()).add(tier.repair());
            }
        }
        writeTags(data, "item", repairTags);
        writeTags(data, "item", itemTags);
        writeTags(data, "block", blockTags);
        for (BlockBuilderJS block : BLOCKS.values()) {
            JsonObject table = new JsonObject();
            table.addProperty("type", "minecraft:block");
            JsonArray pools = new JsonArray();
            if (!block.isDropsNothing()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:item");
                entry.addProperty("name", block.dropItem() != null ? block.dropItem() : NAMESPACE + ":" + block.id());
                if (block.type().equals("slab")) {
                    JsonObject setCount = new JsonObject();
                    setCount.addProperty("function", "minecraft:set_count");
                    setCount.addProperty("count", 2);
                    JsonObject properties = new JsonObject();
                    properties.addProperty("type", "double");
                    JsonObject isDouble = new JsonObject();
                    isDouble.addProperty("condition", "minecraft:block_state_property");
                    isDouble.addProperty("block", NAMESPACE + ":" + block.id());
                    isDouble.add("properties", properties);
                    JsonArray fnConditions = new JsonArray();
                    fnConditions.add(isDouble);
                    setCount.add("conditions", fnConditions);
                    JsonArray functions = new JsonArray();
                    functions.add(setCount);
                    entry.add("functions", functions);
                }
                JsonArray entries = new JsonArray();
                entries.add(entry);
                JsonObject condition = new JsonObject();
                condition.addProperty("condition", "minecraft:survives_explosion");
                JsonArray conditions = new JsonArray();
                conditions.add(condition);
                JsonObject pool = new JsonObject();
                pool.addProperty("rolls", 1);
                pool.add("entries", entries);
                pool.add("conditions", conditions);
                pools.add(pool);
            }
            table.add("pools", pools);
            data.putJson(NAMESPACE, com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/" + block.id(), table);
        }
    }

    private static void writeTags(GeneratedData data, String registry, Map<String, java.util.List<String>> tags) {
        for (Map.Entry<String, java.util.List<String>> entry : tags.entrySet()) {
            String tag = entry.getKey();
            int colon = tag.indexOf(':');
            JsonObject json = new JsonObject();
            json.addProperty("replace", false);
            JsonArray values = new JsonArray();
            entry.getValue().forEach(values::add);
            json.add("values", values);
            data.putJson(tag.substring(0, colon), com.itmecasey.modjs.format.DataFolders.tags(registry) + "/" + tag.substring(colon + 1), json);
        }
    }
}
