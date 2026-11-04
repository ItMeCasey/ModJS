package com.itmecasey.modjs.recipe;

import com.google.gson.JsonArray;
import com.itmecasey.modjs.format.DataFolders;
import com.itmecasey.modjs.format.VersionFormat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.item.ItemRef;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.pack.GeneratedData;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The event passed to {@code ServerEvents.recipes}. */
public final class RecipesEventJS extends EventJS {
    public static final String DEFAULT_NAMESPACE = "modjs";

    private final List<RecipeBuilderJS> builders = new ArrayList<>();

    private RecipeBuilderJS add(String type, JsonObject json) {
        json.addProperty("type", type);
        RecipeBuilderJS builder = new RecipeBuilderJS(json);
        builders.add(builder);
        return builder;
    }

    public RecipeBuilderJS shaped(Object result, Object pattern, Object key) {
        JsonObject json = new JsonObject();
        JsonArray rows = new JsonArray();
        for (Object row : RecipeJson.asList(pattern)) {
            rows.add(JsonUtil.asString(row));
        }
        JsonObject keys = new JsonObject();
        Object unwrapped = JsonUtil.unwrap(key);
        if (!(unwrapped instanceof Scriptable scriptable)) {
            throw Context.reportRuntimeError("shaped recipe key must be an object like {A: 'minecraft:stone'}");
        }
        for (Object id : scriptable.getIds()) {
            String name = String.valueOf(id);
            keys.add(name, RecipeJson.ingredient(scriptable.get(name, scriptable)));
        }
        json.add("key", keys);
        json.add("pattern", rows);
        json.add("result", RecipeJson.result(result));
        return add("minecraft:crafting_shaped", json);
    }

    public RecipeBuilderJS shapeless(Object result, Object ingredients) {
        JsonObject json = new JsonObject();
        JsonArray list = new JsonArray();
        for (Object ingredient : RecipeJson.asList(ingredients)) {
            list.add(RecipeJson.ingredient(ingredient));
        }
        json.add("ingredients", list);
        json.add("result", RecipeJson.result(result));
        return add("minecraft:crafting_shapeless", json);
    }

    private RecipeBuilderJS cooking(String type, int cookingTime, Object result, Object ingredient) {
        JsonObject json = new JsonObject();
        json.add("ingredient", RecipeJson.ingredient(ingredient));
        ItemRef cooked = ItemRef.parse(result);
        VersionFormat.current().setCookingResult(json, cooked.id(), cooked.count());
        json.addProperty("experience", 0.0);
        json.addProperty("cookingtime", cookingTime);
        return add(type, json);
    }

    public RecipeBuilderJS smelting(Object result, Object ingredient) {
        return cooking("minecraft:smelting", 200, result, ingredient);
    }

    public RecipeBuilderJS blasting(Object result, Object ingredient) {
        return cooking("minecraft:blasting", 100, result, ingredient);
    }

    public RecipeBuilderJS smoking(Object result, Object ingredient) {
        return cooking("minecraft:smoking", 100, result, ingredient);
    }

    public RecipeBuilderJS campfireCooking(Object result, Object ingredient) {
        return cooking("minecraft:campfire_cooking", 600, result, ingredient);
    }

    public RecipeBuilderJS stonecutting(Object result, Object ingredient) {
        JsonObject json = new JsonObject();
        json.add("ingredient", RecipeJson.ingredient(ingredient));
        ItemRef cut = ItemRef.parse(result);
        VersionFormat.current().setStonecuttingResult(json, cut.id(), cut.count());
        return add("minecraft:stonecutting", json);
    }

    public RecipeBuilderJS smithing(Object result, Object template, Object base, Object addition) {
        JsonObject json = new JsonObject();
        json.add("template", RecipeJson.ingredient(template));
        json.add("base", RecipeJson.ingredient(base));
        json.add("addition", RecipeJson.ingredient(addition));
        json.add("result", RecipeJson.result(result));
        return add("minecraft:smithing_transform", json);
    }

    /** Adds a recipe from raw JSON; it must contain a {@code type}. */
    public RecipeBuilderJS custom(Object raw) {
        JsonElement element = JsonUtil.toJson(raw);
        if (!element.isJsonObject() || !element.getAsJsonObject().has("type")) {
            throw Context.reportRuntimeError("custom recipe needs an object with a 'type'");
        }
        RecipeBuilderJS builder = new RecipeBuilderJS(element.getAsJsonObject());
        builders.add(builder);
        return builder;
    }

    public void remove(Object filter) {
        RecipeChanges.current().remove(RecipeFilter.of(filter));
    }

    public void replaceInput(Object filter, Object from, Object to) {
        RecipeChanges.current().replaceInput(RecipeFilter.of(filter), idOrTag(from), idOrTag(to));
    }

    public void replaceOutput(Object filter, Object from, Object to) {
        RecipeChanges.current().replaceOutput(RecipeFilter.of(filter), ItemRef.parse(from).id(), ItemRef.parse(to).id());
    }

    private static String idOrTag(Object raw) {
        String text = JsonUtil.asString(raw);
        if (text == null) {
            throw Context.reportRuntimeError("Expected an item id or #tag, got " + raw);
        }
        return text.startsWith("#") ? text : ItemRef.of(text).id();
    }

    public int count() {
        return builders.size();
    }

    /** Writes every recipe added by scripts to the generated data pack. */
    public void writeTo(GeneratedData data) {
        Set<String> used = new HashSet<>();
        for (RecipeBuilderJS builder : builders) {
            JsonObject json = builder.json();
            String namespace = DEFAULT_NAMESPACE;
            String path;
            if (builder.requestedId() != null) {
                String requested = builder.requestedId();
                int colon = requested.indexOf(':');
                namespace = colon < 0 ? DEFAULT_NAMESPACE : requested.substring(0, colon);
                path = colon < 0 ? requested : requested.substring(colon + 1);
            } else {
                String type = json.get("type").getAsString();
                String shortType = type.substring(type.indexOf(':') + 1);
                String result = RecipeFilter.resultId(json);
                path = shortType + "/" + (result == null ? "custom" : result.replace(':', '/'));
            }
            String candidate = path;
            int suffix = 1;
            while (!used.add(namespace + ":" + candidate)) {
                candidate = path + "_" + suffix++;
            }
            data.putJson(namespace, DataFolders.recipe() + "/" + candidate, json);
        }
    }
}
