package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.client.TitleScreenConfig;
import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Function;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configuration for a block created by {@code StartupEvents.registry('block', ...)}. */
public final class BlockBuilderJS {
    private final String id;
    private final String namespace;
    private String displayName;
    private float hardness = 1.5f;
    private float resistance = 6.0f;
    private int lightLevel;
    private boolean requiresTool;
    private String texture;
    private String tab = "minecraft:building_blocks";
    private boolean noItem;
    private boolean dropsNothing;
    private String type = "basic";
    private String unifyTag;
    private String dropItem;
    private int containerRows;
    private final Map<String, String> faceTextures = new LinkedHashMap<>();
    private String model;
    private boolean entity;
    private final List<int[]> slotPositions = new ArrayList<>();
    private int stackLimit = 64;
    private final List<String> valueNames = new ArrayList<>();
    private Function tickFunction;
    private int tickInterval = 1;
    private Function buttonFunction;
    private Function screenFunction;
    private TitleScreenConfig screenConfig;

    private static final List<String> FACE_KEYS = List.of("all", "top", "bottom", "side", "north", "south", "east", "west", "front", "end");
    private static final List<String> MODELS = List.of("cube_all", "cube", "cube_column", "cube_bottom_top", "orientable");

    BlockBuilderJS(String rawId) {
        RegistryNames.Id parsed = RegistryNames.parse(rawId);
        this.namespace = parsed.namespace();
        this.id = parsed.path();
    }

    /** The path part of the id ({@code ruby} in {@code mypack:ruby}). */
    public String id() {
        return id;
    }

    /** The namespace it is registered in ({@code modjs} unless the script gave one). */
    public String namespace() {
        return namespace;
    }

    /** The full registry id, such as {@code mypack:ruby}. */
    public String fullId() {
        return namespace + ":" + id;
    }

    public String displayName() {
        return displayName != null ? displayName : RegistryNames.titleCase(id);
    }

    public float hardness() {
        return hardness;
    }

    public float resistance() {
        return resistance;
    }

    public int lightLevel() {
        return lightLevel;
    }

    public boolean requiresTool() {
        return requiresTool;
    }

    public String texture() {
        return texture != null ? texture : namespace + ":block/" + id;
    }

    public String tab() {
        return tab;
    }

    public boolean isNoItem() {
        return noItem;
    }

    /** Rows (1 to 6) of nine slots when this block is a storage container, or 0. */
    public int containerRows() {
        return containerRows;
    }

    /**
     * Makes the block a storage container like a chest, with {@code rows} rows of nine slots. Right-click opens it; its items
     * drop when it is broken. Readable and writable from scripts through {@code event.blockEntity}.
     */
    public BlockBuilderJS container(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("container rows must be 1 to 6, got " + rows);
        }
        this.containerRows = rows;
        return this;
    }

    // -- per-face textures and model -----------------------------------------------------------------------------

    /**
     * Textures per face: {@code {top, bottom, side, north, south, east, west, all}} ({@code front} is {@code north}, {@code end} is
     * {@code top} and {@code bottom}). The most specific key wins: {@code north} over {@code side} over {@code all}.
     */
    public BlockBuilderJS textures(Object faces) {
        if (!(faces instanceof org.mozilla.javascript.ScriptableObject object)) {
            throw new IllegalArgumentException("textures needs an object such as { top: 'mymod:block/top', side: 'mymod:block/side' }");
        }
        for (Object key : object.getIds()) {
            String name = String.valueOf(key);
            if (!FACE_KEYS.contains(name)) {
                throw new IllegalArgumentException("Unknown texture face '" + name + "'; use " + String.join(", ", FACE_KEYS));
            }
            Object value = JsonUtil.property(object, name);
            if (value != null && !(value instanceof org.mozilla.javascript.Undefined)) {
                faceTextures.put(name.equals("front") ? "north" : name, String.valueOf(value));
            }
        }
        return this;
    }

    /** The texture for one face ({@code up}, {@code down}, {@code north}, {@code south}, {@code east}, {@code west}) after the fallbacks. */
    public String faceTexture(String face) {
        String specific = switch (face) {
            case "up" -> firstOf("top", "end");
            case "down" -> firstOf("bottom", "end");
            default -> faceTextures.get(face);
        };
        if (specific != null) {
            return specific;
        }
        if (!face.equals("up") && !face.equals("down") && faceTextures.containsKey("side")) {
            return faceTextures.get("side");
        }
        return fallbackTexture();
    }

    private String firstOf(String a, String b) {
        return faceTextures.containsKey(a) ? faceTextures.get(a) : faceTextures.get(b);
    }

    private String fallbackTexture() {
        if (faceTextures.containsKey("all")) {
            return faceTextures.get("all");
        }
        if (texture != null) {
            return texture;
        }
        if (!faceTextures.isEmpty()) {
            return faceTextures.values().iterator().next();
        }
        return texture();
    }

    public boolean hasFaceTextures() {
        return !faceTextures.isEmpty();
    }

    /** Model name: one of cube_all, cube, cube_column, cube_bottom_top, orientable, or a model id supplied by the script. */
    public BlockBuilderJS model(String model) {
        if (!MODELS.contains(model) && !model.contains("/")) {
            throw new IllegalArgumentException("Unknown model '" + model + "'; use " + String.join(", ", MODELS) + " or a model id such as mymod:block/thing");
        }
        this.model = model;
        return this;
    }

    /** The chosen model, or the default for the textures given. */
    public String modelName() {
        if (model != null) {
            return model;
        }
        if (faceTextures.isEmpty() || (faceTextures.size() == 1 && faceTextures.containsKey("all"))) {
            return "cube_all";
        }
        boolean onlyColumn = faceTextures.keySet().stream().allMatch(k -> k.equals("top") || k.equals("bottom") || k.equals("side") || k.equals("end"));
        if (onlyColumn) {
            if (faceTextures.containsKey("end")) {
                return "cube_column";
            }
            if (faceTextures.containsKey("top") && faceTextures.containsKey("bottom") && faceTextures.containsKey("side")) {
                return "cube_bottom_top";
            }
        }
        return "cube";
    }

    /** True when {@link #modelName()} is a model file the script supplies instead of one of the built-in parents. */
    public boolean hasCustomModel() {
        return model != null && model.contains("/");
    }

    /** The block turns to face the player and has a {@code facing} property. */
    public boolean isOrientable() {
        return "orientable".equals(model);
    }

    // -- block entity, inventory, screen ------------------------------------------------------------------------------

    /** True for blocks with a block entity of their own (from {@code inventory()}, {@code entity()}, ticks or a screen). */
    public boolean hasEntity() {
        return entity || !slotPositions.isEmpty() || tickFunction != null || buttonFunction != null || screenFunction != null;
    }

    /** True when the block has slots the script defined (as opposed to the chest-style {@link #container(int)}). */
    public boolean hasScriptInventory() {
        return !slotPositions.isEmpty();
    }

    /** Gives the block a block entity with a data map but no slots. */
    public BlockBuilderJS entity() {
        this.entity = true;
        return this;
    }

    /**
     * Gives the block a block entity with slots and a menu. Options: {@code slots} (a count, laid out in a row, or a list of
     * {@code {x, y}} positions from the top left of the screen), {@code stackLimit} (default 64), {@code values} (names of integers
     * kept with the block and sent to an open screen).
     */
    public BlockBuilderJS inventory(Object options) {
        this.entity = true;
        Object slots = options == null ? null : JsonUtil.unwrap(JsonUtil.property(options, "slots"));
        slotPositions.clear();
        if (slots instanceof Number count) {
            int n = count.intValue();
            if (n < 1 || n > 54) {
                throw new IllegalArgumentException("inventory slots must be 1 to 54, got " + n);
            }
            int perRow = Math.min(n, 9);
            for (int i = 0; i < n; i++) {
                slotPositions.add(new int[] {8 + (i % 9) * 18 + (9 - perRow) * 9, 18 + (i / 9) * 18});
            }
        } else if (slots instanceof List<?> list) {
            for (Object entry : list) {
                Object x = JsonUtil.property(entry, "x");
                Object y = JsonUtil.property(entry, "y");
                if (!(x instanceof Number) || !(y instanceof Number)) {
                    throw new IllegalArgumentException("each slot needs numbers x and y, for example { x: 56, y: 17 }");
                }
                slotPositions.add(new int[] {((Number) x).intValue(), ((Number) y).intValue()});
            }
            if (slotPositions.isEmpty() || slotPositions.size() > 54) {
                throw new IllegalArgumentException("inventory slots must be 1 to 54");
            }
        } else {
            throw new IllegalArgumentException("inventory needs slots: a number or a list of { x, y }");
        }
        Object limit = JsonUtil.property(options, "stackLimit");
        if (limit instanceof Number n) {
            stackLimit = Math.max(1, Math.min(99, n.intValue()));
        }
        Object values = JsonUtil.unwrap(JsonUtil.property(options, "values"));
        valueNames.clear();
        if (values instanceof List<?> names) {
            for (Object name : names) {
                valueNames.add(String.valueOf(name));
            }
        }
        return this;
    }

    public int slotCount() {
        return slotPositions.size();
    }

    /** Slot positions as {x, y}, relative to the screen's top left. */
    public List<int[]> slotPositions() {
        return slotPositions;
    }

    public int stackLimit() {
        return stackLimit;
    }

    /** Names of the synced integer values; the index is the slot in the menu's data. */
    public List<String> valueNames() {
        return valueNames;
    }

    /** Runs {@code fn(blockEntity)} on the server every {@code interval} ticks. */
    public BlockBuilderJS onTick(int interval, Function fn) {
        this.tickInterval = Math.max(1, interval);
        this.tickFunction = fn;
        return this;
    }

    public BlockBuilderJS onTick(Function fn) {
        return onTick(1, fn);
    }

    public Function tickFunction() {
        return tickFunction;
    }

    public int tickInterval() {
        return tickInterval;
    }

    /** Runs {@code fn(buttonId, blockEntity, player)} on the server when a screen button with an id is pressed. */
    public BlockBuilderJS onButton(Function fn) {
        this.buttonFunction = fn;
        return this;
    }

    public Function buttonFunction() {
        return buttonFunction;
    }

    /** Defines the block's screen: {@code fn(screen)} gets the same kind of config as {@code ClientEvents.screens}, plus {@code size} and {@code bar}. */
    public BlockBuilderJS screen(Function fn) {
        this.screenFunction = fn;
        this.screenConfig = null;
        return this;
    }

    /** The script's screen definition, built the first time a client needs it, or a default panel when the script gave none. */
    public synchronized TitleScreenConfig screenConfig() {
        if (screenConfig == null) {
            TitleScreenConfig config = TitleScreenConfig.forScreen();
            if (screenFunction != null) {
                try {
                    com.itmecasey.modjs.script.ModJSContextFactory.INSTANCE.call(cx ->
                        screenFunction.call(cx, screenFunction.getParentScope(), screenFunction.getParentScope(), new Object[] {config}));
                } catch (org.mozilla.javascript.RhinoException e) {
                    throw new IllegalStateException("Screen of block '" + id + "' failed: " + e.getMessage(), e);
                }
            }
            screenConfig = config;
        }
        return screenConfig;
    }

    /** Item id dropped instead of the block itself, or null. */
    public String dropItem() {
        return dropItem;
    }

    /** Drops this item (for example {@code "minecraft:diamond"}) instead of the block. */
    public BlockBuilderJS drops(String itemId) {
        this.dropItem = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        return this;
    }

    public boolean isDropsNothing() {
        return dropsNothing;
    }

    /** The tag this item is the canonical (unified) version of, or null. */
    public String unifyTag() {
        return unifyTag;
    }

    /**
     * Makes this the canonical item for a tag such as {@code "c:ingots/tin"}: it joins the tag and, once data loads,
     * replaces every other item in the tag in recipes, drops and inventories (see {@code Unify}).
     */
    public BlockBuilderJS unify(String tag) {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        this.unifyTag = id.contains(":") ? id : "c:" + id;
        return this;
    }

    /** One of basic, slab, stairs, wall, fence. */
    public String type() {
        return type;
    }

    /** Shape of the block: "basic" (default), "slab", "stairs", "wall" or "fence". */
    public BlockBuilderJS type(String type) {
        String t = type.toLowerCase();
        if (!java.util.List.of("basic", "slab", "stairs", "wall", "fence").contains(t)) {
            throw new IllegalArgumentException("Unknown block type '" + type + "'; use basic, slab, stairs, wall or fence");
        }
        this.type = t;
        return this;
    }

    public BlockBuilderJS displayName(String name) {
        this.displayName = name;
        return this;
    }

    public BlockBuilderJS hardness(double hardness) {
        this.hardness = (float) hardness;
        return this;
    }

    public BlockBuilderJS resistance(double resistance) {
        this.resistance = (float) resistance;
        return this;
    }

    /** Light emitted, 0 to 15. */
    public BlockBuilderJS lightLevel(int level) {
        this.lightLevel = Math.max(0, Math.min(15, level));
        return this;
    }

    public BlockBuilderJS requiresTool(boolean requiresTool) {
        this.requiresTool = requiresTool;
        return this;
    }

    /** Texture id such as {@code "mymod:block/ruby_block"} used on every face. */
    public BlockBuilderJS texture(String texture) {
        this.texture = texture;
        return this;
    }

    public BlockBuilderJS tab(String tabId) {
        this.tab = tabId.contains(":") ? tabId : "minecraft:" + tabId;
        return this;
    }

    /** Skips creating the block item. */
    public BlockBuilderJS noItem() {
        this.noItem = true;
        return this;
    }

    public BlockBuilderJS dropsNothing() {
        this.dropsNothing = true;
        return this;
    }
}
