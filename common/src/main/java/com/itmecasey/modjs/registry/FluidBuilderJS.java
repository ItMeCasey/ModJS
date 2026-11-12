package com.itmecasey.modjs.registry;

/** Configuration for a fluid created by {@code StartupEvents.registry('fluid', ...)}. It registers a source, a flowing fluid, a liquid block and a bucket. */
public final class FluidBuilderJS {
    private final String id;
    private final String namespace;
    private String displayName;
    private int color = 0xFF3F76E4;
    private int luminosity;
    private int density = 1000;
    private int viscosity = 1000;
    private int temperature = 300;
    private boolean noBucket;
    private String unifyTag;

    FluidBuilderJS(String rawId) {
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

    /** Tint as ARGB; a value without an alpha part (such as 0x00FF00) is treated as opaque. */
    public int color() {
        return (color >>> 24) == 0 ? color | 0xFF000000 : color;
    }

    public int luminosity() {
        return luminosity;
    }

    public int density() {
        return density;
    }

    public int viscosity() {
        return viscosity;
    }

    public int temperature() {
        return temperature;
    }

    public boolean isNoBucket() {
        return noBucket;
    }

    public String unifyTag() {
        return unifyTag;
    }

    /** Makes this the canonical fluid for a tag such as {@code "c:oil"} (see {@code Unify.fluid}). */
    public FluidBuilderJS unify(String tag) {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        this.unifyTag = id.contains(":") ? id : "c:" + id;
        return this;
    }

    public FluidBuilderJS displayName(String name) {
        this.displayName = name;
        return this;
    }

    public FluidBuilderJS color(int color) {
        this.color = color;
        return this;
    }

    /** Light emitted, 0 to 15. */
    public FluidBuilderJS luminosity(int level) {
        this.luminosity = Math.max(0, Math.min(15, level));
        return this;
    }

    /** Negative values make the fluid float up. */
    public FluidBuilderJS density(int density) {
        this.density = density;
        return this;
    }

    public FluidBuilderJS viscosity(int viscosity) {
        this.viscosity = viscosity;
        return this;
    }

    /** Kelvin; water is 300. */
    public FluidBuilderJS temperature(int temperature) {
        this.temperature = temperature;
        return this;
    }

    public FluidBuilderJS noBucket() {
        this.noBucket = true;
        return this;
    }
}
