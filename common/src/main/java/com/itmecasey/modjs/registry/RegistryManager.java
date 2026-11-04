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
    private static final Map<String, CreativeTabBuilderJS> TABS = new LinkedHashMap<>();
    private static boolean contributorsInstalled;

    private RegistryManager() {
    }

    /** Posts the registry events. Call once, after startup scripts have loaded. */
    public static synchronized void collect() {
        ITEMS.clear();
        BLOCKS.clear();
        TABS.clear();
        com.itmecasey.modjs.item.ItemModifications.clear();
        EventGroups.ITEM.post("modification", new com.itmecasey.modjs.item.ItemModifications.ModificationEventJS());
        EventGroups.STARTUP.postExact("registry", "creative_tab", new RegistryEventJS<>("creative tab", TABS, CreativeTabBuilderJS::new));
        EventGroups.STARTUP.postExact("registry", "block", new RegistryEventJS<>("block", BLOCKS, BlockBuilderJS::new));
        EventGroups.STARTUP.postExact("registry", "item", new RegistryEventJS<>("item", ITEMS, ItemBuilderJS::new));
        if (!contributorsInstalled) {
            contributorsInstalled = true;
            DataBuilder.addClientContributor(RegistryManager::writeAssets);
            DataBuilder.addServerContributor(RegistryManager::writeData);
        }
    }

    public static Collection<ItemBuilderJS> items() {
        return ITEMS.values();
    }

    public static Collection<BlockBuilderJS> blocks() {
        return BLOCKS.values();
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
        for (BlockBuilderJS block : BLOCKS.values()) {
            lang.addProperty("block." + NAMESPACE + "." + block.id(), block.displayName());
            if (!block.isNoItem()) {
                lang.addProperty("item." + NAMESPACE + "." + block.id(), block.displayName());
                if (VersionFormat.current().usesItemDefinitions()) {
                    writeItemDefinition(data, block.id(), NAMESPACE + ":block/" + block.id());
                } else {
                    JsonObject itemModel = new JsonObject();
                    itemModel.addProperty("parent", NAMESPACE + ":block/" + block.id());
                    data.putJson(NAMESPACE, "models/item/" + block.id(), itemModel);
                }
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
        for (BlockBuilderJS block : BLOCKS.values()) {
            JsonObject table = new JsonObject();
            table.addProperty("type", "minecraft:block");
            JsonArray pools = new JsonArray();
            if (!block.isDropsNothing()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "minecraft:item");
                entry.addProperty("name", NAMESPACE + ":" + block.id());
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
            data.putJson(NAMESPACE, "loot_table/blocks/" + block.id(), table);
        }
    }
}
