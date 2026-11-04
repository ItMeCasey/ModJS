package com.itmecasey.modjs.registry;

/** Configuration for a creative tab created by {@code StartupEvents.registry('creative_tab', ...)}. */
public final class CreativeTabBuilderJS {
    private final String id;
    private String displayName;
    private String icon = "minecraft:stone";

    CreativeTabBuilderJS(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The tab id items use in {@code .tab(...)}, for example {@code "modjs:main"}. */
    public String tabId() {
        return RegistryManager.NAMESPACE + ":" + id;
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
