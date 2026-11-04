package com.itmecasey.modjs.worldgen;

import com.google.gson.JsonObject;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * The event passed to {@code StartupEvents.worldTypes}: create, edit, copy, hide and remove the choices of the "World Type"
 * button on the Create World screen. A world type is a data pack world preset: a set of dimensions with their generators.
 */
public final class WorldTypeEventJS extends EventJS {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";
    private static final String DEFAULT_BASE = "minecraft:normal";

    private static String id(String id) {
        return DimensionEventJS.namespace(id) + ":" + DimensionEventJS.path(id);
    }

    /** The preset JSON for an id: one made by a script, or one that ships with the game or another mod. */
    private static JsonObject load(String id) {
        JsonObject made = WorldTypes.get(id);
        if (made != null) {
            return made;
        }
        JsonObject shipped = DimensionEventJS.tryLoadJson(id, "worldgen/world_preset");
        if (shipped == null) {
            throw Context.reportRuntimeError("Cannot find world type '" + id + "'");
        }
        return shipped;
    }

    /** Applies the {@code overworld}, {@code nether}, {@code end} and {@code dimensions} options onto a preset. */
    private static JsonObject build(JsonObject preset, Object options) {
        JsonObject dimensions = preset.has("dimensions") ? preset.getAsJsonObject("dimensions") : new JsonObject();
        preset.add("dimensions", dimensions);
        override(dimensions, OVERWORLD, JsonUtil.property(options, "overworld"));
        override(dimensions, NETHER, JsonUtil.property(options, "nether"));
        override(dimensions, END, JsonUtil.property(options, "end"));
        Object extra = JsonUtil.property(options, "dimensions");
        if (extra instanceof Scriptable scriptable) {
            for (Object key : scriptable.getIds()) {
                String name = String.valueOf(key);
                override(dimensions, id(name), JsonUtil.property(extra, name));
            }
        }
        return preset;
    }

    private static void override(JsonObject dimensions, String dimension, Object options) {
        if (options == null) {
            return;
        }
        if (options instanceof CharSequence kind) {
            options = new org.mozilla.javascript.NativeObject() {
                {
                    put("generator", this, kind.toString());
                }
            };
        }
        dimensions.add(dimension, DimensionEventJS.stem(options));
    }

    private static boolean visible(Object options) {
        Object visible = JsonUtil.property(options, "visible");
        return !(visible instanceof Boolean flag) || flag;
    }

    /**
     * Adds a world type. It starts from {@code base} (default {@code minecraft:normal}, so the Nether and End stay vanilla) and
     * changes the dimensions you give: {@code overworld}, {@code nether} and {@code end} take the same options as
     * {@code DimensionEvents.add} (or just a generator name), and {@code dimensions} adds more dimensions by id. {@code name}
     * is the label on the button and {@code visible: false} keeps it out of the button's list.
     */
    public void add(String id, Object options) {
        String full = id(id);
        String base = JsonUtil.asString(JsonUtil.property(options, "base"));
        JsonObject preset = build(load(base != null ? base : DEFAULT_BASE), options);
        String name = JsonUtil.asString(JsonUtil.property(options, "name"));
        WorldTypes.put(full, preset, name != null ? name : defaultName(full), visible(options));
    }

    /** Changes an existing world type, including the ones that ship with the game, using the options of {@link #add}. */
    public void edit(String id, Object options) {
        String full = id(id);
        JsonObject preset = build(load(full), options);
        String name = JsonUtil.asString(JsonUtil.property(options, "name"));
        WorldTypes.put(full, preset, name, null);
    }

    /** Copies a world type under a new id, optionally changing it with the options of {@link #add}. */
    public void duplicate(String fromId, String toId, Object options) {
        String from = id(fromId);
        JsonObject preset = build(load(from), options);
        String to = id(toId);
        String name = JsonUtil.asString(JsonUtil.property(options, "name"));
        WorldTypes.put(to, preset, name != null ? name : defaultName(to), visible(options));
    }

    public void duplicate(String fromId, String toId) {
        duplicate(fromId, toId, null);
    }

    /** Adds a world type from a complete world preset JSON object. */
    public void addRaw(String id, Object json, String name) {
        String full = id(id);
        Object parsed = JsonUtil.toJson(json);
        if (!(parsed instanceof JsonObject preset)) {
            throw Context.reportRuntimeError("addRaw needs a JSON object with a \"dimensions\" entry");
        }
        WorldTypes.put(full, preset, name != null ? name : defaultName(full), true);
    }

    public void addRaw(String id, Object json) {
        addRaw(id, json, null);
    }

    /** Takes a world type out of the button's list. Vanilla world types stay in the game, they just stop being offered. */
    public void hide(String id) {
        WorldTypes.hide(id(id));
    }

    /** Puts a world type in the button's list. */
    public void show(String id) {
        WorldTypes.show(id(id));
    }

    /** Changes the label of a world type, including vanilla ones. */
    public void rename(String id, String name) {
        WorldTypes.name(id(id), name);
    }

    /** Removes a world type made by a script, or hides a vanilla one. Returns true if a script-made one was removed. */
    public boolean drop(String id) {
        String full = id(id);
        boolean removed = WorldTypes.drop(full);
        if (!removed) {
            WorldTypes.hide(full);
        }
        return removed;
    }

    private static String defaultName(String id) {
        String path = id.substring(id.indexOf(':') + 1).replace('/', ' ').replace('_', ' ');
        return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }
}
