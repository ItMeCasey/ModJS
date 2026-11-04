package com.itmecasey.modjs.format;

/**
 * Data pack folder names, which Minecraft made singular in 1.21 ({@code recipe}, {@code loot_table}, {@code tags/item}) after
 * years of plural ones ({@code recipes}, {@code loot_tables}, {@code tags/items}). Each platform module picks its style once.
 */
public final class DataFolders {
    private static volatile boolean plural;

    private DataFolders() {
    }

    public static void usePlural(boolean value) {
        plural = value;
    }

    public static String recipe() {
        return plural ? "recipes" : "recipe";
    }

    public static String lootTable() {
        return plural ? "loot_tables" : "loot_table";
    }

    /** The folder for a tag registry, for example {@code tags/item} or {@code tags/items}. */
    public static String tags(String registry) {
        if (!plural) {
            return "tags/" + registry;
        }
        return "tags/" + switch (registry) {
            case "item" -> "items";
            case "block" -> "blocks";
            case "fluid" -> "fluids";
            case "entity_type" -> "entity_types";
            case "game_event" -> "game_events";
            default -> registry;
        };
    }
}
