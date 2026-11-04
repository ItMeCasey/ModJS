package com.itmecasey.modjs.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The parts of data pack JSON that differ between Minecraft versions. Each platform module installs the format for
 * its version with {@link #use(VersionFormat)} before any script runs.
 */
public interface VersionFormat {
    /** An ingredient for {@code "minecraft:stone"} or {@code "#minecraft:logs"}. */
    JsonElement ingredient(String token);

    /** The id or {@code #tag} an ingredient JSON element stands for, or null if it is not an ingredient. */
    String ingredientToken(JsonElement element);

    /** A recipe result object. */
    JsonObject result(String id, int count);

    /** Puts the result of a smelting-style recipe into the recipe JSON. */
    default void setCookingResult(JsonObject recipe, String id, int count) {
        recipe.add("result", result(id, count));
    }

    /** Puts the result of a stonecutting recipe into the recipe JSON. */
    default void setStonecuttingResult(JsonObject recipe, String id, int count) {
        recipe.add("result", result(id, count));
    }

    /** True when items need an {@code assets/<ns>/items/<id>.json} definition (Minecraft 1.21.4 and newer). */
    boolean usesItemDefinitions();

    static VersionFormat current() {
        return Holder.current;
    }

    static void use(VersionFormat format) {
        Holder.current = format;
    }

    final class Holder {
        static volatile VersionFormat current = new ModernFormat();

        private Holder() {
        }
    }
}
