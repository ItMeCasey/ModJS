package com.itmecasey.modjs.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptFoldersTest {
    @TempDir
    Path root;

    @Test
    void createsDirectoriesExamplesAndTypes() throws IOException {
        ScriptFolders.prepare(root);

        for (ScriptType type : ScriptType.values()) {
            assertTrue(Files.isRegularFile(root.resolve(type.directory).resolve("example.js")), type.directory);
        }
        assertTrue(Files.isDirectory(root.resolve("assets")));
        assertTrue(Files.isDirectory(root.resolve("data")));
        assertTrue(Files.readString(root.resolve("probe/modjs.d.ts")).contains("declare const ServerEvents"));
        assertTrue(Files.isRegularFile(root.resolve("jsconfig.json")));
    }

    @Test
    void doesNotOverwriteUserScriptsOrExamples() throws IOException {
        Path server = root.resolve("server_scripts");
        Files.createDirectories(server);
        Files.writeString(server.resolve("mine.js"), "// mine");
        ScriptFolders.prepare(root);

        assertEquals("// mine", Files.readString(server.resolve("mine.js")));
        assertTrue(!Files.exists(server.resolve("example.js")), "existing directories get no example");

        Files.writeString(root.resolve("startup_scripts/example.js"), "// edited");
        ScriptFolders.prepare(root);
        assertEquals("// edited", Files.readString(root.resolve("startup_scripts/example.js")));
    }

    @Test
    void exampleScriptsLoadWithoutErrors() {
        ScriptManager.setRoot(root);
        ScriptFolders.prepare(root);
        for (ScriptType type : ScriptType.values()) {
            ScriptManager manager = ScriptManager.of(type);
            manager.reload();
            assertEquals(0, manager.errors().size(), manager.errors().toString());
            assertEquals(1, manager.loadedCount());
        }
    }
}
