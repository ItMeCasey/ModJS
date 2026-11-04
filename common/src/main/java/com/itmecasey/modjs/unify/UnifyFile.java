package com.itmecasey.modjs.unify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.recipe.RecipeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code modjs/config/unify.json}, the no-script way to set up unification. The file is created with defaults on
 * the first run. Scripts run after it and can override anything in it.
 */
public final class UnifyFile {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private UnifyFile() {
    }

    static Path file(Path root) {
        return root.resolve("config").resolve("unify.json");
    }

    /** The default file contents. */
    public static JsonObject defaults() {
        JsonObject json = new JsonObject();
        json.addProperty("_help", "Unify items like the old Ore Dictionary. Set registerMaterials to true to add ModJS items for the materials below. See docs/REFERENCE.md.");
        json.addProperty("registerMaterials", false);
        json.add("materials", new JsonArray());
        json.add("kinds", new JsonArray());
        json.addProperty("includeVanilla", false);
        JsonArray prefer = new JsonArray();
        prefer.add("modjs");
        json.add("prefer", prefer);
        json.add("auto", new JsonArray());
        json.addProperty("autoFluids", false);
        json.addProperty("inputs", "canonical");
        json.add("skipMods", new JsonArray());
        json.add("skipItems", new JsonArray());
        json.add("skipRecipes", new JsonArray());
        json.addProperty("swapInventories", true);
        json.addProperty("convertRecipes", true);
        return json;
    }

    private static JsonObject read(Path root) {
        Path file = file(root);
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(defaults()) + "\n", StandardCharsets.UTF_8);
                return defaults();
            }
            JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : defaults();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("ModJS could not read {}: {}", file, e.getMessage());
            return defaults();
        }
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                if (element.isJsonPrimitive()) {
                    out.add(element.getAsString());
                }
            }
        }
        return out;
    }

    /** Startup part: registers the materials. Call once before registry events are collected. */
    public static void applyStartup(Path root) {
        JsonObject json = read(root);
        if (json.has("registerMaterials") && json.get("registerMaterials").getAsBoolean()) {
            UnifyConfig.addMaterials(strings(json, "materials"), strings(json, "kinds"),
                json.has("includeVanilla") && json.get("includeVanilla").getAsBoolean());
        }
    }

    /** Server part: preferences, groups and skips. Call after {@link UnifyConfig#resetServer()} and before server scripts run. */
    public static void applyServer(Path root) {
        JsonObject json = read(root);
        UnifyConfig.Binding binding = new UnifyConfig.Binding();
        List<String> prefer = strings(json, "prefer");
        if (!prefer.isEmpty()) {
            binding.prefer(prefer);
        }
        List<String> auto = strings(json, "auto");
        if (!auto.isEmpty()) {
            binding.auto(auto);
        }
        if (json.has("autoFluids")) {
            binding.autoFluids(json.get("autoFluids").getAsBoolean());
        }
        if (json.has("inputs")) {
            try {
                binding.inputs(json.get("inputs").getAsString());
            } catch (RuntimeException e) {
                LOGGER.error("unify.json: {}", e.getMessage());
            }
        }
        List<String> skipItems = strings(json, "skipItems");
        for (String mod : strings(json, "skipMods")) {
            skipItems.add("@" + mod);
        }
        if (!skipItems.isEmpty()) {
            binding.skipItems(skipItems);
        }
        for (String id : strings(json, "skipRecipes")) {
            UnifyConfig.skipRecipe(RecipeFilter.of(id));
        }
        if (json.has("swapInventories")) {
            binding.swapInventories(json.get("swapInventories").getAsBoolean());
        }
        if (json.has("convertRecipes")) {
            binding.convertRecipes(json.get("convertRecipes").getAsBoolean());
        }
    }

    /** For tests: the keys a config file may contain. */
    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : defaults().entrySet()) {
            keys.add(entry.getKey());
        }
        return keys;
    }
}
