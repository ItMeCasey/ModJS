package com.itmecasey.modjs.worldgen;

import com.google.gson.JsonObject;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.tags.TagsEventJS;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The world types (world presets, the "World Type" button of the Create World screen) that startup scripts defined with
 * {@code StartupEvents.worldTypes}. They are collected once per game session, then written into the generated data pack and
 * language file every time the game scans for packs.
 */
public final class WorldTypes {
    private static final String PRESET_TAG_REGISTRY = "worldgen/world_preset";
    private static final String LIST_TAG = "minecraft:normal";

    private static final Map<String, JsonObject> PRESETS = new LinkedHashMap<>();
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static final Set<String> SHOWN = new LinkedHashSet<>();
    private static final Set<String> HIDDEN = new LinkedHashSet<>();
    private static boolean loaded;

    private WorldTypes() {
    }

    /** Runs {@code StartupEvents.worldTypes} the first time it is needed. Startup scripts have run by then. */
    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        EventGroups.STARTUP.post("worldTypes", new WorldTypeEventJS());
    }

    /** Stores a preset. {@code visible} true adds it to the world type list, false keeps it out, null leaves the list alone. */
    static synchronized void put(String id, JsonObject preset, String name, Boolean visible) {
        PRESETS.put(id, preset);
        if (name != null) {
            NAMES.put(id, name);
        }
        if (visible != null) {
            if (visible) {
                HIDDEN.remove(id);
                SHOWN.add(id);
            } else {
                SHOWN.remove(id);
            }
        }
    }

    static synchronized boolean drop(String id) {
        SHOWN.remove(id);
        NAMES.remove(id);
        return PRESETS.remove(id) != null;
    }

    static synchronized JsonObject get(String id) {
        JsonObject preset = PRESETS.get(id);
        return preset == null ? null : preset.deepCopy();
    }

    static synchronized void show(String id) {
        HIDDEN.remove(id);
        SHOWN.add(id);
    }

    static synchronized void hide(String id) {
        SHOWN.remove(id);
        HIDDEN.add(id);
    }

    static synchronized void name(String id, String name) {
        NAMES.put(id, name);
    }

    /** The language key the Create World screen uses for a world type's label. */
    public static String languageKey(String id) {
        return "generator." + id.replace(':', '.').replace('/', '.');
    }

    /** Writes the world presets, and the change to the list of presets the button cycles through. */
    public static synchronized void writeTo(GeneratedData data) {
        ensureLoaded();
        for (Map.Entry<String, JsonObject> entry : PRESETS.entrySet()) {
            String id = entry.getKey();
            data.putJson(id.substring(0, id.indexOf(':')), "worldgen/world_preset/" + id.substring(id.indexOf(':') + 1), entry.getValue());
        }
        if (SHOWN.isEmpty() && HIDDEN.isEmpty()) {
            return;
        }
        TagsEventJS tags = new TagsEventJS(PRESET_TAG_REGISTRY);
        for (String id : SHOWN) {
            tags.add(LIST_TAG, id);
        }
        for (String id : HIDDEN) {
            tags.remove(LIST_TAG, id);
        }
        tags.writeTo(data);
    }

    /** Adds the labels of script world types to a language file. */
    public static synchronized void addNames(JsonObject lang) {
        ensureLoaded();
        NAMES.forEach((id, name) -> lang.addProperty(languageKey(id), name));
    }

    /** True once any world type was defined or changed, so callers can skip work. */
    public static synchronized boolean any() {
        ensureLoaded();
        return !PRESETS.isEmpty() || !SHOWN.isEmpty() || !HIDDEN.isEmpty() || !NAMES.isEmpty();
    }

    /** For tests: forget everything and run the startup event again next time. */
    public static synchronized void reset() {
        PRESETS.clear();
        NAMES.clear();
        SHOWN.clear();
        HIDDEN.clear();
        loaded = false;
    }
}
