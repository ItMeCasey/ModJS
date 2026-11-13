package com.itmecasey.modjs.probe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Writes one dump into {@code modjs/probe/}: the declaration files, the data files in {@code probe/data/}, and the project config. */
public final class DumpOutput {
    /** The jsconfig.json ModJS used to write; a file that matches it exactly is upgraded, any other is left alone. */
    private static final String OLD_JSCONFIG_INCLUDE = "\"include\": [\"probe/modjs.d.ts\", \"startup_scripts/**/*.js\", \"server_scripts/**/*.js\", \"client_scripts/**/*.js\"]";

    public static final String JSCONFIG = """
        {
          "modjs": { "generated": true },
          "compilerOptions": {
            "target": "ES2020",
            "lib": ["ES2020"],
            "checkJs": true,
            "noEmit": true
          },
          "include": ["probe/**/*.d.ts", "startup_scripts/**/*.js", "server_scripts/**/*.js", "client_scripts/**/*.js"]
        }
        """.stripIndent();

    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private DumpOutput() {
    }

    public static JsonObject meta(DumpData data, List<String> warnings) {
        JsonObject meta = new JsonObject();
        meta.addProperty("format", 1);
        meta.addProperty("minecraft", data.minecraftVersion);
        meta.addProperty("loader", data.loader);
        meta.addProperty("modjs", data.modjsVersion);
        meta.addProperty("side", data.side);
        meta.addProperty("generatedAt", data.generatedAt);
        meta.addProperty("clientData", data.hasClientData);
        JsonObject counts = new JsonObject();
        counts.addProperty("registries", data.registries.size());
        counts.addProperty("ids", data.totalIds());
        counts.addProperty("tagRegistries", data.tags.size());
        counts.addProperty("recipes", data.recipes.size());
        counts.addProperty("langKeys", data.lang.size());
        meta.add("counts", counts);
        JsonArray mods = new JsonArray();
        for (DumpData.Mod mod : data.mods) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", mod.id());
            entry.addProperty("name", mod.name());
            entry.addProperty("version", mod.version());
            mods.add(entry);
        }
        meta.add("mods", mods);
        JsonArray warn = new JsonArray();
        warnings.forEach(warn::add);
        meta.add("warnings", warn);
        return meta;
    }

    /**
     * Writes everything. {@code curatedTypes} is the text of the hand-written {@code modjs.d.ts} (or empty) and {@code classes} the
     * script-facing classes found in the mod; both only feed {@code api.d.ts}.
     */
    public static List<String> write(ProbeWriter writer, Path modjsRoot, DumpData data, List<String> warnings,
                                     String curatedTypes, Collection<Class<?>> classes) throws IOException {
        List<String> written = new ArrayList<>();
        for (Map.Entry<String, String> file : Typings.files(data).entrySet()) {
            writer.write(file.getKey(), file.getValue());
            written.add(file.getKey());
        }
        if (!classes.isEmpty()) {
            writer.write("api.d.ts", ApiReflector.generate(curatedTypes, classes));
            written.add("api.d.ts");
        }
        json(writer, written, "data/registries.json", data.registries);
        json(writer, written, "data/tags.json", data.tags);
        json(writer, written, "data/states.json", data.blockStates);
        json(writer, written, "data/gamerules.json", data.gameRules);
        json(writer, written, "data/commands.json", data.commands);
        json(writer, written, "data/recipes.json", data.recipes);
        if (data.hasClientData) {
            json(writer, written, "data/lang.json", data.lang);
            json(writer, written, "data/assets.json", data.assets);
            json(writer, written, "data/keys.json", data.keyMappings);
        }
        writer.write("data/meta.json", PRETTY.toJson(meta(data, warnings)) + "\n");
        written.add("data/meta.json");
        ensureProjectConfig(modjsRoot);
        return written;
    }

    private static void json(ProbeWriter writer, List<String> written, String name, Object value) throws IOException {
        writer.write(name, COMPACT.toJson(value));
        written.add(name);
    }

    /** Writes {@code jsconfig.json} when it is missing or is the one ModJS used to generate; a user's own file is never changed. */
    public static boolean ensureProjectConfig(Path modjsRoot) throws IOException {
        Path config = modjsRoot.resolve("jsconfig.json");
        if (Files.exists(config)) {
            String text = Files.readString(config, StandardCharsets.UTF_8);
            boolean generated = text.contains("\"generated\": true") && text.contains("\"modjs\"");
            boolean oldDefault = text.replace("\r\n", "\n").contains(OLD_JSCONFIG_INCLUDE);
            if (!generated && !oldDefault) {
                return false;
            }
        }
        Files.writeString(config, JSCONFIG, StandardCharsets.UTF_8);
        return true;
    }
}
