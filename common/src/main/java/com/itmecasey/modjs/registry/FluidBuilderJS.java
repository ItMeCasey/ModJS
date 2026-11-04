package com.itmecasey.modjs.registry;

/** Configuration for a fluid created by {@code StartupEvents.registry('fluid', ...)}. It registers a source, a flowing fluid, a liquid block and a bucket. */
public final class FluidBuilderJS {
    private final String id;
    private String displayName;
    private int color = 0xFF3F76E4;
    private int luminosity;
    private int density = 1000;
    private int viscosity = 1000;
    private int temperature = 300;
    private boolean noBucket;

    FluidBuilderJS(String id) {
        this.id = id;
    }

    public String id() {
        return id;
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
