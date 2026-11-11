package com.itmecasey.modjs.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.data.DataBuilder;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptBlockModelsTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private GeneratedData build(String source) throws IOException {
        Path file = root.resolve(ScriptType.STARTUP.directory).resolve("blocks.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();
        assertEquals(0, ScriptManager.of(ScriptType.STARTUP).errors().size(), ScriptManager.of(ScriptType.STARTUP).errors().toString());
        GeneratedData assets = new GeneratedData();
        DataBuilder.buildClientAssets(assets);
        return assets;
    }

    private JsonObject json(GeneratedData data, String path) {
        byte[] bytes = data.get("modjs", path);
        assertNotNull(bytes, "missing modjs/" + path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private BlockBuilderJS block(String id) {
        return RegistryManager.blocks().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void faceTexturesPickTheMostSpecificKey() throws IOException {
        GeneratedData assets = build("""
            StartupEvents.registry('block', event => {
              event.create('all_six').textures({ top: 'm:block/t', bottom: 'm:block/b', north: 'm:block/n', south: 'm:block/s', east: 'm:block/e', west: 'm:block/w' });
              event.create('sided').textures({ top: 'm:block/t', side: 'm:block/side' }).texture('m:block/fallback');
              event.create('single').texture('m:block/one');
            });
            """);
        JsonObject six = json(assets, "models/block/all_six.json");
        assertEquals("minecraft:block/cube", six.get("parent").getAsString());
        JsonObject textures = six.getAsJsonObject("textures");
        assertEquals("m:block/t", textures.get("up").getAsString());
        assertEquals("m:block/b", textures.get("down").getAsString());
        assertEquals("m:block/n", textures.get("north").getAsString());
        assertEquals("m:block/w", textures.get("west").getAsString());

        // top plus side without a bottom: the bottom falls back to the plain texture
        JsonObject sided = json(assets, "models/block/sided.json");
        assertEquals("minecraft:block/cube", sided.get("parent").getAsString());
        assertEquals("m:block/side", sided.getAsJsonObject("textures").get("east").getAsString());
        assertEquals("m:block/fallback", sided.getAsJsonObject("textures").get("down").getAsString());

        JsonObject single = json(assets, "models/block/single.json");
        assertEquals("minecraft:block/cube_all", single.get("parent").getAsString());
        assertEquals("m:block/one", single.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void modelOptionsChooseTheParent() throws IOException {
        GeneratedData assets = build("""
            StartupEvents.registry('block', event => {
              event.create('column').textures({ end: 'm:block/end', side: 'm:block/side' });
              event.create('bottomtop').textures({ top: 'm:block/t', bottom: 'm:block/b', side: 'm:block/s' });
              event.create('turning').textures({ top: 'm:block/t', bottom: 'm:block/b', side: 'm:block/s', front: 'm:block/f' }).model('orientable');
              event.create('custom').model('modjs:block/custom_model').texture('m:block/p');
            });
            """);
        assertEquals("minecraft:block/cube_column", json(assets, "models/block/column.json").get("parent").getAsString());
        assertEquals("m:block/end", json(assets, "models/block/column.json").getAsJsonObject("textures").get("end").getAsString());
        assertEquals("minecraft:block/cube_bottom_top", json(assets, "models/block/bottomtop.json").get("parent").getAsString());

        JsonObject turning = json(assets, "models/block/turning.json");
        assertEquals("minecraft:block/orientable_with_bottom", turning.get("parent").getAsString());
        assertEquals("m:block/f", turning.getAsJsonObject("textures").get("front").getAsString());
        JsonObject variants = json(assets, "blockstates/turning.json").getAsJsonObject("variants");
        assertEquals(4, variants.size());
        assertFalse(variants.getAsJsonObject("facing=north").has("y"));
        assertEquals(90, variants.getAsJsonObject("facing=east").get("y").getAsInt());
        assertEquals(180, variants.getAsJsonObject("facing=south").get("y").getAsInt());
        assertEquals(270, variants.getAsJsonObject("facing=west").get("y").getAsInt());
        assertTrue(block("turning").isOrientable());

        // a script-supplied model is only referenced, no model file is generated for it
        assertEquals("modjs:block/custom_model", json(assets, "blockstates/custom.json").getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertNull(assets.get("modjs", "models/block/custom.json"));
    }

    @Test
    void inventoryDefinesSlotsValuesAndCallbacks() throws IOException {
        build("""
            StartupEvents.registry('block', event => {
              event.create('machine').inventory({ slots: [{ x: 56, y: 17 }, { x: 116, y: 35 }], stackLimit: 16, values: ['progress', 'max'] })
                .onTick(5, be => {}).onButton((id, be, player) => {}).screen(s => { s.size(180, 170); s.bar({ value: 'progress', max: 'max', x: 80, y: 35, width: 24, height: 6, color: 0x3fdc5a }); s.button('Go', { id: 'go', x: 10, y: 20 }); });
              event.create('row').inventory({ slots: 5 });
              event.create('plain');
              event.create('marker').entity();
            });
            """);
        BlockBuilderJS machine = block("machine");
        assertTrue(machine.hasEntity());
        assertTrue(machine.hasScriptInventory());
        assertEquals(2, machine.slotCount());
        assertEquals(56, machine.slotPositions().get(0)[0]);
        assertEquals(35, machine.slotPositions().get(1)[1]);
        assertEquals(16, machine.stackLimit());
        assertEquals(java.util.List.of("progress", "max"), machine.valueNames());
        assertEquals(5, machine.tickInterval());
        assertNotNull(machine.tickFunction());
        assertNotNull(machine.buttonFunction());
        com.itmecasey.modjs.client.TitleScreenConfig screen = machine.screenConfig();
        assertEquals(180, screen.panelWidth());
        assertEquals(1, screen.bars().size());
        assertEquals("progress", screen.bars().get(0).value());
        assertEquals("go", screen.buttons().get(0).id());

        assertEquals(5, block("row").slotCount());
        assertEquals(44, block("row").slotPositions().get(0)[0]);
        assertEquals(44 + 4 * 18, block("row").slotPositions().get(4)[0]);
        assertFalse(block("plain").hasEntity());
        assertTrue(block("marker").hasEntity());
        assertFalse(block("marker").hasScriptInventory());
    }

    @Test
    void badOptionsAreRefused() throws IOException {
        Path file = root.resolve(ScriptType.STARTUP.directory).resolve("bad.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            StartupEvents.registry('block', event => {
              event.create('a').textures({ roof: 'x:y' });
              event.create('b').model('sphere');
              event.create('c').inventory({ slots: 99 });
            });
            """);
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();
        String errors = ScriptManager.of(ScriptType.STARTUP).errors().toString();
        assertTrue(errors.contains("Unknown texture face 'roof'") || errors.contains("roof"), errors);
    }
}
