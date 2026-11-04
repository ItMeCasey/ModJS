package com.itmecasey.modjs.data;

import com.google.gson.JsonElement;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.pack.GeneratedData;
import org.mozilla.javascript.Context;

/**
 * Raw file access for data and asset events: {@code event.addJson('mymod:loot_table/blocks/x', {...})} writes
 * {@code data/mymod/loot_table/blocks/x.json} (or the assets equivalent).
 */
public final class DataEventJS extends EventJS {
    private final GeneratedData data;

    public DataEventJS(GeneratedData data) {
        this.data = data;
    }

    private static String[] split(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            throw Context.reportRuntimeError("Expected 'namespace:path', got '" + id + "'");
        }
        return new String[] {id.substring(0, colon), id.substring(colon + 1)};
    }

    public void addJson(String id, Object json) {
        String[] parts = split(id);
        JsonElement element = JsonUtil.toJson(json);
        data.putJson(parts[0], parts[1], element);
    }

    /** Writes a text file; the path must include its extension. */
    public void addText(String id, String text) {
        String[] parts = split(id);
        data.putText(parts[0], parts[1], text);
    }

    public boolean exists(String id) {
        String[] parts = split(id);
        return data.has(parts[0], parts[1] + ".json") || data.has(parts[0], parts[1]);
    }
}
