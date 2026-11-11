package com.itmecasey.modjs.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The data map of a scripted block entity: names to numbers, strings and booleans. It is saved with the block as one JSON string
 * and sent to clients with the block entity's update, so scripts on both sides read the same values.
 */
public final class ScriptBlockData {
    private final JsonObject json = new JsonObject();

    /** Parses saved data; anything that is not a JSON object gives an empty map. */
    public static ScriptBlockData fromJson(String text) {
        ScriptBlockData data = new ScriptBlockData();
        if (text == null || text.isEmpty()) {
            return data;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        data.json.add(entry.getKey(), entry.getValue());
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // damaged data starts again empty
        }
        return data;
    }

    public String toJson() {
        return json.toString();
    }

    public boolean isEmpty() {
        return json.size() == 0;
    }

    /** The value as a Boolean, Double or String, or null when unset. */
    public Object get(String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsDouble();
        }
        return primitive.getAsString();
    }

    /** Sets a number, string or boolean; null removes the key. Returns true when the data changed. */
    public boolean set(String key, Object value) {
        Object plain = com.itmecasey.modjs.json.JsonUtil.unwrap(value);
        JsonElement next;
        if (plain == null || plain instanceof org.mozilla.javascript.Undefined) {
            next = null;
        } else if (plain instanceof Boolean bool) {
            next = new JsonPrimitive(bool);
        } else if (plain instanceof Number number) {
            double d = number.doubleValue();
            next = d == Math.rint(d) && Math.abs(d) < 1e15 ? new JsonPrimitive((long) d) : new JsonPrimitive(d);
        } else if (plain instanceof CharSequence text) {
            next = new JsonPrimitive(text.toString());
        } else {
            throw new IllegalArgumentException("Block data holds numbers, strings and booleans, not " + plain.getClass().getSimpleName());
        }
        JsonElement old = json.get(key);
        if (next == null) {
            return json.remove(key) != null;
        }
        if (next.equals(old)) {
            return false;
        }
        json.add(key, next);
        return true;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : json.keySet()) {
            map.put(key, get(key));
        }
        return map;
    }
}
