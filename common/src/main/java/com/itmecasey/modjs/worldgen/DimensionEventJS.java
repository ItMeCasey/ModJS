package com.itmecasey.modjs.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.pack.GeneratedData;
import org.mozilla.javascript.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The event passed to {@code DimensionEvents.add(event => ...)}. Dimensions are data pack JSON ({@code dimension/<id>.json}
 * and {@code dimension_type/<id>.json}), so changes apply when a world loads: restart the game or server, not {@code /reload}.
 */
public final class DimensionEventJS extends EventJS {
    private final GeneratedData data;

    public DimensionEventJS(GeneratedData data) {
        this.data = data;
    }

    // -- ids ---------------------------------------------------------------------------------------------------

    private static String namespace(String id) {
        return id.contains(":") ? id.substring(0, id.indexOf(':')) : "modjs";
    }

    private static String path(String id) {
        String p = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (!p.matches("[a-z0-9_./-]+") || !namespace(id).matches("[a-z0-9_.-]+")) {
            throw Context.reportRuntimeError("Invalid dimension id '" + id + "': use lowercase letters, digits and _ . / -");
        }
        return p;
    }

    private static String full(String id) {
        return namespace(id) + ":" + path(id);
    }

    // -- add ---------------------------------------------------------------------------------------------------

    /**
     * Adds a dimension. Options: {@code type} (a dimension type id, default {@code minecraft:overworld}), and
     * {@code generator}: {@code "void"} (default), {@code "flat"} (with {@code layers: [{block, height}]} and
     * {@code biome}), {@code "overworld"}, {@code "nether"}, {@code "end"}, or a raw generator JSON object.
     */
    public void add(String id, Object options) {
        data.putJson(namespace(id), "dimension/" + path(id), stem(options));
    }

    /** Adds a dimension from a full {@code dimension/<id>.json} object. */
    public void addRaw(String id, Object json) {
        data.putJson(namespace(id), "dimension/" + path(id), JsonUtil.toJson(json));
    }

    /** The {@code dimension/<id>.json} object for the given options (type and generator). */
    public static JsonObject stem(Object options) {
        JsonObject stem = new JsonObject();
        String type = JsonUtil.asString(JsonUtil.property(options, "type"));
        stem.addProperty("type", type != null ? type : "minecraft:overworld");
        stem.add("generator", generator(options));
        return stem;
    }

    private static JsonElement generator(Object options) {
        Object raw = JsonUtil.property(options, "generator");
        String kind = raw instanceof CharSequence ? raw.toString() : raw == null ? "void" : null;
        if (kind == null) {
            return JsonUtil.toJson(raw);
        }
        switch (kind) {
            case "void":
            case "flat": {
                JsonObject settings = new JsonObject();
                String biome = JsonUtil.asString(JsonUtil.property(options, "biome"));
                settings.addProperty("biome", biome != null ? biome : "minecraft:plains");
                JsonArray layers = new JsonArray();
                Object layerOption = JsonUtil.property(options, "layers");
                if (kind.equals("flat") && layerOption != null) {
                    JsonElement parsed = JsonUtil.toJson(layerOption);
                    if (parsed.isJsonArray()) {
                        layers = parsed.getAsJsonArray();
                    }
                } else if (kind.equals("void")) {
                    JsonObject air = new JsonObject();
                    air.addProperty("block", "minecraft:air");
                    air.addProperty("height", 1);
                    layers.add(air);
                } else {
                    JsonObject stone = new JsonObject();
                    stone.addProperty("block", "minecraft:stone");
                    stone.addProperty("height", 1);
                    layers.add(stone);
                }
                settings.add("layers", layers);
                settings.addProperty("features", false);
                settings.addProperty("lakes", false);
                settings.add("structure_overrides", new JsonArray());
                JsonObject generator = new JsonObject();
                generator.addProperty("type", "minecraft:flat");
                generator.add("settings", settings);
                return generator;
            }
            case "overworld":
            case "nether":
            case "end":
                return preset(kind);
            default:
                throw Context.reportRuntimeError("Unknown generator '" + kind + "'; use void, flat, overworld, nether, end or a JSON object");
        }
    }

    /** The generator vanilla uses for its own dimension of that kind. */
    private static JsonObject preset(String kind) {
        JsonObject generator = new JsonObject();
        generator.addProperty("type", "minecraft:noise");
        JsonObject source = new JsonObject();
        if (kind.equals("end")) {
            source.addProperty("type", "minecraft:the_end");
            generator.addProperty("settings", "minecraft:end");
        } else {
            source.addProperty("type", "minecraft:multi_noise");
            source.addProperty("preset", kind.equals("nether") ? "minecraft:nether" : "minecraft:overworld");
            generator.addProperty("settings", kind.equals("nether") ? "minecraft:nether" : "minecraft:overworld");
        }
        generator.add("biome_source", source);
        return generator;
    }

    // -- types -------------------------------------------------------------------------------------------------

    /**
     * Adds a dimension type by copying an existing one and changing top-level fields:
     * {@code addType('mymod:bright', {base: 'minecraft:overworld', set: {ambient_light: 0.5, coordinate_scale: 4}})}.
     */
    public void addType(String id, Object options) {
        String base = JsonUtil.asString(JsonUtil.property(options, "base"));
        JsonObject json = loadJson(base != null ? base : "minecraft:overworld", "dimension_type");
        Object set = JsonUtil.property(options, "set");
        if (set != null) {
            JsonElement overrides = JsonUtil.toJson(set);
            if (overrides.isJsonObject()) {
                overrides.getAsJsonObject().entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
            }
        }
        data.putJson(namespace(id), "dimension_type/" + path(id), json);
    }

    /** Adds a dimension type from a full JSON object. */
    public void addTypeRaw(String id, Object json) {
        data.putJson(namespace(id), "dimension_type/" + path(id), JsonUtil.toJson(json));
    }

    // -- edit, duplicate, drop ---------------------------------------------------------------------------------

    /** Replaces an existing dimension (for example {@code minecraft:the_nether}) using the same options as {@link #add}. */
    public void edit(String id, Object options) {
        add(id, options);
    }

    /**
     * Copies a dimension (script-made, from a data pack, or one of the three vanilla ones) to a new id. Optional
     * {@code options} (same as {@link #add}) change the copy, for example {@code {type: 'mymod:bright'}}.
     */
    public void duplicate(String fromId, String toId, Object options) {
        JsonObject stem = existingStem(fromId);
        if (options != null) {
            String type = JsonUtil.asString(JsonUtil.property(options, "type"));
            if (type != null) {
                stem.addProperty("type", type);
            }
            if (JsonUtil.property(options, "generator") != null) {
                stem.add("generator", generator(options));
            }
        }
        data.putJson(namespace(toId), "dimension/" + path(toId), stem);
    }

    public void duplicate(String fromId, String toId) {
        duplicate(fromId, toId, null);
    }

    /** Copies a dimension type to a new id, optionally changing top-level fields with {@code set}. */
    public void duplicateType(String fromId, String toId, Object set) {
        JsonObject json = loadJson(fromId, "dimension_type");
        if (set != null) {
            JsonElement overrides = JsonUtil.toJson(set);
            if (overrides.isJsonObject()) {
                overrides.getAsJsonObject().entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
            }
        }
        data.putJson(namespace(toId), "dimension_type/" + path(toId), json);
    }

    /**
     * Removes a dimension from the generated data and from data packs in {@code modjs/data}. Dimensions that ship inside
     * the game or other mods cannot be removed this way; Minecraft has no way to delete them.
     */
    public boolean drop(String id) {
        boolean removed = data.remove(namespace(id), "dimension/" + path(id) + ".json");
        removed |= data.remove(namespace(id), "dimension_type/" + path(id) + ".json");
        return removed;
    }

    private JsonObject existingStem(String id) {
        byte[] bytes = data.get(namespace(id), "dimension/" + path(id) + ".json");
        if (bytes != null) {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject onClasspath = tryLoadJson(full(id), "dimension");
        if (onClasspath != null) {
            return onClasspath;
        }
        JsonObject stem = new JsonObject();
        switch (full(id)) {
            case "minecraft:overworld" -> {
                stem.addProperty("type", "minecraft:overworld");
                stem.add("generator", preset("overworld"));
            }
            case "minecraft:the_nether" -> {
                stem.addProperty("type", "minecraft:the_nether");
                stem.add("generator", preset("nether"));
            }
            case "minecraft:the_end" -> {
                stem.addProperty("type", "minecraft:the_end");
                stem.add("generator", preset("end"));
            }
            default -> throw Context.reportRuntimeError("Cannot find dimension '" + id + "' to copy; only script-made dimensions, data pack dimensions and the three vanilla ones can be duplicated");
        }
        return stem;
    }

    private JsonObject loadJson(String id, String folder) {
        byte[] bytes = data.get(namespace(id), folder + "/" + path(id) + ".json");
        if (bytes != null) {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject json = tryLoadJson(full(id), folder);
        if (json == null) {
            throw Context.reportRuntimeError("Cannot find " + folder.replace('_', ' ') + " '" + id + "'");
        }
        return json;
    }

    /** Reads {@code data/<ns>/<folder>/<path>.json} from the game and mod jars, or null. */
    private static JsonObject tryLoadJson(String id, String folder) {
        String resource = "data/" + namespace(id) + "/" + folder + "/" + path(id) + ".json";
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), DimensionEventJS.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in != null) {
                    return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            } catch (IOException | RuntimeException e) {
                // fall through to the next loader
            }
        }
        return null;
    }
}
