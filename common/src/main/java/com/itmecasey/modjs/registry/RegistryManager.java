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
    private static final Map<String, ProfessionBuilderJS> PROFESSIONS = new LinkedHashMap<>();
    private static final Map<String, ProfessionBuilderJS> DIRECT_PROFESSIONS = new LinkedHashMap<>();
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
        PROFESSIONS.clear();
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
        EventGroups.STARTUP.postExact("registry", "villager_profession", new RegistryEventJS<>("villager profession", PROFESSIONS, ProfessionBuilderJS::new));
        collectProfessions();
        for (BlockBuilderJS block : BLOCKS.values()) {
            if (!block.isNoItem() && ITEMS.remove(block.fullId()) != null) {
                com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.STARTUP)
                    .addError("Item '" + block.fullId() + "' has the same id as a block with an item; the block item is kept and the item is dropped");
            }
        }
        if (!UNIFY_FILE_READ) {
            UNIFY_FILE_READ = true;
            com.itmecasey.modjs.unify.UnifyFile.applyStartup(com.itmecasey.modjs.script.ScriptManager.root());
        }
        addUnifiedMaterials();
        for (ItemBuilderJS item : ITEMS.values()) {
            if (item.unifyTag() != null) {
                com.itmecasey.modjs.unify.UnifyConfig.addCanonicalItem(item.unifyTag(), item.fullId());
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            if (block.unifyTag() != null) {
                com.itmecasey.modjs.unify.UnifyConfig.addCanonicalItem(block.unifyTag(), block.fullId());
            }
        }
        for (FluidBuilderJS fluid : FLUIDS.values()) {
            if (fluid.unifyTag() != null) {
                com.itmecasey.modjs.unify.UnifyConfig.addCanonicalFluid(fluid.unifyTag(), fluid.fullId());
            }
        }
        if (!contributorsInstalled) {
            contributorsInstalled = true;
            DataBuilder.addClientContributor(RegistryManager::writeAssets);
            DataBuilder.addServerContributor(RegistryManager::writeData);
        }
    }

    /** {@code Villagers.profession(...)} in a startup script: collected next to the ones made in the registry event. */
    public static synchronized void addProfession(ProfessionBuilderJS profession) {
        if (DIRECT_PROFESSIONS.containsKey(profession.fullId())) {
            throw org.mozilla.javascript.Context.reportRuntimeError("Duplicate villager profession id '" + profession.fullId() + "'");
        }
        DIRECT_PROFESSIONS.put(profession.fullId(), profession);
    }

    private static void collectProfessions() {
        for (ProfessionBuilderJS direct : DIRECT_PROFESSIONS.values()) {
            if (PROFESSIONS.containsKey(direct.fullId())) {
                com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.STARTUP).addError("Duplicate villager profession id '" + direct.fullId() + "'");
            } else {
                PROFESSIONS.put(direct.fullId(), direct);
            }
        }
        PROFESSIONS.values().removeIf(profession -> {
            String problem = profession.problem();
            if (problem != null) {
                com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.STARTUP).addError(problem);
                return true;
            }
            return false;
        });
        java.util.List<com.itmecasey.modjs.villager.TradeOps.Op> trades = new java.util.ArrayList<>();
        for (ProfessionBuilderJS profession : PROFESSIONS.values()) {
            profession.tradesByLevel().forEach((level, specs) -> trades.add(new com.itmecasey.modjs.villager.TradeOps.Op(
                com.itmecasey.modjs.villager.TradeOps.Kind.ADD, profession.fullId(), level, specs, null, null)));
        }
        com.itmecasey.modjs.villager.TradeOps.setProfessionOps(trades);
    }

    /** Forgets every profession (tests). */
    public static synchronized void forgetProfessions() {
        PROFESSIONS.clear();
        DIRECT_PROFESSIONS.clear();
    }

    public static Collection<ProfessionBuilderJS> professions() {
        return PROFESSIONS.values();
    }

    /** Registers the canonical items {@code Unify.materials} asked for, unless a script already made the same id. */
    private static void addUnifiedMaterials() {
        for (com.itmecasey.modjs.unify.UnifyMaterials.Spec spec : com.itmecasey.modjs.unify.UnifyConfig.materialSpecs()) {
            for (com.itmecasey.modjs.unify.UnifyMaterials.Entry entry : com.itmecasey.modjs.unify.UnifyMaterials.expand(spec)) {
                String key = NAMESPACE + ":" + entry.id();
                if (ITEMS.containsKey(key) || BLOCKS.containsKey(key)) {
                    continue;
                }
                if (entry.block()) {
                    BlockBuilderJS block = new BlockBuilderJS(entry.id()).displayName(entry.displayName()).hardness(3).resistance(6).unify(entry.tag());
                    if (entry.dropId() != null) {
                        block.drops(entry.dropId()).requiresTool(true);
                    }
                    BLOCKS.put(key, block);
                } else {
                    ItemBuilderJS item = new ItemBuilderJS(entry.id()).displayName(entry.displayName()).unify(entry.tag());
                    if (entry.kind().equals("rod")) {
                        item.parentModel("minecraft:item/handheld");
                    }
                    ITEMS.put(key, item);
                }
                UNIFY_ENTRIES.put(key, entry);
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

    private static JsonObject lang(Map<String, JsonObject> langs, String namespace) {
        return langs.computeIfAbsent(namespace, k -> new JsonObject());
    }

    static void writeAssets(GeneratedData data) {
        Map<String, JsonObject> langs = new LinkedHashMap<>();
        for (ItemBuilderJS item : ITEMS.values()) {
            lang(langs, item.namespace()).addProperty("item." + item.namespace() + "." + item.id(), item.displayName());
            if (VersionFormat.current().usesItemDefinitions()) {
                writeItemDefinition(data, item.namespace(), item.id(), item.namespace() + ":item/" + item.id());
            }
            JsonObject model = new JsonObject();
            model.addProperty("parent", item.parentModel());
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", item.texture());
            model.add("textures", textures);
            data.putJson(item.namespace(), "models/item/" + item.id(), model);
        }
        for (CreativeTabBuilderJS tab : TABS.values()) {
            lang(langs, tab.namespace()).addProperty("itemGroup." + tab.namespace() + "." + tab.id(), tab.displayName());
        }
        for (FluidBuilderJS fluid : FLUIDS.values()) {
            lang(langs, fluid.namespace()).addProperty("block." + fluid.namespace() + "." + fluid.id(), fluid.displayName());
            lang(langs, fluid.namespace()).addProperty("fluid_type." + fluid.namespace() + "." + fluid.id(), fluid.displayName());
            JsonObject particle = new JsonObject();
            JsonObject particleTextures = new JsonObject();
            particleTextures.addProperty("particle", "minecraft:block/water_still");
            particle.add("textures", particleTextures);
            data.putJson(fluid.namespace(), "models/block/" + fluid.id(), particle);
            JsonObject variant = new JsonObject();
            variant.addProperty("model", fluid.namespace() + ":block/" + fluid.id());
            JsonObject variants = new JsonObject();
            variants.add("", variant);
            JsonObject state = new JsonObject();
            state.add("variants", variants);
            data.putJson(fluid.namespace(), "blockstates/" + fluid.id(), state);
            if (!fluid.isNoBucket()) {
                lang(langs, fluid.namespace()).addProperty("item." + fluid.namespace() + "." + fluid.id() + "_bucket", fluid.displayName() + " Bucket");
                if (VersionFormat.current().usesItemDefinitions()) {
                    writeItemDefinition(data, fluid.namespace(), fluid.id() + "_bucket", fluid.namespace() + ":item/" + fluid.id() + "_bucket");
                }
                JsonObject bucket = new JsonObject();
                bucket.addProperty("parent", "minecraft:item/generated");
                JsonObject bucketTextures = new JsonObject();
                bucketTextures.addProperty("layer0", "minecraft:item/water_bucket");
                bucket.add("textures", bucketTextures);
                data.putJson(fluid.namespace(), "models/item/" + fluid.id() + "_bucket", bucket);
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            lang(langs, block.namespace()).addProperty("block." + block.namespace() + "." + block.id(), block.displayName());
            if (!block.isNoItem()) {
                lang(langs, block.namespace()).addProperty("item." + block.namespace() + "." + block.id(), block.displayName());
                if (VersionFormat.current().usesItemDefinitions()) {
                    writeItemDefinition(data, block.namespace(), block.id(), BlockShapes.itemModel(block));
                } else {
                    JsonObject itemModel = new JsonObject();
                    itemModel.addProperty("parent", BlockShapes.itemModel(block));
                    data.putJson(block.namespace(), "models/item/" + block.id(), itemModel);
                }
            }
            if (!block.type().equals("basic")) {
                BlockShapes.write(data, block);
                continue;
            }
            BlockModels.write(data, block);
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
        for (ProfessionBuilderJS profession : PROFESSIONS.values()) {
            JsonObject names = lang(langs, profession.namespace());
            names.addProperty("entity." + profession.namespace() + ".villager." + profession.id(), profession.displayName());
            names.addProperty("entity.minecraft.villager." + profession.id(), profession.displayName());
            names.addProperty("entity.minecraft.villager." + profession.namespace() + "." + profession.id(), profession.displayName());
            ProfessionAssets.copyTexture(data, profession);
        }
        for (Map.Entry<String, JsonObject> entry : langs.entrySet()) {
            if (entry.getValue().size() > 0) {
                data.putJson(entry.getKey(), "lang/en_us", entry.getValue());
            }
        }
    }

    private static void writeItemDefinition(GeneratedData data, String namespace, String id, String model) {
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "minecraft:model");
        inner.addProperty("model", model);
        JsonObject definition = new JsonObject();
        definition.add("model", inner);
        data.putJson(namespace, "items/" + id, definition);
    }

    static void writeData(GeneratedData data) {
        if (!PROFESSIONS.isEmpty() && com.itmecasey.modjs.script.PlatformBinding.atLeast(26, 1)) {
            com.itmecasey.modjs.villager.TradeData.writeProfessions(data, PROFESSIONS.values());
        }
        Map<String, java.util.List<String>> itemTags = new LinkedHashMap<>();
        Map<String, java.util.List<String>> blockTags = new LinkedHashMap<>();
        for (ItemBuilderJS item : ITEMS.values()) {
            if (item.unifyTag() != null) {
                itemTags.computeIfAbsent(item.unifyTag(), k -> new java.util.ArrayList<>()).add(item.fullId());
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            if (block.unifyTag() != null) {
                itemTags.computeIfAbsent(block.unifyTag(), k -> new java.util.ArrayList<>()).add(block.fullId());
                blockTags.computeIfAbsent(block.unifyTag(), k -> new java.util.ArrayList<>()).add(block.fullId());
            }
        }
        for (BlockBuilderJS block : BLOCKS.values()) {
            if (block.requiresTool()) {
                blockTags.computeIfAbsent("minecraft:mineable/pickaxe", k -> new java.util.ArrayList<>()).add(block.fullId());
                if (block.dropItem() != null) {
                    blockTags.computeIfAbsent("minecraft:needs_stone_tool", k -> new java.util.ArrayList<>()).add(block.fullId());
                }
            }
        }
        Map<String, java.util.List<String>> fluidTags = new LinkedHashMap<>();
        for (FluidBuilderJS fluid : FLUIDS.values()) {
            if (fluid.unifyTag() != null) {
                fluidTags.computeIfAbsent(fluid.unifyTag(), k -> new java.util.ArrayList<>()).add(fluid.fullId());
            }
        }
        writeTags(data, "fluid", fluidTags);
        Map<String, java.util.List<String>> repairTags = new LinkedHashMap<>();
        for (ArmorMaterialBuilderJS material : ARMOR_MATERIALS.values()) {
            if (material.repair() != null) {
                repairTags.computeIfAbsent(material.namespace() + ":repairs_armor_" + material.id(), k -> new java.util.ArrayList<>()).add(material.repair());
            }
        }
        for (ToolTierBuilderJS tier : TIERS.values()) {
            if (tier.repair() != null) {
                repairTags.computeIfAbsent(tier.namespace() + ":repairs_tool_" + tier.id(), k -> new java.util.ArrayList<>()).add(tier.repair());
            }
        }
        writeTags(data, "item", repairTags);
        writeTags(data, "item", itemTags);
        writeTags(data, "block", blockTags);
        for (BlockBuilderJS block : BLOCKS.values()) {
            JsonObject table = new JsonObject();
            table.addProperty("type", "minecraft:block");
            JsonArray pools = new JsonArray();
            if (!block.isDropsNothing() && !(block.isNoItem() && block.dropItem() == null)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:item");
                entry.addProperty("name", block.dropItem() != null ? block.dropItem() : block.fullId());
                if (block.type().equals("slab")) {
                    JsonObject setCount = new JsonObject();
                    setCount.addProperty("function", "minecraft:set_count");
                    setCount.addProperty("count", 2);
                    JsonObject properties = new JsonObject();
                    properties.addProperty("type", "double");
                    JsonObject isDouble = new JsonObject();
                    isDouble.addProperty("condition", "minecraft:block_state_property");
                    isDouble.addProperty("block", block.fullId());
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
            data.putJson(block.namespace(), com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/" + block.id(), table);
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
