package com.itmecasey.modjs.persistent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.script.ScriptManager;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.ScriptRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The {@code JsonIO} global: read and write JSON files inside the {@code modjs} folder. Paths are relative to it and
 * cannot leave it.
 */
public final class JsonIOBinding {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Callable IDENTITY = (cx, scope, thisObj, args) -> args[1];

    private static Path resolve(String relative) {
        Path root = ScriptManager.root().toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw Context.reportRuntimeError("JsonIO paths must stay inside the modjs folder: " + relative);
        }
        return target;
    }

    public boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    /** Reads a JSON file into a script value, or returns null when the file does not exist. */
    public Object read(String path) {
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(text);
            Context cx = Context.getCurrentContext();
            return NativeJSON.parse(cx, ScriptRuntime.getTopCallScope(cx), GSON.toJson(parsed), IDENTITY);
        } catch (IOException | RuntimeException e) {
            throw Context.reportRuntimeError("Could not read " + path + ": " + e.getMessage());
        }
    }

    public void write(String path, Object value) {
        Path file = resolve(path);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(JsonUtil.toJson(value)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw Context.reportRuntimeError("Could not write " + path + ": " + e.getMessage());
        }
    }
}
