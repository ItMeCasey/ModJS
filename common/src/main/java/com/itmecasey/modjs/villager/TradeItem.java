package com.itmecasey.modjs.villager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.item.ItemRef;
import com.itmecasey.modjs.json.JsonUtil;

import java.util.regex.Pattern;

/**
 * An item in a trade: an id, a count, and optionally what the stack carries ({@code components} on 1.21.1 and 26.x, {@code nbt} text on 1.20.1).
 * Costs ignore the extras on 26.x and 1.21.1 only when they are absent; a result carries them.
 */
public record TradeItem(String id, int count, JsonObject components, String nbt) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public TradeItem {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("'" + id + "' is not an item id such as minecraft:emerald");
        }
        if (count < 1 || count > 99) {
            throw new IllegalArgumentException("the count of " + id + " must be 1 to 99, not " + count);
        }
    }

    /** Accepts {@code 'minecraft:emerald'}, {@code '5x minecraft:emerald'}, {@code Item.of(...)}, or {@code {item, count, components, nbt}}. */
    public static TradeItem parse(Object raw, String field) {
        Object value = JsonUtil.unwrap(raw);
        if (value == null) {
            throw new IllegalArgumentException("'" + field + "' is missing");
        }
        try {
            if (value instanceof ItemRef ref) {
                return new TradeItem(ref.id(), ref.count(), null, null);
            }
            String text = JsonUtil.asString(value);
            if (text != null) {
                ItemRef ref = ItemRef.of(text);
                return new TradeItem(ref.id(), ref.count(), null, null);
            }
            Object id = JsonUtil.property(value, "item");
            if (id == null) {
                id = JsonUtil.property(value, "id");
            }
            String idText = JsonUtil.asString(id);
            if (idText == null) {
                throw new IllegalArgumentException("'" + field + "' must be an item: 'minecraft:emerald', '5x minecraft:emerald', Item.of(...) or { item: 'minecraft:emerald', count: 5 }");
            }
            ItemRef ref = ItemRef.of(idText);
            Object count = JsonUtil.property(value, "count");
            int n = count instanceof Number number ? number.intValue() : ref.count();
            JsonObject components = null;
            Object comps = JsonUtil.property(value, "components");
            if (comps != null) {
                JsonElement json = JsonUtil.toJson(comps);
                if (!json.isJsonObject()) {
                    throw new IllegalArgumentException("'" + field + ".components' must be an object");
                }
                components = json.getAsJsonObject();
            }
            String nbt = null;
            Object tag = JsonUtil.property(value, "nbt");
            if (tag != null) {
                nbt = JsonUtil.asString(tag);
                if (nbt == null) {
                    nbt = JsonUtil.toJson(tag).toString();
                }
            }
            return new TradeItem(ref.id(), n, components, nbt);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("'" + field)) {
                throw e;
            }
            throw new IllegalArgumentException("'" + field + "': " + e.getMessage());
        }
    }

    /** The {@code gives} / {@code wants} object of a 26.x trade file. */
    public JsonObject toJson(boolean withComponents) {
        JsonObject out = new JsonObject();
        out.addProperty("id", id);
        if (count != 1) {
            out.addProperty("count", count);
        }
        if (withComponents && components != null && components.size() > 0) {
            out.add("components", components.deepCopy());
        }
        return out;
    }
}
