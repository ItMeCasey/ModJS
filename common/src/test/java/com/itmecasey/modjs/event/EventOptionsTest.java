package com.itmecasey.modjs.event;

import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventOptionsTest {
    @TempDir
    Path root;

    public static final class Counter extends EventJS {
        public final StringBuilder order = new StringBuilder();

        public void mark(String text) {
            order.append(text);
        }
    }

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private void load(String source) throws IOException {
        Path file = root.resolve("server_scripts/s.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        ScriptManager.of(ScriptType.SERVER).reload();
        assertTrue(ScriptManager.of(ScriptType.SERVER).errors().isEmpty(), ScriptManager.of(ScriptType.SERVER).errors().toString());
    }

    @Test
    void priorityRunsHigherFirst() throws IOException {
        load("""
            ServerEvents.recipes(e => e.mark('b'));
            ServerEvents.recipes.priority(10, e => e.mark('a'));
            ServerEvents.recipes.priority(-5, e => e.mark('c'));
            """);
        Counter counter = new Counter();
        EventGroups.SERVER.post("recipes", counter);
        assertEquals("abc", counter.order.toString());
    }

    @Test
    void onceRunsOnlyOnce() throws IOException {
        load("""
            ServerEvents.recipes.once(e => e.mark('o'));
            ServerEvents.recipes(e => e.mark('x'));
            """);
        Counter first = new Counter();
        EventGroups.SERVER.post("recipes", first);
        Counter second = new Counter();
        EventGroups.SERVER.post("recipes", second);
        assertEquals("ox", first.order.toString());
        assertEquals("x", second.order.toString());
    }

    @Test
    void offRemovesAHandler() throws IOException {
        load("""
            const noisy = e => e.mark('n');
            ServerEvents.recipes(noisy);
            ServerEvents.recipes(e => e.mark('k'));
            ServerEvents.recipes.off(noisy);
            """);
        Counter counter = new Counter();
        EventGroups.SERVER.post("recipes", counter);
        assertEquals("k", counter.order.toString());
    }

    @Test
    void idFilteredVariantsSupportOptionsToo() throws IOException {
        load("""
            ServerEvents.tags.once('item', e => e.mark('i'));
            ServerEvents.tags.priority(3, 'item', e => e.mark('p'));
            """);
        Counter counter = new Counter();
        EventGroups.SERVER.post("tags", "item", counter);
        assertEquals("pi", counter.order.toString());
    }
}
