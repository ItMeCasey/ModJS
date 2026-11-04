package com.itmecasey.modjs.recipe;

import com.google.gson.JsonObject;

/** A recipe being assembled by a script; chain {@code id}, {@code group}, {@code xp}, {@code cookingTime}. */
public final class RecipeBuilderJS {
    private final JsonObject json;
    private String id;

    RecipeBuilderJS(JsonObject json) {
        this.json = json;
    }

    JsonObject json() {
        return json;
    }

    String requestedId() {
        return id;
    }

    /** Sets the recipe id, for example {@code "kubejs:my_recipe"}; without a namespace {@code modjs} is used. */
    public RecipeBuilderJS id(String recipeId) {
        this.id = recipeId;
        return this;
    }

    public RecipeBuilderJS group(String group) {
        json.addProperty("group", group);
        return this;
    }

    public RecipeBuilderJS category(String category) {
        json.addProperty("category", category);
        return this;
    }

    public RecipeBuilderJS xp(double experience) {
        json.addProperty("experience", experience);
        return this;
    }

    public RecipeBuilderJS cookingTime(int ticks) {
        json.addProperty("cookingtime", ticks);
        return this;
    }
}
