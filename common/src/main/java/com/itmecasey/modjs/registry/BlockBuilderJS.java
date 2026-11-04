package com.itmecasey.modjs.registry;

/** Configuration for a block created by {@code StartupEvents.registry('block', ...)}. */
public final class BlockBuilderJS {
    private final String id;
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

    BlockBuilderJS(String id) {
        this.id = id;
    }

    public String id() {
        return id;
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
        return texture != null ? texture : RegistryManager.NAMESPACE + ":block/" + id;
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
