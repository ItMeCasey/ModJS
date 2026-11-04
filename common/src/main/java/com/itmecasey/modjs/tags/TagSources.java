package com.itmecasey.modjs.tags;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the tag files that ship inside the game and mod jars, so removals can be computed. */
final class TagSources {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private TagSources() {
    }

    /** The id an entry stands for: the string itself, or the {@code id} of an object entry. */
    static String entryId(JsonElement entry) {
        if (entry.isJsonPrimitive()) {
            return entry.getAsString();
        }
        if (entry.isJsonObject() && entry.getAsJsonObject().has("id")) {
            return entry.getAsJsonObject().get("id").getAsString();
        }
        return "";
    }

    /** Every copy of the tag found on the class path, merged the way the game merges data packs. */
    static List<JsonElement> mergedValues(String registry, String namespace, String path) {
        String resource = "data/" + namespace + "/" + com.itmecasey.modjs.format.DataFolders.tags(registry) + "/" + path + ".json";
        Map<String, JsonElement> merged = new LinkedHashMap<>();
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), TagSources.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                Enumeration<URL> urls = loader.getResources(resource);
                while (urls.hasMoreElements()) {
                    read(urls.nextElement(), merged);
                }
            } catch (IOException e) {
                LOGGER.warn("Could not look up {}", resource, e);
            }
            if (!merged.isEmpty()) {
                break;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static void read(URL url, Map<String, JsonElement> merged) {
        try (InputStream in = url.openStream(); InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("replace") && json.get("replace").getAsBoolean()) {
                merged.clear();
            }
            JsonArray values = json.has("values") ? json.getAsJsonArray("values") : new JsonArray();
            for (JsonElement value : values) {
                merged.putIfAbsent(entryId(value), value);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read tag file {}", url, e);
        }
    }
}
