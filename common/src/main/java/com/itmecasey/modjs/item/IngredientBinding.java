package com.itmecasey.modjs.item;

import com.itmecasey.modjs.json.JsonUtil;

/** The {@code Ingredient} global: {@code Ingredient.of("#c:ingots/iron")} or {@code Ingredient.of(["a", "b"])}. */
public final class IngredientBinding {
    public Object of(Object ingredient) {
        return JsonUtil.toJson(ingredient);
    }

    public String tag(String tag) {
        return tag.startsWith("#") ? tag : "#" + tag;
    }
}
