package com.itmecasey.modjs.item;

import com.itmecasey.modjs.event.EventJS;
import org.mozilla.javascript.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Changes to existing items collected from {@code ItemEvents.modification}; platform modules apply them. */
public final class ItemModifications {
    private static final Map<String, Changes> CHANGES = new HashMap<>();

    private ItemModifications() {
    }

    /** What a script may change on an existing item. Null fields are left alone. */
    public static final class Changes {
        private Integer maxStackSize;
        private Integer maxDamage;
        private String rarity;

        public Integer getMaxStackSize() {
            return maxStackSize;
        }

        public Integer getMaxDamage() {
            return maxDamage;
        }

        public String getRarity() {
            return rarity;
        }

        public void setMaxStackSize(Integer value) {
            if (value < 1 || value > 99) {
                throw Context.reportRuntimeError("maxStackSize must be between 1 and 99, got " + value);
            }
            this.maxStackSize = value;
        }

        public void setMaxDamage(Integer value) {
            if (value < 1) {
                throw Context.reportRuntimeError("maxDamage must be at least 1, got " + value);
            }
            this.maxDamage = value;
        }

        /** One of {@code common}, {@code uncommon}, {@code rare}, {@code epic}. */
        public void setRarity(String value) {
            this.rarity = value;
        }
    }

    public static synchronized Changes forItem(String id) {
        return CHANGES.get(id);
    }

    static synchronized Changes edit(String id) {
        return CHANGES.computeIfAbsent(id, k -> new Changes());
    }

    public static synchronized java.util.Set<String> ids() {
        return new java.util.HashSet<>(CHANGES.keySet());
    }

    public static synchronized void clear() {
        CHANGES.clear();
    }

    /** The event passed to {@code ItemEvents.modification(event => ...)} in startup scripts. */
    public static final class ModificationEventJS extends EventJS {
        /** {@code event.modify('minecraft:ender_pearl', item => { item.maxStackSize = 64; })} */
        public void modify(String id, Consumer<Changes> change) {
            String full = id.contains(":") ? id : "minecraft:" + id;
            change.accept(edit(full));
        }

        public void modify(List<String> ids, Consumer<Changes> change) {
            for (String id : ids) {
                modify(id, change);
            }
        }
    }
}
