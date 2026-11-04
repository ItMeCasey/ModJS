package com.itmecasey.modjs.item;

import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Context;

/** The {@code Item} global. */
public final class ItemBinding {
    public ItemRef of(Object item) {
        return ItemRef.parse(item);
    }

    public ItemRef of(Object item, Object count) {
        ItemRef ref = ItemRef.parse(item);
        return count instanceof Number n ? ref.withCount(n.intValue()) : ref;
    }

    public String toString() {
        return "Item";
    }
}
