package com.itmecasey.modjs.tags;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.recipe.RecipeJson;
import org.mozilla.javascript.Context;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The event passed to {@code ServerEvents.tags('item', event => ...)}. */
public final class TagsEventJS extends EventJS {
    private final String registry;
    private final Map<String, Set<String>> additions = new LinkedHashMap<>();
    private final Set<String> replaced = new LinkedHashSet<>();

    public TagsEventJS(String registry) {
        this.registry = normalizeRegistry(registry);
    }

    public static String normalizeRegistry(String registry) {
        String name = registry.contains(":") ? registry.substring(registry.indexOf(':') + 1) : registry;
        return switch (name) {
            case "items" -> "item";
            case "blocks" -> "block";
            case "fluids" -> "fluid";
            case "entity_types", "entities" -> "entity_type";
            case "biomes" -> "biome";
            default -> name;
        };
    }

    public String getRegistry() {
        return registry;
    }

    private static String tagId(Object raw) {
        String text = JsonUtil.asString(raw);
        if (text == null) {
            throw Context.reportRuntimeError("Tag must be a string like 'c:ingots/copper', got " + raw);
        }
        String id = text.startsWith("#") ? text.substring(1) : text;
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** Adds ids or {@code #tags} to a tag. */
    public TagsEventJS add(Object tag, Object entries) {
        Set<String> set = additions.computeIfAbsent(tagId(tag), k -> new LinkedHashSet<>());
        for (Object entry : RecipeJson.asList(entries)) {
            String text = JsonUtil.asString(entry);
            if (text == null) {
                throw Context.reportRuntimeError("Tag entries must be strings, got " + entry);
            }
            set.add(text);
        }
        return this;
    }

    /** Empties a tag so only entries added afterwards remain. */
    public TagsEventJS removeAll(Object tag) {
        String id = tagId(tag);
        replaced.add(id);
        additions.remove(id);
        return this;
    }

    /**
     * Requests removal of entries from a tag. Data packs cannot remove entries, so this is recorded in
     * {@link TagChanges} and applied after tags load.
     */
    public TagsEventJS remove(Object tag, Object entries) {
        String id = tagId(tag);
        for (Object entry : RecipeJson.asList(entries)) {
            String text = JsonUtil.asString(entry);
            if (text == null) {
                throw Context.reportRuntimeError("Tag entries must be strings, got " + entry);
            }
            TagChanges.current().remove(registry, id, text);
        }
        return this;
    }

    public void writeTo(GeneratedData data) {
        Set<String> all = new LinkedHashSet<>(additions.keySet());
        all.addAll(replaced);
        for (String tag : all) {
            int colon = tag.indexOf(':');
            String namespace = tag.substring(0, colon);
            String path = tag.substring(colon + 1);
            JsonObject json = new JsonObject();
            json.addProperty("replace", replaced.contains(tag));
            JsonArray values = new JsonArray();
            for (String value : additions.getOrDefault(tag, Set.of())) {
                values.add(value);
            }
            json.add("values", values);
            data.putJson(namespace, "tags/" + registry + "/" + path, json);
        }
    }
}
