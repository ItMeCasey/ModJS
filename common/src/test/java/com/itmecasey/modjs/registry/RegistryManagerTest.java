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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryManagerTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private void script(ScriptType type, String name, String source) throws IOException {
        Path file = root.resolve(type.directory).resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private JsonObject json(GeneratedData data, String namespace, String path) {
        byte[] bytes = data.get(namespace, path);
        assertNotNull(bytes, "missing " + namespace + "/" + path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void itemsAndBlocksProduceAssetsAndData() throws IOException {
        script(ScriptType.STARTUP, "content.js", """
            StartupEvents.registry('item', event => {
              event.create('ruby').displayName('Fancy Ruby').maxStackSize(16).food(4, 0.6).alwaysEdible().tab('ingredients');
              event.create('plain_thing');
            });
            StartupEvents.registry('block', event => {
              event.create('ruby_block').hardness(5).resistance(10).lightLevel(7).requiresTool(true).texture('mymod:block/ruby');
              event.create('ghost').noItem().dropsNothing();
            });
            """);
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();

        assertEquals(ScriptManager.of(ScriptType.STARTUP).errors().size(), 0, ScriptManager.of(ScriptType.STARTUP).errors().toString());
        assertEquals(2, RegistryManager.items().size());
        assertEquals(2, RegistryManager.blocks().size());
        assertEquals(16, RegistryManager.items().iterator().next().maxStackSize());

        GeneratedData assets = new GeneratedData();
        DataBuilder.buildClientAssets(assets);
        JsonObject lang = json(assets, "modjs", "lang/en_us.json");
        assertEquals("Fancy Ruby", lang.get("item.modjs.ruby").getAsString());
        assertEquals("Plain Thing", lang.get("item.modjs.plain_thing").getAsString());
        assertEquals("Ruby Block", lang.get("block.modjs.ruby_block").getAsString());
        assertTrue(!lang.has("item.modjs.ghost"));

        assertEquals("modjs:item/ruby", json(assets, "modjs", "items/ruby.json").getAsJsonObject("model").get("model").getAsString());
        assertEquals("modjs:item/ruby", json(assets, "modjs", "models/item/ruby.json").getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals("mymod:block/ruby", json(assets, "modjs", "models/block/ruby_block.json").getAsJsonObject("textures").get("all").getAsString());
        assertNotNull(assets.get("modjs", "blockstates/ruby_block.json"));

        GeneratedData data = new GeneratedData();
        DataBuilder.buildServerData(data);
        JsonObject loot = json(data, "modjs", "loot_table/blocks/ruby_block.json");
        assertEquals(1, loot.getAsJsonArray("pools").size());
        assertEquals(0, json(data, "modjs", "loot_table/blocks/ghost.json").getAsJsonArray("pools").size());
    }

    @Test
    void duplicateAndInvalidIdsAreScriptErrors() throws IOException {
        script(ScriptType.STARTUP, "bad.js", """
            StartupEvents.registry('item', event => {
              event.create('a');
              event.create('a');
            });
            StartupEvents.registry('item', event => event.create('Bad Id'));
            """);
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();

        assertEquals(1, RegistryManager.items().size());
        assertEquals(2, ScriptManager.of(ScriptType.STARTUP).errors().size());
    }

    @Test
    void clientLangEventsMergeWithGeneratedLang() throws IOException {
        script(ScriptType.STARTUP, "c.js", "StartupEvents.registry('item', e => e.create('gem'));");
        script(ScriptType.CLIENT, "l.js", "ClientEvents.lang('en_us', e => e.add('item.modjs.gem', 'Shiny Gem'));");
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();

        GeneratedData assets = new GeneratedData();
        DataBuilder.buildClientAssets(assets);
        assertEquals("Shiny Gem", json(assets, "modjs", "lang/en_us.json").get("item.modjs.gem").getAsString());
    }

    @Test
    void assetAndDataFoldersAreImported() throws IOException {
        Path texture = root.resolve("assets/mymod/textures/item/thing.png");
        Files.createDirectories(texture.getParent());
        Files.write(texture, new byte[] {1, 2, 3});
        Path recipe = root.resolve("data/mymod/recipe/x.json");
        Files.createDirectories(recipe.getParent());
        Files.writeString(recipe, "{}");

        GeneratedData assets = new GeneratedData();
        DataBuilder.buildClientAssets(assets);
        assertEquals(3, assets.get("mymod", "textures/item/thing.png").length);

        GeneratedData data = new GeneratedData();
        DataBuilder.buildServerData(data);
        assertNotNull(data.get("mymod", "recipe/x.json"));
    }
}
