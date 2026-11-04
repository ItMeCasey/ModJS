package com.itmecasey.modjs.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.item.ItemRef;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.recipe.RecipeJson;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A loot table under construction: {@code table.addPool(pool => pool.addItem('minecraft:diamond'))}. */
public final class LootTableJS {
    private final String type;
    private final List<PoolJS> pools = new ArrayList<>();
    private final JsonArray functions = new JsonArray();

    LootTableJS(String type) {
        this.type = type;
    }

    public LootTableJS addPool(Consumer<PoolJS> configure) {
        PoolJS pool = new PoolJS();
        configure.accept(pool);
        pools.add(pool);
        return this;
    }

    /** Adds a raw function JSON object to the whole table. */
    public LootTableJS addFunction(Object json) {
        functions.add(JsonUtil.toJson(json));
        return this;
    }

    JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        JsonArray poolArray = new JsonArray();
        pools.forEach(pool -> poolArray.add(pool.toJson()));
        out.add("pools", poolArray);
        if (!functions.isEmpty()) {
            out.add("functions", functions);
        }
        return out;
    }

    /** One roll of a loot table: entries, conditions and functions. */
    public static final class PoolJS {
        private JsonElement rolls = new com.google.gson.JsonPrimitive(1);
        private final JsonArray entries = new JsonArray();
        private final JsonArray conditions = new JsonArray();

        /** {@code rolls(2)} for a fixed number or {@code rolls([1, 3])} for a uniform range. */
        public PoolJS rolls(Object value) {
            this.rolls = range(value);
            return this;
        }

        /** Adds an item entry. {@code count} may be a number or {@code [min, max]}; {@code weight} defaults to 1. */
        public PoolJS addItem(Object item, Object count, Object weight) {
            ItemRef ref = ItemRef.parse(item);
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", ref.id());
            if (weight instanceof Number number) {
                entry.addProperty("weight", number.intValue());
            }
            JsonArray functionList = new JsonArray();
            if (count != null && !(JsonUtil.unwrap(count) instanceof org.mozilla.javascript.Undefined)) {
                JsonObject setCount = new JsonObject();
                setCount.addProperty("function", "minecraft:set_count");
                setCount.add("count", range(count));
                functionList.add(setCount);
            } else if (ref.count() > 1) {
                JsonObject setCount = new JsonObject();
                setCount.addProperty("function", "minecraft:set_count");
                setCount.addProperty("count", ref.count());
                functionList.add(setCount);
            }
            if (!functionList.isEmpty()) {
                entry.add("functions", functionList);
            }
            entries.add(entry);
            return this;
        }

        public PoolJS addItem(Object item, Object count) {
            return addItem(item, count, null);
        }

        public PoolJS addItem(Object item) {
            return addItem(item, null, null);
        }

        /** Adds a raw entry JSON object. */
        public PoolJS addEntry(Object json) {
            entries.add(JsonUtil.toJson(json));
            return this;
        }

        public PoolJS addCondition(Object json) {
            conditions.add(JsonUtil.toJson(json));
            return this;
        }

        public PoolJS survivesExplosion() {
            return condition("minecraft:survives_explosion");
        }

        public PoolJS killedByPlayer() {
            return condition("minecraft:killed_by_player");
        }

        public PoolJS randomChance(double chance) {
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", "minecraft:random_chance");
            condition.addProperty("chance", chance);
            conditions.add(condition);
            return this;
        }

        private PoolJS condition(String id) {
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", id);
            conditions.add(condition);
            return this;
        }

        JsonObject toJson() {
            JsonObject out = new JsonObject();
            out.add("rolls", rolls);
            out.add("entries", entries);
            if (!conditions.isEmpty()) {
                out.add("conditions", conditions);
            }
            return out;
        }
    }

    /** A number stays a number; {@code [min, max]} becomes a uniform provider. */
    static JsonElement range(Object value) {
        Object unwrapped = JsonUtil.unwrap(value);
        if (unwrapped instanceof Number number) {
            return JsonUtil.toJson(number);
        }
        List<Object> list = RecipeJson.asList(unwrapped);
        if (list.size() == 2) {
            JsonObject uniform = new JsonObject();
            uniform.addProperty("type", "minecraft:uniform");
            uniform.add("min", JsonUtil.toJson(list.get(0)));
            uniform.add("max", JsonUtil.toJson(list.get(1)));
            return uniform;
        }
        throw Context.reportRuntimeError("Expected a number or [min, max], got " + value);
    }
}
