package com.itmecasey.modjs.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.item.ItemRef;
import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;

import java.util.ArrayList;
import java.util.List;

/** Builds the JSON fragments (ingredients and results) used by recipes in the 26.1 format. */
public final class RecipeJson {
    private RecipeJson() {
    }

    /**
     * Accepts an id string, a {@code #tag} string, an {@link ItemRef}, {@code {item: id}} / {@code {tag: id}} objects,
     * or an array of any of those. Returns a JSON string or array of strings.
     */
    public static JsonElement ingredient(Object raw) {
        Object value = JsonUtil.unwrap(raw);
        String text = JsonUtil.asString(value);
        if (text != null) {
            return VersionFormat.current().ingredient(normalizeIngredient(text));
        }
        if (value instanceof NativeArray || value instanceof List<?>) {
            JsonArray out = new JsonArray();
            for (Object entry : asList(value)) {
                JsonElement child = ingredient(entry);
                if (child.isJsonArray()) {
                    child.getAsJsonArray().forEach(out::add);
                } else {
                    out.add(child);
                }
            }
            return out;
        }
        if (value instanceof Scriptable) {
            String item = JsonUtil.asString(JsonUtil.property(value, "item"));
            if (item != null) {
                return VersionFormat.current().ingredient(item);
            }
            String tag = JsonUtil.asString(JsonUtil.property(value, "tag"));
            if (tag != null) {
                return VersionFormat.current().ingredient(tag.startsWith("#") ? tag : "#" + tag);
            }
        }
        throw new IllegalArgumentException("Not an ingredient: " + value);
    }

    private static String normalizeIngredient(String text) {
        // "3x minecraft:stone" is accepted for convenience, the count is meaningless for an ingredient.
        ItemRef ref = text.startsWith("#") ? null : ItemRef.of(text);
        return ref == null ? text : ref.id();
    }

    /** Returns a result object {@code {"id": ..., "count": n}} (count omitted when 1). */
    public static JsonObject result(Object raw) {
        ItemRef ref = ItemRef.parse(raw);
        return VersionFormat.current().result(ref.id(), ref.count());
    }

    /**
     * Accepts an id, a {@code #tag}, or an array of those and returns plain strings (never version-specific
     * ingredient objects). Used for biome, feature and entity selectors.
     */
    public static JsonElement selector(Object raw) {
        List<Object> entries = asList(raw);
        if (entries.size() == 1 && !(JsonUtil.unwrap(raw) instanceof NativeArray)) {
            String text = JsonUtil.asString(entries.get(0));
            if (text == null) {
                throw new IllegalArgumentException("Expected an id, a #tag or a list of ids, got " + raw);
            }
            return new JsonPrimitive(text);
        }
        JsonArray out = new JsonArray();
        for (Object entry : entries) {
            String text = JsonUtil.asString(entry);
            if (text == null) {
                throw new IllegalArgumentException("Expected an id or #tag, got " + entry);
            }
            out.add(text);
        }
        return out;
    }

    public static List<Object> asList(Object raw) {
        Object value = JsonUtil.unwrap(raw);
        List<Object> out = new ArrayList<>();
        if (value instanceof NativeArray array) {
            for (int i = 0; i < array.getLength(); i++) {
                out.add(array.get(i, array));
            }
        } else if (value instanceof List<?> list) {
            out.addAll(list);
        } else if (value != null) {
            out.add(value);
        }
        return out;
    }
}
