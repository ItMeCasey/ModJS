package com.itmecasey.modjs.format;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.data.DataBuilder;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.recipe.RecipeChanges;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.21.1 recipe format: ingredients are {@code {"item": ..}} / {@code {"tag": ..}} objects. */
class LegacyFormatTest {
    @TempDir
    Path root;

    private final GeneratedData data = new GeneratedData();

    @BeforeEach
    void setUp() {
        VersionFormat.use(new LegacyFormat());
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    @AfterEach
    void tearDown() {
        VersionFormat.use(new ModernFormat());
    }

    private void script(String source) throws IOException {
        Path file = root.resolve("server_scripts/s.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private JsonObject json(String namespace, String path) {
        byte[] bytes = data.get(namespace, path);
        assertNotNull(bytes, "missing " + namespace + "/" + path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void recipesUseIngredientObjects() throws IOException {
        script("""
            ServerEvents.recipes(event => {
              event.shaped('3x minecraft:diamond_block', ['AB'], { A: 'minecraft:stick', B: '#c:gems/diamond' });
              event.shapeless('minecraft:stone', [['minecraft:dirt', 'minecraft:gravel'], 'minecraft:sand']);
              event.smelting('minecraft:glass', 'minecraft:sand');
            });
            """);
        DataBuilder.buildServerData(data);
        assertEquals(0, ScriptManager.of(ScriptType.SERVER).errors().size(), ScriptManager.of(ScriptType.SERVER).errors().toString());

        JsonObject shaped = json("modjs", "recipe/crafting_shaped/minecraft/diamond_block.json");
        assertEquals("minecraft:stick", shaped.getAsJsonObject("key").getAsJsonObject("A").get("item").getAsString());
        assertEquals("c:gems/diamond", shaped.getAsJsonObject("key").getAsJsonObject("B").get("tag").getAsString());
        assertEquals(3, shaped.getAsJsonObject("result").get("count").getAsInt());

        JsonObject shapeless = json("modjs", "recipe/crafting_shapeless/minecraft/stone.json");
        assertTrue(shapeless.getAsJsonArray("ingredients").get(0).isJsonArray());
        assertEquals("minecraft:sand", shapeless.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString());

        assertEquals("minecraft:sand", json("modjs", "recipe/smelting/minecraft/glass.json").getAsJsonObject("ingredient").get("item").getAsString());
    }

    @Test
    void filtersAndReplacementsUnderstandIngredientObjects() throws IOException {
        script("""
            ServerEvents.recipes(event => {
              event.remove({ input: 'minecraft:flint' });
              event.replaceInput({ output: 'minecraft:iron_pickaxe' }, '#c:rods/wooden', 'mymod:handle');
              event.replaceInput({ output: 'minecraft:iron_pickaxe' }, 'minecraft:iron_ingot', '#c:ingots/steel');
            });
            """);
        DataBuilder.buildServerData(data);
        RecipeChanges changes = RecipeChanges.current();

        JsonObject arrow = JsonParser.parseString("""
            {"type":"minecraft:crafting_shaped","key":{"A":{"item":"minecraft:flint"},"B":{"item":"minecraft:stick"}},"result":{"id":"minecraft:arrow"}}
            """).getAsJsonObject();
        assertNull(changes.apply("minecraft:arrow", arrow));

        JsonObject pickaxe = JsonParser.parseString("""
            {"type":"minecraft:crafting_shaped","key":{"#":{"tag":"c:rods/wooden"},"X":[{"item":"minecraft:iron_ingot"}]},"result":{"id":"minecraft:iron_pickaxe"}}
            """).getAsJsonObject();
        JsonObject rewritten = changes.apply("minecraft:iron_pickaxe", pickaxe);
        assertEquals("mymod:handle", rewritten.getAsJsonObject("key").getAsJsonObject("#").get("item").getAsString());
        assertEquals("c:ingots/steel", rewritten.getAsJsonObject("key").getAsJsonArray("X").get(0).getAsJsonObject().get("tag").getAsString());
    }

    @Test
    void blocksGetItemModelsInsteadOfItemDefinitions() throws IOException {
        Path startup = root.resolve("startup_scripts/c.js");
        Files.createDirectories(startup.getParent());
        Files.writeString(startup, "StartupEvents.registry('block', e => e.create('gem_block'));");
        ScriptManager.of(ScriptType.STARTUP).reload();
        com.itmecasey.modjs.registry.RegistryManager.collect();

        GeneratedData assets = new GeneratedData();
        DataBuilder.buildClientAssets(assets);
        assertNotNull(assets.get("modjs", "models/item/gem_block.json"));
        assertNull(assets.get("modjs", "items/gem_block.json"));
    }
}
