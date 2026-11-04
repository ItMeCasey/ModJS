package com.itmecasey.modjs.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Minecraft 1.21 to 1.21.1: ingredients are {@code {"item": id}} or {@code {"tag": id}} objects. */
public final class LegacyFormat implements VersionFormat {
    @Override
    public JsonElement ingredient(String token) {
        JsonObject out = new JsonObject();
        if (token.startsWith("#")) {
            out.addProperty("tag", token.substring(1));
        } else {
            out.addProperty("item", token);
        }
        return out;
    }

    @Override
    public String ingredientToken(JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.size() != 1) {
            return null;
        }
        if (object.has("item") && object.get("item").isJsonPrimitive()) {
            return object.get("item").getAsString();
        }
        if (object.has("tag") && object.get("tag").isJsonPrimitive()) {
            return "#" + object.get("tag").getAsString();
        }
        return null;
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
        return false;
    }
}
