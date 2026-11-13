package com.itmecasey.modjs.script;

import com.itmecasey.modjs.config.ConfigManager;
import com.itmecasey.modjs.config.ScriptConfig;
import com.itmecasey.modjs.unify.UnifyFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A modjs folder full of the user's own files must come out of every startup and reload routine byte for byte the same. */
class UserFilesSafetyTest {
    @TempDir
    Path root;

    @AfterEach
    void tearDown() {
        ConfigManager.clear();
    }

    private void put(String relative, byte[] bytes) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private void put(String relative, String text) throws IOException {
        put(relative, text.getBytes(StandardCharsets.UTF_8));
    }

    private void userTree() throws IOException {
        put("startup_scripts/items.js", "StartupEvents.registry('item', e => { e.create('ruby'); });\n");
        put("startup_scripts/sub/deeper/more.js", "// nested\n");
        put("startup_scripts/example.js", "// my own example.js, edited\nconsole.log('mine');\n");
        put("server_scripts/broken.js", "this is not javascript {{{\n");
        put("server_scripts/ok.js", "ServerEvents.recipes(e => {});\n");
        put("server_scripts/odd name (1) üñï.js", "// odd name\n");
        put("server_scripts/not-a-script.txt", "notes\n");
        put("client_scripts/c.js", "ClientEvents.tick(() => {});\n");
        put("client_scripts/empty.js", new byte[0]);
        put("data/mypack/recipes/x.json", "{\"type\":\"minecraft:crafting_shapeless\"}\n");
        put("assets/mypack/lang/en_us.json", "{\"item.mypack.ruby\":\"Ruby\"}\n");
        put("assets/mypack/textures/blob.bin", new byte[] {0, 1, 2, (byte) 0xFF, (byte) 0xFE, 10, 13});
        put("config/unify.json", "{ \"prefer\": [\"mymod\"], \"note\": \"custom\" }\n");
        put("config/mypack.json5", "// my own comment\n{ \"speed\": 4, /* inline */ \"extra\": true }\n");
        put("config/mypack.json5.bak", "older backup\n");
        put("jsconfig.json", "{ \"compilerOptions\": { \"target\": \"ES5\" } }\n");
        put("README.txt", "readme\r\nwith crlf\r\n");
        put("mystuff/deep/er/file.bin", new byte[] {9, 8, 7});
        put("persistent/world.json", "{\"a\":1}");
        put("probe/notes.txt", "my notes inside probe\n");
        put("data/scores.json", "{\"alice\":3}");
    }

    private Map<String, byte[]> snapshot() throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                files.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            }
        }
        return files;
    }

    private Set<String> directories() throws IOException {
        Set<String> dirs = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isDirectory).forEach(p -> dirs.add(root.relativize(p).toString().replace('\\', '/')));
        }
        return dirs;
    }

    private void assertUntouched(Map<String, byte[]> before, Set<String> dirsBefore, String when) throws IOException {
        Map<String, byte[]> after = snapshot();
        for (Map.Entry<String, byte[]> entry : before.entrySet()) {
            assertTrue(after.containsKey(entry.getKey()), when + ": " + entry.getKey() + " is gone");
            assertArrayEquals(entry.getValue(), after.get(entry.getKey()), when + ": " + entry.getKey() + " changed");
        }
        assertTrue(directories().containsAll(dirsBefore), when + ": a folder is gone");
        assertTrue(after.keySet().stream().noneMatch(name -> name.contains(".tmp-")), when + ": a temporary file was left behind");
    }

    private void runEverything() throws IOException {
        ScriptManager.setRoot(root);
        ScriptFolders.prepare(root);
        UnifyFile.applyStartup(root);
        UnifyFile.applyServer(root);
        for (ScriptType type : ScriptType.values()) {
            ScriptManager.of(type).reload();
        }
        for (ScriptType type : ScriptType.values()) {
            ScriptManager.of(type).reload();
        }
        // a config whose file the user wrote: read, reloaded and polled, never rewritten
        ScriptManager.of(ScriptType.SERVER).eval("var cfg = Config.register('mypack', { speed: Config.number(1.5), enabled: Config.bool(true) });"
            + " cfg.reload(); Config.reload(); var scores = JsonIO.read('data/scores.json');", "safety-test");
        ConfigManager.pollNow(false);
        ConfigManager.pollNow(true);
    }

    @Test
    void everyUserFileSurvivesStartupAndReloadsByteForByte() throws IOException {
        userTree();
        Map<String, byte[]> before = snapshot();
        Set<String> dirs = directories();
        runEverything();
        assertUntouched(before, dirs, "first run");
        runEverything();
        assertUntouched(before, dirs, "second run");
        // the typings file in probe is ModJS's, so it was created next to the user's notes; nothing else new appeared in the user's folders
        Set<String> added = new TreeSet<>(snapshot().keySet());
        added.removeAll(before.keySet());
        assertEquals(Set.of("probe/modjs.d.ts"), added);
    }

    @Test
    void scriptFolderExamplesAreOnlyWrittenForFoldersThatDidNotExist() throws IOException {
        ScriptManager.setRoot(root);
        ScriptFolders.prepare(root);
        assertTrue(Files.isRegularFile(root.resolve("server_scripts/example.js")));
        assertTrue(Files.isRegularFile(root.resolve("jsconfig.json")));
        // the user edits and removes things; a second start recreates nothing they removed from existing folders and keeps their edits
        Files.writeString(root.resolve("server_scripts/example.js"), "// edited\n");
        Files.delete(root.resolve("startup_scripts/example.js"));
        Files.writeString(root.resolve("jsconfig.json"), "{}");
        ScriptFolders.prepare(root);
        assertEquals("// edited\n", Files.readString(root.resolve("server_scripts/example.js")));
        assertFalse(Files.exists(root.resolve("startup_scripts/example.js")));
        assertEquals("{}", Files.readString(root.resolve("jsconfig.json")));
    }

    @Test
    void theTypingsFileIsRewrittenOnlyWhenItDiffers() throws IOException, InterruptedException {
        ScriptManager.setRoot(root);
        ScriptFolders.prepare(root);
        Path types = root.resolve("probe/modjs.d.ts");
        assertTrue(Files.isRegularFile(types));
        long stamp = Files.getLastModifiedTime(types).toMillis();
        Thread.sleep(20);
        ScriptFolders.prepare(root);
        assertEquals(stamp, Files.getLastModifiedTime(types).toMillis());
        Files.writeString(types, "stale");
        ScriptFolders.prepare(root);
        assertTrue(Files.readString(types).length() > 100);
    }

    @Test
    void safeFilesNeverReplaceWhatExistsAndLeaveNoTemporaryFiles() throws IOException {
        Path file = root.resolve("a/b.txt");
        assertTrue(SafeFiles.createIfAbsent(file, "one"));
        assertFalse(SafeFiles.createIfAbsent(file, "two"));
        assertEquals("one", Files.readString(file));
        SafeFiles.writeAtomic(file, "three");
        assertEquals("three", Files.readString(file));
        assertFalse(SafeFiles.writeIfChanged(file, "three".getBytes(StandardCharsets.UTF_8)));
        assertEquals(root.resolve("a/b.txt.bak"), SafeFiles.backup(file));
        assertEquals(root.resolve("a/b.txt.bak-2"), SafeFiles.backup(file));
        assertEquals("three", Files.readString(root.resolve("a/b.txt.bak")));
        try (Stream<Path> list = Files.list(root.resolve("a"))) {
            assertEquals(3, list.count());
        }
    }

    @Test
    void anExplicitSaveKeepsACopyOfTheFileTheUserEdited() throws IOException {
        put("config/mypack.json5", "// mine\n{ \"speed\": 4 }\n");
        byte[] original = Files.readAllBytes(root.resolve("config/mypack.json5"));
        ScriptManager.setRoot(root);
        ScriptConfig config = ConfigManager.register("mypack", ScriptConfig.Side.COMMON, schemaWithSpeed());
        assertEquals(4.0, config.get("speed"));
        assertArrayEquals(original, Files.readAllBytes(root.resolve("config/mypack.json5")));
        config.save();
        assertArrayEquals(original, Files.readAllBytes(root.resolve("config/mypack.json5.bak")));
        assertFalse(Arrays.equals(original, Files.readAllBytes(root.resolve("config/mypack.json5"))));
    }

    private static com.itmecasey.modjs.config.ConfigSpec.GroupSpec schemaWithSpeed() {
        com.itmecasey.modjs.config.ConfigSpec.GroupSpec s = new com.itmecasey.modjs.config.ConfigSpec.GroupSpec();
        s.children.put("speed", new com.itmecasey.modjs.config.ConfigSpec.NumberSpec(false, 1.5, null, null));
        s.children.put("mode", new com.itmecasey.modjs.config.ConfigSpec.StringSpec("a", 0, null));
        return s;
    }
}
