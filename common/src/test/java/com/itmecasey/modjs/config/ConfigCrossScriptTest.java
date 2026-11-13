package com.itmecasey.modjs.config;

import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Configs shared between scripts: different files, different script types (startup, server, client), reloads and name collisions. */
class ConfigCrossScriptTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        ConfigManager.clear();
        for (ScriptType type : ScriptType.values()) {
            ScriptManager.of(type).reload();
        }
    }

    @AfterEach
    void tearDown() {
        ConfigManager.clear();
    }

    private static Object run(ScriptType type, String source) {
        return ScriptManager.of(type).eval(source, type.name().toLowerCase() + "-script.js");
    }

    private static double number(ScriptType type, String expression) {
        return ((Number) run(type, expression)).doubleValue();
    }

    private static String text(ScriptType type, String expression) {
        return String.valueOf(run(type, expression));
    }

    private void registerShared() {
        run(ScriptType.STARTUP, "var a = Config.register('shared', { value: Config.number(1), tags: Config.list('string', ['a']) });");
    }

    @Test
    void aConfigRegisteredByOneScriptIsReadByScriptsOfEveryType() {
        registerShared();
        run(ScriptType.SERVER, "var b = Config.get('shared');");
        run(ScriptType.CLIENT, "var c = Config.get('shared');");
        assertEquals(1.0, number(ScriptType.SERVER, "b.value"));
        assertEquals("a", text(ScriptType.SERVER, "b.tags[0]"));
        assertEquals(1.0, number(ScriptType.CLIENT, "c.value"));
        assertEquals("true", text(ScriptType.CLIENT, "Config.has('shared')"));
        assertEquals("shared", text(ScriptType.CLIENT, "Config.names()[0]"));
    }

    @Test
    void writesFromAnotherScriptAreSeenAtOnceAndThroughOnChange() {
        registerShared();
        run(ScriptType.SERVER, "var b = Config.get('shared');");
        run(ScriptType.CLIENT, "var c = Config.get('shared');");
        run(ScriptType.STARTUP, "var seenA = []; a.onChange(function (cfg, keys) { seenA.push(keys.join('+') + '=' + cfg.value); });");
        run(ScriptType.CLIENT, "var seenC = []; c.onChange(function (cfg, keys) { seenC.push(keys.join('+') + '=' + cfg.value); });");
        run(ScriptType.SERVER, "b.value = 5; b.set('tags', ['x', 'y']);");
        // seen at once, by the script that registered the config and by the client script
        assertEquals(5.0, number(ScriptType.STARTUP, "a.value"));
        assertEquals(2.0, number(ScriptType.STARTUP, "a.tags.length"));
        assertEquals(5.0, number(ScriptType.CLIENT, "c.value"));
        // through onChange: each callback runs on its own side's tick
        ConfigManager.pollNow(false);
        assertEquals("value+tags=5", text(ScriptType.STARTUP, "seenA.join(',')"));
        assertEquals("", text(ScriptType.CLIENT, "seenC.join(',')"));
        ConfigManager.pollNow(true);
        assertEquals("value+tags=5", text(ScriptType.CLIENT, "seenC.join(',')"));
        // a second change reaches both again, once each
        run(ScriptType.CLIENT, "c.value = 9;");
        ConfigManager.pollNow(false);
        ConfigManager.pollNow(true);
        assertEquals("value+tags=5,value=9", text(ScriptType.STARTUP, "seenA.join(',')"));
        assertEquals("value+tags=5,value=9", text(ScriptType.CLIENT, "seenC.join(',')"));
        // the same value again is not a change
        run(ScriptType.SERVER, "b.value = 9;");
        ConfigManager.pollNow(false);
        assertEquals("value+tags=5,value=9", text(ScriptType.STARTUP, "seenA.join(',')"));
    }

    @Test
    void saveFromAnotherScriptWritesTheFileAndKeepsACopyOfAnEditedOne() throws IOException {
        registerShared();
        run(ScriptType.SERVER, "var b = Config.get('shared'); b.value = 5; b.save();");
        Path file = root.resolve("config/shared.json5");
        assertTrue(Files.readString(file).contains("\"value\": 5.0"));
        assertFalse(Files.exists(root.resolve("config/shared.json5.bak")), "a file ModJS wrote itself needs no copy");
        // the user edits the file; the other script saves
        String edited = "// mine\n{ \"value\": 3 }\n";
        Files.writeString(file, edited);
        run(ScriptType.CLIENT, "var c = Config.get('shared'); c.reload();");
        assertEquals(3.0, number(ScriptType.STARTUP, "a.value"));
        run(ScriptType.CLIENT, "c.value = 4; c.save();");
        assertEquals(edited, Files.readString(root.resolve("config/shared.json5.bak")));
        assertTrue(Files.readString(file).contains("\"value\": 4.0"));
        run(ScriptType.SERVER, "b.save();");
        assertFalse(Files.exists(root.resolve("config/shared.json5.bak-2")));
    }

    @Test
    void severalConfigsFromThreeScriptsSurviveTheReloadOfOneScriptType() {
        run(ScriptType.STARTUP, "var s1 = Config.register('one', { n: 1 }); var s2 = Config.register('two', { n: 2 });");
        run(ScriptType.SERVER, "var v3 = Config.register('three', { n: 3 }, { side: 'server' }); var v4 = Config.register('four', { n: 4 }, { side: 'server' });"
            + " var seen = 0; v3.onChange(function () { seen++; });");
        run(ScriptType.CLIENT, "var k5 = Config.register('five', { n: 5 });");
        assertEquals(5, ConfigManager.names().size());
        // everyone reads everyone's
        assertEquals(1.0, number(ScriptType.CLIENT, "Config.get('one').n"));
        assertEquals(3.0, number(ScriptType.STARTUP, "Config.get('three').n"));
        assertEquals(5.0, number(ScriptType.SERVER, "Config.get('five').n"));
        assertEquals(4.0, number(ScriptType.CLIENT, "Config.get('four').n"));
        // the server scripts run again (a reload): the other types keep working references
        ScriptManager.of(ScriptType.SERVER).reload();
        run(ScriptType.CLIENT, "Config.get('three').n = 30;");
        assertEquals(java.util.List.of(), ScriptManager.of(ScriptType.CLIENT).errors());
        assertEquals(30.0, number(ScriptType.STARTUP, "Config.get('three').n"));
        assertEquals(1.0, number(ScriptType.STARTUP, "s1.n"));
        assertEquals(5.0, number(ScriptType.CLIENT, "k5.n"));
        run(ScriptType.CLIENT, "k5.n = 50;");
        assertEquals(50.0, number(ScriptType.STARTUP, "Config.get('five').n"));
        // the old callback of the reloaded type is gone, and registering again returns the same config
        ConfigManager.pollNow(false);
        assertEquals(0.0, number(ScriptType.SERVER, "typeof seen === 'undefined' ? 0 : seen"));
        run(ScriptType.SERVER, "var v3b = Config.register('three', { n: 3 }, { side: 'server' });");
        assertEquals(30.0, number(ScriptType.SERVER, "v3b.n"));
        assertEquals(5, ConfigManager.names().size());
    }

    @Test
    void registeringTheSameNameAgainUsesTheLatestEntriesAndLogsOneLine() throws IOException {
        run(ScriptType.STARTUP, "var d = Config.register('dup', { x: Config.number(1) });");
        int before = ConfigManager.notices().size();
        // the same entries again (a script that runs twice): no line
        run(ScriptType.SERVER, "var same = Config.register('dup', { x: Config.number(1) });");
        assertEquals(before, ConfigManager.notices().size());
        // different entries from another script: the latest are used, one plain line is logged, nothing throws
        run(ScriptType.SERVER, "var e = Config.register('dup', { x: Config.number(2), y: Config.bool(true) });");
        assertTrue(ConfigManager.notices().subList(before, ConfigManager.notices().size()).stream()
            .anyMatch(line -> line.contains("config 'dup' was registered again with different entries by server scripts")));
        assertEquals("true", text(ScriptType.STARTUP, "d.y"));
        // the value in the file wins over a new default
        assertEquals(1.0, number(ScriptType.SERVER, "e.x"));
        assertEquals(List_of(), ScriptManager.of(ScriptType.SERVER).errors());
        // another side: refused with a line, the existing config is returned
        before = ConfigManager.notices().size();
        run(ScriptType.SERVER, "var f = Config.register('dup', { z: 1 }, { side: 'server' });");
        assertEquals(before + 1, ConfigManager.notices().size());
        assertTrue(ConfigManager.notices().get(before).contains("already registered as a common config"));
        assertEquals("true", text(ScriptType.SERVER, "f.y"));
        assertTrue(Files.isRegularFile(root.resolve("config/dup.json5")));
        assertFalse(Files.exists(root.resolve("config/dup-server.json5")));
    }

    private static java.util.List<String> List_of() {
        return java.util.List.of();
    }

    @Test
    void aNameThatIsNotRegisteredYetIsNullAndHasSaysFalse() {
        run(ScriptType.SERVER, "var early = Config.get('later'); var hasEarly = Config.has('later');");
        assertEquals("null", text(ScriptType.SERVER, "early"));
        assertEquals("false", text(ScriptType.SERVER, "hasEarly"));
        assertEquals(0.0, number(ScriptType.SERVER, "Config.names().length"));
        run(ScriptType.STARTUP, "var l = Config.register('later', { n: 7 });");
        assertEquals("true", text(ScriptType.SERVER, "Config.has('later')"));
        assertEquals(7.0, number(ScriptType.SERVER, "Config.get('later').n"));
        // a variable that was null stays null: ask again
        assertEquals("null", text(ScriptType.SERVER, "early"));
        assertNull(ConfigManager.get("nothing"));
    }

    @Test
    void sidesOfTheCallerAreChecked() {
        run(ScriptType.CLIENT, "Config.register('s', { x: 1 }, { side: 'server' });");
        run(ScriptType.SERVER, "Config.register('c', { x: 1 }, { side: 'client' });");
        assertEquals(1, ScriptManager.of(ScriptType.CLIENT).errors().size());
        assertEquals(1, ScriptManager.of(ScriptType.SERVER).errors().size());
        assertTrue(ConfigManager.names().isEmpty());
        // a client script may register and read a common config, a startup script any side
        run(ScriptType.CLIENT, "Config.register('ok', { x: 1 });");
        run(ScriptType.STARTUP, "Config.register('st', { x: 1 }, { side: 'server' });");
        assertEquals(2, ConfigManager.names().size());
    }
}
