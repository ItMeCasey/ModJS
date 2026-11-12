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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamespaceTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private void run(String source) throws IOException {
        Path file = root.resolve(ScriptType.STARTUP.directory).resolve("ns.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        ScriptManager.of(ScriptType.STARTUP).reload();
        RegistryManager.collect();
    }

    private String errors() {
        return ScriptManager.of(ScriptType.STARTUP).errors().toString();
    }

    private JsonObject json(GeneratedData data, String namespace, String path) {
        byte[] bytes = data.get(namespace, path);
        assertNotNull(bytes, "missing " + namespace + "/" + path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void parseSplitsAndValidates() {
        assertEquals("modjs:ruby", RegistryNames.parse("ruby").full());
        assertEquals("mypack:ruby", RegistryNames.parse("mypack:ruby").full());
        assertEquals("mypack:gems/ruby", RegistryNames.parse("gems/ruby", "mypack").full());
        assertEquals("my.pack-2_x", RegistryNames.parse("my.pack-2_x:a").namespace());
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse("minecraft:ruby"));
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse("MyPack:ruby"));
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse("my pack:ruby"));
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse("a:b:c"));
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse(":ruby"));
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse("mypack:"));
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse("mypack:Ruby"));
        assertThrows(IllegalArgumentException.class, () -> RegistryNames.parse(""));
    }

    @Test
    void aNamespaceOptionAndAPlainIdWorkTogether() throws IOException {
        run("""
            StartupEvents.registry('item', event => {
              event.create('ruby');
              event.create('mypack:ruby').displayName('Pack Ruby');
              event.namespace('other').create('gem');
              event.create('modjs:explicit');
            });
            """);
        assertEquals("", errors().replace("[]", ""));
        assertEquals(java.util.List.of("modjs:ruby", "mypack:ruby", "other:gem", "modjs:explicit"),
            RegistryManager.items().stream().map(ItemBuilderJS::fullId).toList());
        ItemBuilderJS packRuby = RegistryManager.items().stream().filter(i -> i.fullId().equals("mypack:ruby")).findFirst().orElseThrow();
        assertEquals("mypack", packRuby.namespace());
        assertEquals("ruby", packRuby.id());
        assertEquals("mypack:item/ruby", packRuby.texture());
        assertEquals("modjs:item/ruby", RegistryManager.items().iterator().next().texture());
    }

    @Test
    void duplicatesAreCheckedPerFullIdAndBadNamespacesAreRefused() throws IOException {
        run("""
            StartupEvents.registry('item', event => {
              event.create('ruby');
              event.create('mypack:ruby');
              try { event.create('mypack:ruby'); } catch (e) { console.log('DUP ' + e.message); }
              try { event.create('minecraft:ruby'); } catch (e) { console.log('MC ' + e.message); }
              try { event.create('Bad:ruby'); } catch (e) { console.log('BAD ' + e.message); }
              try { event.namespace('minecraft'); } catch (e) { console.log('NS ' + e.message); }
              try { event.namespace('UPPER'); } catch (e) { console.log('NS2 ' + e.message); }
            });
            """);
        assertEquals(2, RegistryManager.items().size());
    }

    @Test
    void itemAndBlockWithTheSameFullIdKeepTheBlockItem() throws IOException {
        run("""
            StartupEvents.registry('block', event => event.create('mypack:thing'));
            StartupEvents.registry('item', event => { event.create('mypack:thing'); event.create('mypack:other'); });
            """);
        assertEquals(1, RegistryManager.items().size());
        assertEquals("mypack:other", RegistryManager.items().iterator().next().fullId());
        assertTrue(errors().contains("mypack:thing"), errors());
    }

    @Test
    void generatedAssetsAndDataLandInTheNamespace() throws IOException {
        run("""
            StartupEvents.registry('item', event => event.create('mypack:ruby').displayName('Pack Ruby'));
            StartupEvents.registry('item', event => event.create('plain').displayName('Plain'));
            StartupEvents.registry('block', event => {
              event.create('mypack:ruby_block').displayName('Ruby Block').hardness(2).requiresTool(true).unify('c:storage_blocks/ruby');
              event.create('mypack:ruby_slab').type('slab');
              event.create('mypack:turner').model('orientable').textures({ top: 'mypack:block/t', side: 'mypack:block/s', front: 'mypack:block/f' });
            });
            StartupEvents.registry('creative_tab', event => event.create('mypack:main').displayName('Main').icon('mypack:ruby'));
            StartupEvents.registry('fluid', event => event.create('mypack:goo').displayName('Goo'));
            StartupEvents.registry('armor_material', event => event.create('mypack:ruby').defense(1, 2, 3, 4).repair('mypack:ruby'));
            StartupEvents.registry('tool_tier', event => event.create('mypack:ruby').uses(10).repair('mypack:ruby'));
            """);
        assertEquals("", errors().replace("[]", ""));
        GeneratedData assets = new GeneratedData();
        DataBuilder.buildClientAssets(assets);

        // models, blockstates, lang per namespace
        JsonObject itemModel = json(assets, "mypack", "models/item/ruby.json");
        assertEquals("mypack:item/ruby", itemModel.getAsJsonObject("textures").get("layer0").getAsString());
        assertNull(assets.get("modjs", "models/item/ruby.json"));
        assertNotNull(assets.get("modjs", "models/item/plain.json"));
        assertEquals("mypack:block/ruby_block", json(assets, "mypack", "blockstates/ruby_block.json").getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        assertTrue(assets.has("mypack", "models/item/ruby_block.json") || assets.has("mypack", "items/ruby_block.json"));
        assertFalse(assets.has("modjs", "models/item/ruby_block.json") || assets.has("modjs", "items/ruby_block.json"));
        assertEquals("mypack:block/ruby_slab", json(assets, "mypack", "blockstates/ruby_slab.json").getAsJsonObject("variants").getAsJsonObject("type=bottom").get("model").getAsString());
        assertNotNull(assets.get("mypack", "models/block/ruby_slab_top.json"));
        assertNotNull(assets.get("mypack", "models/block/ruby_slab_double.json"));
        assertEquals("mypack:block/turner", json(assets, "mypack", "blockstates/turner.json").getAsJsonObject("variants").getAsJsonObject("facing=east").get("model").getAsString());
        JsonObject packLang = json(assets, "mypack", "lang/en_us.json");
        assertEquals("Pack Ruby", packLang.get("item.mypack.ruby").getAsString());
        assertEquals("Ruby Block", packLang.get("block.mypack.ruby_block").getAsString());
        assertEquals("Ruby Block", packLang.get("item.mypack.ruby_block").getAsString());
        assertEquals("Main", packLang.get("itemGroup.mypack.main").getAsString());
        assertEquals("Goo", packLang.get("block.mypack.goo").getAsString());
        assertEquals("Goo", packLang.get("fluid_type.mypack.goo").getAsString());
        assertFalse(packLang.has("item.modjs.plain"));
        assertEquals("Plain", json(assets, "modjs", "lang/en_us.json").get("item.modjs.plain").getAsString());
        assertFalse(json(assets, "modjs", "lang/en_us.json").has("item.mypack.ruby"));

        // data pack: loot table, tags, repair tags
        GeneratedData data = new GeneratedData();
        DataBuilder.buildServerData(data);
        String lootFolder = com.itmecasey.modjs.format.DataFolders.lootTable();
        JsonObject loot = json(data, "mypack", lootFolder + "/blocks/ruby_block.json");
        String lootText = loot.toString();
        assertTrue(lootText.contains("\"mypack:ruby_block\""), lootText);
        assertNull(data.get("modjs", lootFolder + "/blocks/ruby_block.json"));
        JsonObject tag = json(data, "c", com.itmecasey.modjs.format.DataFolders.tags("item") + "/storage_blocks/ruby.json");
        assertTrue(tag.getAsJsonArray("values").toString().contains("mypack:ruby_block"));
        JsonObject armorRepair = json(data, "mypack", com.itmecasey.modjs.format.DataFolders.tags("item") + "/repairs_armor_ruby.json");
        assertTrue(armorRepair.getAsJsonArray("values").toString().contains("mypack:ruby"));
        assertNotNull(data.get("mypack", com.itmecasey.modjs.format.DataFolders.tags("item") + "/repairs_tool_ruby.json"));
        JsonObject pickaxe = json(data, "minecraft", com.itmecasey.modjs.format.DataFolders.tags("block") + "/mineable/pickaxe.json");
        assertTrue(pickaxe.getAsJsonArray("values").toString().contains("mypack:ruby_block"));
    }

    @Test
    void scriptBlocksAreFoundByFullId() throws IOException {
        run("""
            StartupEvents.registry('block', event => {
              event.create('mypack:machine').inventory({ slots: 2 });
              event.create('machine').inventory({ slots: 3 });
            });
            """);
        assertEquals(2, com.itmecasey.modjs.block.ScriptBlockLogic.definition("mypack:machine").slotCount());
        assertEquals(3, com.itmecasey.modjs.block.ScriptBlockLogic.definition("modjs:machine").slotCount());
        assertEquals(3, com.itmecasey.modjs.block.ScriptBlockLogic.definition("machine").slotCount());
        assertNull(com.itmecasey.modjs.block.ScriptBlockLogic.definition("other:machine"));
    }
}
