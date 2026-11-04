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

    public Map<String, String> entries() {
        return entries;
    }
}
