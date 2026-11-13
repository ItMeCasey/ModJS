package com.itmecasey.modjs.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads what only the client has: language text, the resource pack file lists and key mappings. Loaded only on the client. */
final class ClientCollector {
    private ClientCollector() {
    }

    private record Kind(String name, String folder, String extension) {
    }

    private static final Kind[] KINDS = {
        new Kind("texture", "textures", ".png"),
        new Kind("model", "models", ".json"),
        new Kind("blockstate", "blockstates", ".json"),
        new Kind("particle", "particles", ".json"),
        new Kind("font", "font", ".json"),
        new Kind("sound", "sounds", ".ogg"),
    };

    static void collect(DumpData data, List<String> warnings) {
        var manager = Minecraft.getInstance().getResourceManager();
        int failures = 0;
        for (var entry : manager.listResources("lang", location -> location.getPath().endsWith("/en_us.json")).entrySet()) {
            try (InputStream in = entry.getValue().open()) {
                JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (json.isJsonObject()) {
                    for (var pair : ((JsonObject) json).entrySet()) {
                        if (pair.getValue().isJsonPrimitive()) {
                            data.lang.put(pair.getKey(), pair.getValue().getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                failures++;
            }
        }
        if (failures > 0) {
            warnings.add(failures + " language file(s) could not be read");
        }
        for (Kind kind : KINDS) {
            List<String> ids = new ArrayList<>();
            for (var location : manager.listResources(kind.folder(), l -> l.getPath().endsWith(kind.extension())).keySet()) {
                ids.add(GameCollector.stripId(location.getNamespace(), location.getPath(), kind.folder()).replaceAll(java.util.regex.Pattern.quote(kind.extension()) + "$", ""));
            }
            ids.sort(null);
            data.assets.put(kind.name(), ids);
        }
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            data.keyMappings.add(mapping.getName());
        }
        data.keyMappings.sort(null);
        data.hasClientData = true;
    }
}
