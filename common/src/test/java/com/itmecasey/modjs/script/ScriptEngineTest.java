package com.itmecasey.modjs.script;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptEngineTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private void write(ScriptType type, String name, String source) throws IOException {
        Path file = root.resolve(type.directory).resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    public static final class TestEvent extends EventJS {
        public int value;

        public int getValue() {
            return value;
        }

        public void add(int amount) {
            value += amount;
        }
    }

    @Test
    void runsScriptsAndHandlersInOrder() throws IOException {
        write(ScriptType.SERVER, "a.js", "ServerEvents.recipes(e => e.add(1));");
        write(ScriptType.SERVER, "sub/b.js", "ServerEvents.recipes(function (e) { e.add(10); });");
        ScriptManager manager = ScriptManager.of(ScriptType.SERVER);
        manager.reload();

        assertEquals(2, manager.loadedCount());
        assertTrue(manager.errors().isEmpty(), manager.errors().toString());

        TestEvent event = new TestEvent();
        EventGroups.SERVER.post("recipes", event);
        assertEquals(11, event.getValue());
    }

    @Test
    void idFilteredHandlersOnlyRunForTheirId() throws IOException {
        write(ScriptType.SERVER, "tags.js",
            "ServerEvents.tags('item', e => e.add(1)); ServerEvents.tags('block', e => e.add(100));");
        ScriptManager.of(ScriptType.SERVER).reload();

        TestEvent event = new TestEvent();
        EventGroups.SERVER.post("tags", "item", event);
        assertEquals(1, event.getValue());
    }

    @Test
    void reloadDropsOldHandlers() throws IOException {
        write(ScriptType.SERVER, "a.js", "ServerEvents.recipes(e => e.add(1));");
        ScriptManager manager = ScriptManager.of(ScriptType.SERVER);
        manager.reload();
        manager.reload();

        TestEvent event = new TestEvent();
        EventGroups.SERVER.post("recipes", event);
        assertEquals(1, event.getValue());
    }

    @Test
    void cancelIsReported() throws IOException {
        write(ScriptType.SERVER, "c.js", "BlockEvents.broken(e => e.cancel());");
        ScriptManager.of(ScriptType.SERVER).reload();

        assertTrue(EventGroups.BLOCK.post("broken", new TestEvent()));
        assertFalse(EventGroups.BLOCK.post("placed", new TestEvent()));
    }

    @Test
    void scriptErrorsAreCollectedNotThrown() throws IOException {
        write(ScriptType.SERVER, "bad.js", "this is not javascript (");
        write(ScriptType.SERVER, "good.js", "ServerEvents.recipes(e => e.add(5));");
        ScriptManager manager = ScriptManager.of(ScriptType.SERVER);
        manager.reload();

        assertEquals(1, manager.errors().size());
        assertTrue(manager.errors().get(0).contains("bad.js"));
        TestEvent event = new TestEvent();
        EventGroups.SERVER.post("recipes", event);
        assertEquals(5, event.getValue());
    }

    @Test
    void handlerErrorsAreCollected() throws IOException {
        write(ScriptType.SERVER, "boom.js", "ServerEvents.recipes(e => { throw new Error('boom'); });");
        ScriptManager manager = ScriptManager.of(ScriptType.SERVER);
        manager.reload();
        EventGroups.SERVER.post("recipes", new TestEvent());

        assertEquals(1, manager.errors().size());
        assertTrue(manager.errors().get(0).contains("boom"));
    }

    @Test
    void eventsRejectedInWrongScriptType() throws IOException {
        write(ScriptType.CLIENT, "x.js", "ServerEvents.recipes(e => e.add(1));");
        ScriptManager manager = ScriptManager.of(ScriptType.CLIENT);
        manager.reload();

        assertEquals(1, manager.errors().size());
        TestEvent event = new TestEvent();
        EventGroups.SERVER.post("recipes", event);
        assertEquals(0, event.getValue());
    }

    @Test
    void sandboxBlocksDangerousClasses() throws IOException {
        write(ScriptType.SERVER, "evil.js", "Java.loadClass('java.lang.Runtime');");
        write(ScriptType.SERVER, "evil2.js", "Java.loadClass('java.io.File');");
        write(ScriptType.SERVER, "ok.js", "Java.loadClass('java.util.ArrayList');");
        ScriptManager manager = ScriptManager.of(ScriptType.SERVER);
        manager.reload();

        assertEquals(2, manager.errors().size(), manager.errors().toString());
        assertEquals(1, manager.loadedCount());
    }

    @Test
    void consoleAndGlobalWork() throws IOException {
        write(ScriptType.STARTUP, "g.js", "global.answer = 42; console.log('hello', 1);");
        write(ScriptType.SERVER, "h.js", "ServerEvents.recipes(e => e.add(global.answer));");
        ScriptManager.of(ScriptType.STARTUP).reload();
        ScriptManager.of(ScriptType.SERVER).reload();

        TestEvent event = new TestEvent();
        EventGroups.SERVER.post("recipes", event);
        assertEquals(42, event.getValue());
    }
}
