package com.itmecasey.modjs.probe;

/** Pulls plain id strings out of Minecraft key objects without naming classes that changed between versions. */
public final class Ids {
    private Ids() {
    }

    /**
     * The registry or location a {@code ResourceKey} or {@code TagKey} names: both print as {@code Type[registry / location]}, and
     * the location is what follows the slash. A root registry key prints as {@code ResourceKey[minecraft:root / minecraft:item]}.
     */
    public static String location(Object key) {
        String text = String.valueOf(key);
        int slash = text.indexOf(" / ");
        int end = text.lastIndexOf(']');
        if (slash < 0 || end < slash) {
            return text;
        }
        return text.substring(slash + 3, end);
    }

    /** {@code minecraft:item} becomes {@code item}; ids from other namespaces keep their namespace. */
    public static String registryName(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** {@code #minecraft:logs} and {@code minecraft:logs} both become {@code minecraft:logs}. */
    public static String stripHash(String id) {
        return id.startsWith("#") ? id.substring(1) : id;
    }
}
