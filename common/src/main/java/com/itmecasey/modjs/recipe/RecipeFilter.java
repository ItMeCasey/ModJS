package com.itmecasey.modjs.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.NativeArray;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Selects recipes the way KubeJS does: an object whose keys must all match ({@code id}, {@code mod}, {@code type},
 * {@code group}, {@code output}, {@code input}), an array meaning "any of", or {@code {not: filter}}.
 * Matching runs on the recipe's serialized JSON, so it works for every recipe type.
 */
public final class RecipeFilter {
    public record Target(String id, JsonObject json) {
    }

    private final Predicate<Target> predicate;

    private RecipeFilter(Predicate<Target> predicate) {
        this.predicate = predicate;
    }

    public boolean matches(String id, JsonObject json) {
        return predicate.test(new Target(id, json));
    }

    public static RecipeFilter of(Object raw) {
        Object value = JsonUtil.unwrap(raw);
        if (value instanceof NativeArray || value instanceof List<?>) {
            List<RecipeFilter> any = new ArrayList<>();
            for (Object entry : RecipeJson.asList(value)) {
                any.add(of(entry));
            }
            return new RecipeFilter(target -> any.stream().anyMatch(f -> f.predicate.test(target)));
        }
        String text = JsonUtil.asString(value);
        if (text != null) {
            return new RecipeFilter(target -> target.id().equals(text.contains(":") ? text : "minecraft:" + text));
        }
        List<Predicate<Target>> all = new ArrayList<>();
        Object id = JsonUtil.property(value, "id");
        if (id != null) {
            String wanted = JsonUtil.asString(id);
            if (wanted != null) {
                all.add(t -> t.id().equals(wanted.contains(":") ? wanted : "minecraft:" + wanted));
            }
        }
        String mod = JsonUtil.asString(JsonUtil.property(value, "mod"));
        if (mod != null) {
            all.add(t -> t.id().startsWith(mod + ":"));
        }
        String type = JsonUtil.asString(JsonUtil.property(value, "type"));
        if (type != null) {
            String full = type.contains(":") ? type : "minecraft:" + type;
            all.add(t -> full.equals(string(t.json(), "type")));
        }
        String group = JsonUtil.asString(JsonUtil.property(value, "group"));
        if (group != null) {
            all.add(t -> group.equals(string(t.json(), "group")));
        }
        Object output = JsonUtil.property(value, "output");
        if (output != null) {
            String wanted = outputId(output);
            all.add(t -> wanted.equals(resultId(t.json())));
        }
        Object input = JsonUtil.property(value, "input");
        if (input != null) {
            List<Object> options = RecipeJson.asList(input);
            String first = options.isEmpty() ? null : JsonUtil.asString(options.get(0));
            if (first == null) {
                throw new IllegalArgumentException("Expected an item id or #tag for 'input', got " + input);
            }
            String wanted = first.startsWith("#") ? first : com.itmecasey.modjs.item.ItemRef.of(first).id();
            all.add(t -> containsIngredient(t.json(), wanted));
        }
        Object not = JsonUtil.property(value, "not");
        if (not != null) {
            RecipeFilter inner = of(not);
            all.add(t -> !inner.predicate.test(t));
        }
        Object or = JsonUtil.property(value, "or");
        if (or != null) {
            RecipeFilter inner = of(or);
            all.add(inner.predicate);
        }
        if (all.isEmpty()) {
            throw new IllegalArgumentException("Empty recipe filter");
        }
        return new RecipeFilter(target -> all.stream().allMatch(p -> p.test(target)));
    }

    private static String outputId(Object output) {
        String text = JsonUtil.asString(output);
        if (text != null) {
            return text.startsWith("#") ? text : com.itmecasey.modjs.item.ItemRef.of(text).id();
        }
        return com.itmecasey.modjs.item.ItemRef.parse(output).id();
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /** The id of the recipe result, handling both {@code "result": "id"} and {@code "result": {"id": ...}}. */
    public static String resultId(JsonObject json) {
        JsonElement result = json.get("result");
        if (result == null) {
            return null;
        }
        if (result.isJsonPrimitive()) {
            return result.getAsString();
        }
        if (result.isJsonObject()) {
            JsonObject object = result.getAsJsonObject();
            JsonElement id = object.has("id") ? object.get("id") : object.get("item");
            return id != null && id.isJsonPrimitive() ? id.getAsString() : null;
        }
        return null;
    }

    private static boolean containsIngredient(JsonElement element, String wanted) {
        String token = VersionFormat.current().ingredientToken(element);
        if (token != null) {
            return token.equals(wanted);
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsIngredient(child, wanted)) {
                    return true;
                }
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("result") || entry.getKey().equals("type")) {
                    continue;
                }
                if (containsIngredient(entry.getValue(), wanted)) {
                    return true;
                }
            }
        }
        return false;
    }
}
