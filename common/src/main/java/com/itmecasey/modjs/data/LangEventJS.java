package com.itmecasey.modjs.data;

import com.itmecasey.modjs.event.EventJS;

import java.util.LinkedHashMap;
import java.util.Map;

/** The event passed to {@code ClientEvents.lang('en_us', event => ...)}. */
public final class LangEventJS extends EventJS {
    private final String language;
    private final Map<String, String> entries = new LinkedHashMap<>();

    public LangEventJS(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }

    /** {@code add('item.mymod.thing', 'Thing')} */
    public void add(String key, String value) {
        entries.put(key, value);
    }

    /** {@code renameItem('minecraft:diamond', 'Shiny Rock')} changes an item's display name. */
    public void renameItem(String id, String name) {
        add(key("item", id), name);
    }

    /** {@code renameBlock('minecraft:stone', 'Hard Rock')} changes a block's display name (its item follows). */
    public void renameBlock(String id, String name) {
        add(key("block", id), name);
    }

    /** {@code renameEntity('minecraft:zombie', 'Biter')} */
    public void renameEntity(String id, String name) {
        add(key("entity", id), name);
    }

    private static String key(String kind, String id) {
        String full = id.contains(":") ? id : "minecraft:" + id;
        return kind + "." + full.replace(':', '.').replace('/', '.');
    }

    public Map<String, String> entries() {
        return entries;
    }
}
