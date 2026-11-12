package com.itmecasey.modjs.registry;

/** Configuration for a creative tab created by {@code StartupEvents.registry('creative_tab', ...)}. */
public final class CreativeTabBuilderJS {
    private final String id;
    private final String namespace;
    private String displayName;
    private String icon = "minecraft:stone";

    CreativeTabBuilderJS(String rawId) {
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

    /** The tab id items use in {@code .tab(...)}, for example {@code "modjs:main"}. */
    public String tabId() {
        return namespace + ":" + id;
    }

    public String displayName() {
        return displayName != null ? displayName : RegistryNames.titleCase(id);
    }

    public String icon() {
        return icon;
    }

    public CreativeTabBuilderJS displayName(String name) {
        this.displayName = name;
        return this;
    }

    /** Item id shown on the tab, for example {@code "modjs:ruby"} or {@code "minecraft:diamond"}. */
    public CreativeTabBuilderJS icon(String itemId) {
        this.icon = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        return this;
    }
}
