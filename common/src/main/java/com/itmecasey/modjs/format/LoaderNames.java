package com.itmecasey.modjs.format;

/**
 * Names that differ between mod loaders: Forge calls its data pack namespace {@code forge}, NeoForge calls it {@code neoforge}.
 * Each platform module installs its loader once, before scripts run.
 */
public final class LoaderNames {
    private static volatile String loader = "forge";

    private LoaderNames() {
    }

    public static void use(String name) {
        loader = name;
    }

    /** {@code forge} or {@code neoforge}. */
    public static String loader() {
        return loader;
    }

    /** A loader-namespaced id such as {@code forge:add_features} or {@code neoforge:add_features}. */
    public static String id(String path) {
        return loader + ":" + path;
    }

    /** The data pack folder for biome modifiers: {@code forge/biome_modifier} or {@code neoforge/biome_modifier}. */
    public static String biomeModifierFolder() {
        return loader + "/biome_modifier/";
    }
}
