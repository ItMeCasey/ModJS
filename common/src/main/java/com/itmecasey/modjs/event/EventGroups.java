package com.itmecasey.modjs.event;

import com.itmecasey.modjs.script.ScriptType;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every script-visible event group, keyed by the global name scripts use. */
public final class EventGroups {
    public static final EventGroup STARTUP = new EventGroup("StartupEvents", ScriptType.STARTUP);
    public static final EventGroup SERVER = new EventGroup("ServerEvents", ScriptType.SERVER);
    public static final EventGroup BLOCK = new EventGroup("BlockEvents", null);
    public static final EventGroup ITEM = new EventGroup("ItemEvents", null);
    public static final EventGroup PLAYER = new EventGroup("PlayerEvents", ScriptType.SERVER);
    public static final EventGroup ENTITY = new EventGroup("EntityEvents", ScriptType.SERVER);
    public static final EventGroup LEVEL = new EventGroup("LevelEvents", ScriptType.SERVER);
    public static final EventGroup RECIPE_VIEWER = new EventGroup("RecipeViewerEvents", ScriptType.CLIENT);
    public static final EventGroup DIMENSION = new EventGroup("DimensionEvents", ScriptType.SERVER);
    public static final EventGroup WORLDGEN = new EventGroup("WorldgenEvents", ScriptType.SERVER);
    public static final EventGroup NETWORK = new EventGroup("NetworkEvents", null);
    public static final EventGroup CLIENT = new EventGroup("ClientEvents", ScriptType.CLIENT);
    public static final EventGroup INFO = new EventGroup("InfoEvents", null);

    private static final Map<String, EventGroup> ALL = new LinkedHashMap<>();

    static {
        for (EventGroup group : new EventGroup[] {STARTUP, SERVER, BLOCK, ITEM, PLAYER, ENTITY, LEVEL, WORLDGEN, DIMENSION, RECIPE_VIEWER, NETWORK, CLIENT, INFO}) {
            ALL.put(group.groupName(), group);
        }
    }

    private EventGroups() {
    }

    public static Map<String, EventGroup> all() {
        return ALL;
    }

    public static void clear(ScriptType type) {
        ALL.values().forEach(group -> group.clear(type));
    }
}
