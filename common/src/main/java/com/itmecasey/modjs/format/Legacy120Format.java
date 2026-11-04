package com.itmecasey.modjs.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Minecraft 1.20 to 1.20.6: like {@link LegacyFormat}, but results use the {@code item} key and cooking and stonecutting results are plain strings. */
public final class Legacy120Format implements VersionFormat {
    private final LegacyFormat ingredients = new LegacyFormat();

    @Override
    public JsonElement ingredient(String token) {
        return ingredients.ingredient(token);
    }

    @Override
    public String ingredientToken(JsonElement element) {
        return ingredients.ingredientToken(element);
    }

    @Override
    public JsonObject result(String id, int count) {
        JsonObject out = new JsonObject();
        out.addProperty("item", id);
        if (count > 1) {
            out.addProperty("count", count);
        }
        return out;
    }

    @Override
    public void setCookingResult(JsonObject recipe, String id, int count) {
        recipe.addProperty("result", id);
    }

    @Override
    public void setStonecuttingResult(JsonObject recipe, String id, int count) {
        recipe.addProperty("result", id);
        if (count > 1) {
            recipe.addProperty("count", count);
        }
    }

    @Override
    public boolean usesItemDefinitions() {
        return false;
    }
}
