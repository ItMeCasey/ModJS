package com.itmecasey.modjs.persistent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.script.ScriptManager;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.ScriptRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/**
 * Key-value data scripts keep between runs, saved as JSON in {@code modjs/persistent/<name>.json}.
 * Exposed to scripts as {@code Persistent.get('key')}, {@code Persistent.set('key', value)}, and so on.
 */
public final class PersistentStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Callable IDENTITY = (cx, scope, thisObj, args) -> args[1];

    private final String name;
    private JsonObject data;

    PersistentStore(String name) {
        this.name = name;
    }

    private Path file() {
        return ScriptManager.root().resolve("persistent").resolve(name + ".json");
    }

    private synchronized JsonObject data() {
        if (data == null) {
            Path file = file();
            data = new JsonObject();
            if (Files.isRegularFile(file)) {
                try {
                    JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    if (parsed.isJsonObject()) {
                        data = parsed.getAsJsonObject();
                    }
                } catch (IOException | RuntimeException e) {
                    LOGGER.error("Could not read {}", file, e);
                }
            }
        }
        return data;
    }

    /** Drops the in-memory copy so the next access reads the file again. */
    public synchronized void reload() {
        data = null;
    }

    public synchronized boolean has(String key) {
        return data().has(key);
    }

    /** The stored value as a script value, or {@code undefined}-like null when the key is missing. */
    public synchronized Object get(String key) {
        JsonElement element = data().get(key);
        if (element == null || element instanceof JsonNull) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsDouble();
            }
            return primitive.getAsString();
        }
        Context cx = Context.getCurrentContext();
        return NativeJSON.parse(cx, ScriptRuntime.getTopCallScope(cx), GSON.toJson(element), IDENTITY);
    }

    public Object get(String key, Object fallback) {
        Object value = get(key);
        return value == null ? fallback : value;
    }

    public synchronized void set(String key, Object value) {
        data().add(key, JsonUtil.toJson(value));
        save();
    }

    public synchronized void remove(String key) {
        data().remove(key);
        save();
    }

    public synchronized Set<String> keys() {
        return new TreeSet<>(data().keySet());
    }

    private void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(data()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Could not save {}", file, e);
        }
    }
}
