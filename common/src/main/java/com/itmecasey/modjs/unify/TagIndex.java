package com.itmecasey.modjs.unify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Resolves item tags from the tag JSON files of every loaded pack. Keeps unification independent of registry state, so
 * the same code runs while recipes reload (before tags are bound) and on every Minecraft version.
 */
public final class TagIndex {
    private final Map<String, List<JsonObject>> files;

    /** @param files tag id (for example {@code c:ingots/iron}) to its files, lowest-priority pack first */
    public TagIndex(Map<String, List<JsonObject>> files) {
        this.files = files;
    }

    /** Every tag id that has at least one file. */
    public Set<String> tagIds() {
        return new TreeSet<>(files.keySet());
    }

    /** Item ids in the tag, with nested {@code #tags} expanded. Unknown or cyclic tags contribute nothing. */
    public Set<String> resolve(String tag) {
        Set<String> out = new LinkedHashSet<>();
        collect(tag.startsWith("#") ? tag.substring(1) : tag, out, new LinkedHashSet<>());
        return out;
    }

    private void collect(String tag, Set<String> out, Set<String> visiting) {
        List<JsonObject> list = files.get(tag);
        if (list == null || !visiting.add(tag)) {
            return;
        }
        List<String> direct = new ArrayList<>();
        List<String> nested = new ArrayList<>();
        for (JsonObject file : list) {
            if (file.has("replace") && file.get("replace").getAsBoolean()) {
                direct.clear();
                nested.clear();
            }
            JsonArray values = file.has("values") ? file.getAsJsonArray("values") : new JsonArray();
            for (JsonElement value : values) {
                String text = value.isJsonPrimitive() ? value.getAsString()
                    : value.isJsonObject() && value.getAsJsonObject().has("id") ? value.getAsJsonObject().get("id").getAsString() : null;
                if (text == null) {
                    continue;
                }
                if (text.startsWith("#")) {
                    nested.add(text.substring(1));
                } else {
                    direct.add(text);
                }
            }
        }
        out.addAll(direct);
        for (String child : nested) {
            collect(child, out, visiting);
        }
        visiting.remove(tag);
    }
}
