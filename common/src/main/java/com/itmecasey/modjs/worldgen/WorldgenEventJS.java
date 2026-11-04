package com.itmecasey.modjs.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.recipe.RecipeJson;
import org.mozilla.javascript.Context;

import java.util.List;

/**
 * The event passed to {@code WorldgenEvents.add(event => ...)}. Everything is written as data pack JSON:
 * configured and placed features plus Forge biome modifiers.
 */
public final class WorldgenEventJS extends EventJS {
    private static final String NAMESPACE = "modjs";

    private final GeneratedData data;

    public WorldgenEventJS(GeneratedData data) {
        this.data = data;
    }

    private static String stringOption(Object options, String key, String fallback) {
        String value = JsonUtil.asString(JsonUtil.property(options, key));
        return value != null ? value : fallback;
    }

    private static int intOption(Object options, String key, int fallback) {
        Object value = JsonUtil.property(options, key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    /** Biome selector: an id, a {@code #tag}, or an array of ids. */
    private static JsonElement biomes(Object options, String key, String fallback) {
        Object value = JsonUtil.property(options, key);
        if (value == null) {
            return new JsonPrimitive(fallback);
        }
        return RecipeJson.selector(value);
    }

    private static String requireId(Object options) {
        String id = JsonUtil.asString(JsonUtil.property(options, "id"));
        if (id == null || !id.matches("[a-z0-9_./-]+")) {
            throw Context.reportRuntimeError("Worldgen entries need an 'id' of lowercase letters, digits and _ . / -");
        }
        return id;
    }

    /**
     * Adds an ore. Options: {@code id}, {@code block} (an id or an array of ids), {@code size} (default 9),
     * {@code count} veins per chunk (default 8), {@code minY}/{@code maxY} (default -64/64), {@code replaces}
     * (default {@code #minecraft:stone_ore_replaceables}), {@code biomes} (default {@code #minecraft:is_overworld}),
     * {@code step} (default {@code underground_ores}).
     */
    public void addOre(Object options) {
        String id = requireId(options);
        String block = JsonUtil.asString(JsonUtil.property(options, "block"));
        if (block == null) {
            throw Context.reportRuntimeError("addOre needs a 'block'");
        }
        String replaces = stringOption(options, "replaces", "#minecraft:stone_ore_replaceables");

        JsonObject target = new JsonObject();
        target.add("target", predicate(replaces));
        JsonObject state = new JsonObject();
        state.addProperty("Name", block);
        target.add("state", state);
        JsonArray targets = new JsonArray();
        targets.add(target);
        JsonObject config = new JsonObject();
        config.add("targets", targets);
        config.addProperty("size", intOption(options, "size", 9));
        config.addProperty("discard_chance_on_air_exposure", 0.0);
        JsonObject configured = new JsonObject();
        configured.addProperty("type", "minecraft:ore");
        configured.add("config", config);
        data.putJson(NAMESPACE, "worldgen/configured_feature/" + id, configured);

        int minY = intOption(options, "minY", -64);
        int maxY = intOption(options, "maxY", 64);
        JsonArray placement = new JsonArray();
        placement.add(typed("minecraft:count", "count", new JsonPrimitive(intOption(options, "count", 8))));
        placement.add(simple("minecraft:in_square"));
        JsonObject uniform = new JsonObject();
        uniform.addProperty("type", "minecraft:uniform");
        JsonObject min = new JsonObject();
        min.addProperty("absolute", minY);
        JsonObject max = new JsonObject();
        max.addProperty("absolute", maxY);
        uniform.add("min_inclusive", min);
        uniform.add("max_inclusive", max);
        placement.add(typed("minecraft:height_range", "height", uniform));
        placement.add(simple("minecraft:biome"));
        JsonObject placed = new JsonObject();
        placed.addProperty("feature", NAMESPACE + ":" + id);
        placed.add("placement", placement);
        data.putJson(NAMESPACE, "worldgen/placed_feature/" + id, placed);

        writeModifier(id, com.itmecasey.modjs.format.LoaderNames.id("add_features"), biomes(options, "biomes", "#minecraft:is_overworld"),
            "features", new JsonPrimitive(NAMESPACE + ":" + id), stringOption(options, "step", "underground_ores"));
    }

    private static JsonObject predicate(String replaces) {
        JsonObject predicate = new JsonObject();
        if (replaces.startsWith("#")) {
            predicate.addProperty("predicate_type", "minecraft:tag_match");
            predicate.addProperty("tag", replaces.substring(1));
        } else {
            predicate.addProperty("predicate_type", "minecraft:block_match");
            predicate.addProperty("block", replaces);
        }
        return predicate;
    }

    private static JsonObject simple(String type) {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        return out;
    }

    private static JsonObject typed(String type, String key, JsonElement value) {
        JsonObject out = simple(type);
        out.add(key, value);
        return out;
    }

    /** Adds existing placed features to biomes: options {@code id}, {@code features}, {@code biomes}, {@code step}. */
    public void addFeature(Object options) {
        String id = requireId(options);
        JsonElement features = RecipeJson.selector(JsonUtil.property(options, "features"));
        writeModifier(id, com.itmecasey.modjs.format.LoaderNames.id("add_features"), biomes(options, "biomes", "#minecraft:is_overworld"),
            "features", features, stringOption(options, "step", "underground_ores"));
    }

    /** Removes placed features from biomes: options {@code id}, {@code features}, {@code biomes}, {@code steps}. */
    public void removeFeature(Object options) {
        String id = requireId(options);
        JsonObject json = new JsonObject();
        json.addProperty("type", com.itmecasey.modjs.format.LoaderNames.id("remove_features"));
        json.add("biomes", biomes(options, "biomes", "#minecraft:is_overworld"));
        json.add("features", RecipeJson.selector(JsonUtil.property(options, "features")));
        data.putJson(NAMESPACE, com.itmecasey.modjs.format.LoaderNames.biomeModifierFolder() + id, json);
    }

    /** Adds a mob spawn: options {@code id}, {@code entity}, {@code weight}, {@code minCount}, {@code maxCount}, {@code biomes}. */
    public void addSpawn(Object options) {
        String id = requireId(options);
        String entity = stringOption(options, "entity", null);
        if (entity == null) {
            throw Context.reportRuntimeError("addSpawn needs an 'entity'");
        }
        JsonObject spawner = new JsonObject();
        spawner.addProperty("type", entity);
        spawner.addProperty("weight", intOption(options, "weight", 10));
        spawner.addProperty("minCount", intOption(options, "minCount", 1));
        spawner.addProperty("maxCount", intOption(options, "maxCount", 3));
        JsonObject json = new JsonObject();
        json.addProperty("type", com.itmecasey.modjs.format.LoaderNames.id("add_spawns"));
        json.add("biomes", biomes(options, "biomes", "#minecraft:is_overworld"));
        JsonArray spawners = new JsonArray();
        spawners.add(spawner);
        json.add("spawners", spawners);
        data.putJson(NAMESPACE, com.itmecasey.modjs.format.LoaderNames.biomeModifierFolder() + id, json);
    }

    /** Removes mob spawns: options {@code id}, {@code entities} (ids or a #tag), {@code biomes}. */
    public void removeSpawn(Object options) {
        String id = requireId(options);
        JsonObject json = new JsonObject();
        json.addProperty("type", com.itmecasey.modjs.format.LoaderNames.id("remove_spawns"));
        json.add("biomes", biomes(options, "biomes", "#minecraft:is_overworld"));
        json.add("entity_types", RecipeJson.selector(JsonUtil.property(options, "entities")));
        data.putJson(NAMESPACE, com.itmecasey.modjs.format.LoaderNames.biomeModifierFolder() + id, json);
    }

    private void writeModifier(String id, String type, JsonElement biomes, String featureKey, JsonElement features, String step) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("biomes", biomes);
        json.add(featureKey, features);
        json.addProperty("step", step);
        data.putJson(NAMESPACE, com.itmecasey.modjs.format.LoaderNames.biomeModifierFolder() + id, json);
    }

    public List<String> steps() {
        return List.of("raw_generation", "lakes", "local_modifications", "underground_structures", "surface_structures",
            "strongholds", "underground_ores", "underground_decoration", "fluid_springs", "vegetal_decoration", "top_layer_modification");
    }
}
