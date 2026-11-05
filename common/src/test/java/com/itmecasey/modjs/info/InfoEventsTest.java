package com.itmecasey.modjs.info;

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

class InfoEventsTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private void load(ScriptType type, String source) throws IOException {
        Path file = root.resolve(type.directory + "/info.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        ScriptManager.of(type).reload();
        assertTrue(ScriptManager.of(type).errors().isEmpty(), ScriptManager.of(type).errors().toString());
    }

    private static InfoEventJS block(String id, String side) {
        return new InfoEventJS("block", id, side, false, 1, 2, 3);
    }

    @Test
    void linesBarsAndIconsAreCollected() throws IOException {
        load(ScriptType.STARTUP, """
            InfoEvents.block('mymod:lamp', e => {
              e.add('Plain');
              e.add('Gold', { color: '#ffaa00', position: 'tail' });
              e.bar('Power', 1.7, 0x336699);
              e.item('coal', 'Fuel', 8);
              e.hideTag('jade:energy_storage');
            });
            """);
        InfoEventJS event = InfoDispatch.onServer(block("mymod:lamp", "server"));
        assertEquals(4, event.lines().size());
        assertEquals("Plain", event.lines().get(0).text().toString());
        assertEquals(0xFFAA00, event.lines().get(1).textColor());
        assertEquals("tail", event.lines().get(1).position());
        assertEquals(1.0F, event.lines().get(2).fraction());
        assertEquals(0x336699, event.lines().get(2).barColor());
        assertEquals("minecraft:coal", event.lines().get(3).item());
        assertEquals(8, event.lines().get(3).count());
        assertTrue(event.hiddenTags().contains("jade:energy_storage"));
    }

    @Test
    void handlerForAnotherIdDoesNotRunButGeneralOneDoes() throws IOException {
        load(ScriptType.STARTUP, """
            InfoEvents.block('mymod:other', e => e.add('other'));
            InfoEvents.block(e => e.add('any ' + e.id));
            """);
        InfoEventJS event = InfoDispatch.onServer(block("mymod:lamp", "server"));
        assertEquals(1, event.lines().size());
        assertEquals("any mymod:lamp", event.lines().get(0).text().toString());
    }

    @Test
    void clientScriptsRunOnlyOnTheClientWhenTheServerCovers() throws IOException {
        load(ScriptType.CLIENT, "InfoEvents.block('mymod:lamp', e => e.add('client'));");
        load(ScriptType.SERVER, "InfoEvents.block('mymod:lamp', e => e.add('server'));");
        assertEquals("server", InfoDispatch.onServer(block("mymod:lamp", "server")).lines().get(0).text().toString());
        InfoEventJS covered = InfoDispatch.onClient(block("mymod:lamp", "client"), true);
        assertEquals(1, covered.lines().size());
        assertEquals("client", covered.lines().get(0).text().toString());
        InfoEventJS plain = InfoDispatch.onClient(block("mymod:lamp", "client"), false);
        assertEquals(2, plain.lines().size());
    }

    @Test
    void activeReflectsRegistrations() throws IOException {
        assertFalse(InfoDispatch.active("block"));
        load(ScriptType.STARTUP, "InfoEvents.entity('minecraft:cow', e => e.add('moo'));");
        assertTrue(InfoDispatch.active("entity"));
        assertFalse(InfoDispatch.active("block"));
    }

    @Test
    void badColorFallsBack() {
        assertEquals(7, InfoEventJS.colorOf("nonsense", 7));
        assertEquals(0x00FF00, InfoEventJS.colorOf("0x00ff00", 7));
    }
}
