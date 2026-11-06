package com.itmecasey.modjs.persistent;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The JSON file and persistent value helpers scripts use, exercised from a real script. */
class HelpersTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        PersistentBinding.resetCache();
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private void run(String source) throws IOException {
        Path file = root.resolve("startup_scripts/helpers.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        ScriptManager.of(ScriptType.STARTUP).reload();
        assertEquals(0, ScriptManager.of(ScriptType.STARTUP).errors().size(), ScriptManager.of(ScriptType.STARTUP).errors().toString());
    }

    @Test
    void jsonIoRoundTripsNestedValues() throws IOException {
        run("""
            JsonIO.write('config/data.json', { name: 'ruby', count: 3, tags: ['a', 'b'], nested: { ok: true } });
            const back = JsonIO.read('config/data.json');
            if (back.name !== 'ruby' || back.count !== 3 || back.tags.length !== 2 || back.nested.ok !== true) throw 'round trip lost data';
            if (!JsonIO.exists('config/data.json') || JsonIO.exists('config/none.json')) throw 'exists is wrong';
            if (JsonIO.read('config/none.json') !== null) throw 'a missing file reads as null';
            """);
        assertTrue(Files.isRegularFile(root.resolve("config/data.json")));
    }

    @Test
    void jsonIoRefusesPathsOutsideTheFolder() throws IOException {
        Path file = root.resolve("startup_scripts/escape.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "JsonIO.write('../outside.json', {});");
        ScriptManager.of(ScriptType.STARTUP).reload();
        assertFalse(ScriptManager.of(ScriptType.STARTUP).errors().isEmpty());
        assertFalse(Files.exists(root.getParent().resolve("outside.json")));
    }

    @Test
    void persistentValuesSurviveAReload() throws IOException {
        run("""
            Persistent.set('kills', 7);
            Persistent.store('world_notes').set('title', 'Hub');
            Persistent.player('1234-abcd').set('level', 12);
            """);
        PersistentBinding.resetCache();
        run("""
            if (Persistent.get('kills') !== 7) throw 'main value lost';
            if (Persistent.store('world_notes').get('title') !== 'Hub') throw 'named store lost';
            if (Persistent.player('1234-abcd').get('level') !== 12) throw 'player store lost';
            if (!Persistent.has('kills')) throw 'has is wrong';
            Persistent.remove('kills');
            if (Persistent.has('kills')) throw 'remove did not remove';
            if (Persistent.get('missing', 'fallback') !== 'fallback') throw 'fallback not used';
            """);
        assertTrue(Files.isRegularFile(root.resolve("persistent/main.json")));
        assertTrue(Files.isRegularFile(root.resolve("persistent/player_1234-abcd.json")));
    }

    @Test
    void persistentStoreNamesAreChecked() throws IOException {
        Path file = root.resolve("startup_scripts/bad.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Persistent.store('../escape');");
        ScriptManager.of(ScriptType.STARTUP).reload();
        assertFalse(ScriptManager.of(ScriptType.STARTUP).errors().isEmpty());
    }
}
