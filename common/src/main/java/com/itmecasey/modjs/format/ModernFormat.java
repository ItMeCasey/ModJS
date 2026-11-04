package com.itmecasey.modjs.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Minecraft 1.21.2 and newer: ingredients are plain strings and items use item definitions. */
public final class ModernFormat implements VersionFormat {
    @Override
    public JsonElement ingredient(String token) {
        return new JsonPrimitive(token);
    }

    @Override
    public String ingredientToken(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }

    @Override
    public JsonObject result(String id, int count) {
        JsonObject out = new JsonObject();
        out.addProperty("id", id);
        if (count > 1) {
            out.addProperty("count", count);
        }
        return out;
    }

    @Override
    public boolean usesItemDefinitions() {
        return true;
    }
}
