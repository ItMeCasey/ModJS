package com.itmecasey.modjs.script;

import java.util.function.Predicate;

/**
 * The {@code Platform} global: {@code Platform.mcVersion}, {@code Platform.isClient()}, {@code Platform.isLoaded('create')}.
 * The platform module fills in the values once during mod construction.
 */
public final class PlatformBinding {
    private static String mcVersion = "unknown";
    private static boolean client;
    private static Predicate<String> modLoaded = id -> false;

    /** Called by the platform module before any script runs. */
    public static void configure(String minecraftVersion, boolean isClient, Predicate<String> isModLoaded) {
        mcVersion = minecraftVersion;
        client = isClient;
        modLoaded = isModLoaded;
    }

    /** True when the running Minecraft version is {@code major.minor} or newer (patch numbers are ignored). */
    public static boolean atLeast(int major, int minor) {
        String[] parts = mcVersion.split("[.]");
        try {
            int actualMajor = Integer.parseInt(parts[0]);
            int actualMinor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return actualMajor > major || (actualMajor == major && actualMinor >= minor);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public String getLoader() {
        return com.itmecasey.modjs.format.LoaderNames.loader();
    }

    public boolean isClient() {
        return client;
    }

    public boolean isLoaded(String modId) {
        return modLoaded.test(modId);
    }
}
