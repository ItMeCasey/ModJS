package com.itmecasey.modjs.item;

import com.itmecasey.modjs.json.JsonUtil;

/**
 * A script-side reference to an item stack: an id plus a count. Returned by {@code Item.of(...)} and accepted
 * anywhere scripts name an item. Also parses the {@code "3x minecraft:stone"} shorthand.
 */
public record ItemRef(String id, int count) {
    public ItemRef {
        if (id.indexOf(':') < 0) {
            id = "minecraft:" + id;
        }
        if (count < 1) {
            count = 1;
        }
    }

    public static ItemRef of(String text) {
        String trimmed = text.trim();
        int x = trimmed.indexOf("x ");
        if (x > 0) {
            String prefix = trimmed.substring(0, x);
            if (prefix.chars().allMatch(Character::isDigit)) {
                return new ItemRef(trimmed.substring(x + 2).trim(), Integer.parseInt(prefix));
            }
        }
        return new ItemRef(trimmed, 1);
    }

    /** Accepts a string, an {@link ItemRef}, or an object with {@code item} or {@code id} and optional {@code count}. */
    public static ItemRef parse(Object raw) {
        Object value = JsonUtil.unwrap(raw);
        if (value instanceof ItemRef ref) {
            return ref;
        }
        String text = JsonUtil.asString(value);
        if (text != null) {
            return of(text);
        }
        Object id = JsonUtil.property(value, "id");
        if (id == null) {
            id = JsonUtil.property(value, "item");
        }
        String idText = JsonUtil.asString(id);
        if (idText != null) {
            Object count = JsonUtil.property(value, "count");
            return new ItemRef(idText, count instanceof Number n ? n.intValue() : 1);
        }
        throw new IllegalArgumentException("Not an item: " + value);
    }

    public ItemRef withCount(int newCount) {
        return new ItemRef(id, newCount);
    }

    public ItemRef withChance(double chance) {
        return this;
    }
}
