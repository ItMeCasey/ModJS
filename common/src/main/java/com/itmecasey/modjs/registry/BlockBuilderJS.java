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

    public boolean isDropsNothing() {
        return dropsNothing;
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
