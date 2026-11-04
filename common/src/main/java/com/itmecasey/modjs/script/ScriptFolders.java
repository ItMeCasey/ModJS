package com.itmecasey.modjs.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prepares the {@code modjs} folder on launch: creates the script directories, writes example scripts the first
 * time a directory is created, and refreshes the editor type declarations.
 */
public final class ScriptFolders {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private static final String STARTUP_EXAMPLE = """
        // Startup scripts run once while the game loads. Register new content here.
        // Textures go in modjs/assets/modjs/textures/item/<id>.png and block/<id>.png.
        //
        // StartupEvents.registry('item', event => {
        //   event.create('ruby').displayName('Ruby').tooltip('A shiny red gem');
        // });
        //
        // StartupEvents.registry('block', event => {
        //   event.create('ruby_block').hardness(5).resistance(6).requiresTool(true);
        // });
        """;

    private static final String SERVER_EXAMPLE = """
        // Server scripts run when a world loads and again on /modjs reload.
        //
        // ServerEvents.recipes(event => {
        //   event.shaped('minecraft:diamond', ['AAA', 'AAA', 'AAA'], { A: 'minecraft:coal' });
        //   event.smelting('minecraft:glass', 'minecraft:sand').xp(0.1);
        //   event.remove({ output: 'minecraft:torch' });
        // });
        //
        // ServerEvents.tags('item', event => {
        //   event.add('c:gems/ruby', 'modjs:ruby');
        // });
        //
        // PlayerEvents.chat(event => {
        //   if (event.message.includes('hello')) event.tell('Hello, ' + event.playerName + '!');
        // });
        """;

    private static final String CLIENT_EXAMPLE = """
        // Client scripts run on the client when resources load (F3+T reloads them).
        //
        // ClientEvents.lang('en_us', event => {
        //   event.add('item.modjs.ruby', 'Ruby');
        // });
        //
        // ItemEvents.tooltip(event => {
        //   event.add('minecraft:apple', 'Crunchy');
        // });
        """;

    private ScriptFolders() {
    }

    public static void prepare(Path root) {
        try {
            writeExampleIfNew(root, ScriptType.STARTUP, STARTUP_EXAMPLE);
            writeExampleIfNew(root, ScriptType.SERVER, SERVER_EXAMPLE);
            writeExampleIfNew(root, ScriptType.CLIENT, CLIENT_EXAMPLE);
            Files.createDirectories(root.resolve("assets"));
            Files.createDirectories(root.resolve("data"));
            copyTypes(root);
            Path config = root.resolve("jsconfig.json");
            if (!Files.exists(config)) {
                Files.writeString(config, """
                    {
                      "compilerOptions": {
                        "target": "ES2020",
                        "lib": ["ES2020"],
                        "checkJs": true,
                        "noEmit": true
                      },
                      "include": ["probe/modjs.d.ts", "startup_scripts/**/*.js", "server_scripts/**/*.js", "client_scripts/**/*.js"]
                    }
                    """.stripIndent(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.error("Could not prepare {}", root, e);
        }
    }

    private static void writeExampleIfNew(Path root, ScriptType type, String example) throws IOException {
        Path dir = root.resolve(type.directory);
        boolean isNew = !Files.exists(dir);
        Files.createDirectories(dir);
        if (isNew) {
            Files.writeString(dir.resolve("example.js"), example.stripIndent(), StandardCharsets.UTF_8);
        }
    }

    private static void copyTypes(Path root) throws IOException {
        try (InputStream in = ScriptFolders.class.getResourceAsStream("/modjs/modjs.d.ts")) {
            if (in == null) {
                return;
            }
            Path target = root.resolve("probe").resolve("modjs.d.ts");
            Files.createDirectories(target.getParent());
            Files.write(target, in.readAllBytes());
        }
    }
}
