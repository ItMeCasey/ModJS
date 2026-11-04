package com.itmecasey.modjs.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.format.VersionFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Removals and replacements requested by scripts for recipes that already exist. They are applied to the loaded
 * recipes after every recipe reload; see {@code RecipeReloadHook}.
 */
public final class RecipeChanges {
    private record Replacement(RecipeFilter filter, String from, String to, boolean output) {
    }

    private static volatile RecipeChanges current = new RecipeChanges();

    private final List<RecipeFilter> removals = new ArrayList<>();
    private final List<Replacement> replacements = new ArrayList<>();
    private int removedCount;
    private int replacedCount;

    public static RecipeChanges current() {
        return current;
    }

    public static void reset() {
        current = new RecipeChanges();
    }

    synchronized void remove(RecipeFilter filter) {
        removals.add(filter);
    }

    synchronized void replaceInput(RecipeFilter filter, String from, String to) {
        replacements.add(new Replacement(filter, from, to, false));
    }

    synchronized void replaceOutput(RecipeFilter filter, String from, String to) {
        replacements.add(new Replacement(filter, from, to, true));
    }

    public synchronized boolean isEmpty() {
        return removals.isEmpty() && replacements.isEmpty();
    }

    public synchronized int removedCount() {
        return removedCount;
    }

    public synchronized int replacedCount() {
        return replacedCount;
    }

    /** Returns null when the recipe must be removed, otherwise the (possibly rewritten) JSON. */
    public synchronized JsonObject apply(String id, JsonObject json) {
        for (RecipeFilter filter : removals) {
            if (filter.matches(id, json)) {
                removedCount++;
                return null;
            }
        }
        JsonObject out = json;
        for (Replacement replacement : replacements) {
            if (!replacement.filter.matches(id, out)) {
                continue;
            }
            JsonObject copy = out.deepCopy();
            boolean changed = replacement.output
                ? replaceResult(copy, replacement.from, replacement.to)
                : replaceIngredients(copy, replacement.from, replacement.to);
            if (changed) {
                replacedCount++;
                out = copy;
            }
        }
        return out;
    }

    private static boolean replaceResult(JsonObject json, String from, String to) {
        JsonElement result = json.get("result");
        if (result != null && result.isJsonObject()) {
            JsonObject object = result.getAsJsonObject();
            JsonElement id = object.get("id");
            if (id != null && id.isJsonPrimitive() && id.getAsString().equals(from)) {
                object.addProperty("id", to);
                return true;
            }
        } else if (result != null && result.isJsonPrimitive() && result.getAsString().equals(from)) {
            json.addProperty("result", to);
            return true;
        }
        return false;
    }

    private static boolean replaceIngredients(JsonObject json, String from, String to) {
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.equals("result") || key.equals("type") || key.equals("group") || key.equals("category")) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (isToken(value, from)) {
                entry.setValue(VersionFormat.current().ingredient(to));
                changed = true;
            } else {
                changed |= replaceInContainer(value, from, to);
            }
        }
        return changed;
    }

    private static boolean isToken(JsonElement element, String token) {
        return token.equals(VersionFormat.current().ingredientToken(element));
    }

    /** Rewrites matching ingredients inside an array or object in place; returns whether anything changed. */
    private static boolean replaceInContainer(JsonElement container, String from, String to) {
        boolean changed = false;
        if (container.isJsonArray()) {
            JsonArray array = container.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (isToken(child, from)) {
                    array.set(i, VersionFormat.current().ingredient(to));
                    changed = true;
                } else {
                    changed |= replaceInContainer(child, from, to);
                }
            }
        } else if (container.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : container.getAsJsonObject().entrySet()) {
                JsonElement child = entry.getValue();
                if (isToken(child, from)) {
                    entry.setValue(VersionFormat.current().ingredient(to));
                    changed = true;
                } else {
                    changed |= replaceInContainer(child, from, to);
                }
            }
        }
        return changed;
    }
}
