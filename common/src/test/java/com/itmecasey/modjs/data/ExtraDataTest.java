package com.itmecasey.modjs.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.persistent.PersistentBinding;
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

class ExtraDataTest {
    @TempDir
    Path root;

    private final GeneratedData data = new GeneratedData();

    @org.junit.jupiter.api.BeforeAll
    static void folderStyle() {
        // The 1.20.x modules run these tests against a game that still uses plural data folders.
        com.itmecasey.modjs.format.DataFolders.usePlural(Boolean.getBoolean("modjs.pluralFolders"));
    }

    @org.junit.jupiter.api.AfterAll
    static void resetFolderStyle() {
        com.itmecasey.modjs.format.DataFolders.usePlural(false);
    }

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        PersistentBinding.resetCache();
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
    }

    private void script(ScriptType type, String name, String source) throws IOException {
        Path file = root.resolve(type.directory).resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    private JsonObject json(String namespace, String path) {
        byte[] bytes = data.get(namespace, path);
        assertNotNull(bytes, "missing " + namespace + "/" + path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void lootTablesAreBuilt() throws IOException {
        script(ScriptType.SERVER, "loot.js", """
            ServerEvents.blockLootTables(e => {
              e.addSimpleBlock('minecraft:dirt', '2x minecraft:diamond');
              e.add('mymod:ore', t => t.addPool(p => p.rolls([1, 2]).addItem('minecraft:coal', [1, 3]).addItem('minecraft:stick', 1, 5).randomChance(0.5)));
              e.clear('minecraft:stone');
            });
            ServerEvents.entityLootTables(e => e.addSimple('minecraft:cow', 'minecraft:bone'));
            ServerEvents.genericLootTables(e => e.add('mymod:gameplay/fishing', t => t.addPool(p => p.addItem('minecraft:cod'))));
            """);
        DataBuilder.buildServerData(data);
        assertEquals(0, ScriptManager.of(ScriptType.SERVER).errors().size(), ScriptManager.of(ScriptType.SERVER).errors().toString());

        JsonObject dirt = json("minecraft", com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/dirt.json");
        assertEquals("minecraft:block", dirt.get("type").getAsString());
        JsonObject pool = dirt.getAsJsonArray("pools").get(0).getAsJsonObject();
        JsonObject entry = pool.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals("minecraft:diamond", entry.get("name").getAsString());
        assertEquals(2, entry.getAsJsonArray("functions").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("minecraft:survives_explosion", pool.getAsJsonArray("conditions").get(0).getAsJsonObject().get("condition").getAsString());

        JsonObject ore = json("mymod", com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/ore.json");
        JsonObject orePool = ore.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals("minecraft:uniform", orePool.getAsJsonObject("rolls").get("type").getAsString());
        assertEquals(2, orePool.getAsJsonArray("entries").size());
        assertEquals(5, orePool.getAsJsonArray("entries").get(1).getAsJsonObject().get("weight").getAsInt());
        assertEquals(0.5, orePool.getAsJsonArray("conditions").get(0).getAsJsonObject().get("chance").getAsDouble());

        assertEquals(0, json("minecraft", com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/stone.json").getAsJsonArray("pools").size());
        assertEquals("minecraft:entity", json("minecraft", com.itmecasey.modjs.format.DataFolders.lootTable() + "/entities/cow.json").get("type").getAsString());
        assertNotNull(data.get("mymod", com.itmecasey.modjs.format.DataFolders.lootTable() + "/gameplay/fishing.json"));
    }

    @Test
    void worldgenWritesFeaturesAndBiomeModifiers() throws IOException {
        script(ScriptType.SERVER, "gen.js", """
            WorldgenEvents.add(e => {
              e.addOre({ id: 'ruby_ore', block: 'modjs:ruby_ore', size: 6, count: 4, minY: -32, maxY: 16 });
              e.removeFeature({ id: 'no_coal', features: 'minecraft:ore_coal_upper' });
              e.addSpawn({ id: 'more_cows', entity: 'minecraft:cow', weight: 20 });
              e.removeSpawn({ id: 'no_zombies', entities: ['minecraft:zombie'] });
            });
            """);
        DataBuilder.buildServerData(data);
        assertEquals(0, ScriptManager.of(ScriptType.SERVER).errors().size(), ScriptManager.of(ScriptType.SERVER).errors().toString());

        JsonObject configured = json("modjs", "worldgen/configured_feature/ruby_ore.json");
        assertEquals("minecraft:ore", configured.get("type").getAsString());
        assertEquals(6, configured.getAsJsonObject("config").get("size").getAsInt());
        JsonObject target = configured.getAsJsonObject("config").getAsJsonArray("targets").get(0).getAsJsonObject();
        assertEquals("modjs:ruby_ore", target.getAsJsonObject("state").get("Name").getAsString());
        assertEquals("minecraft:tag_match", target.getAsJsonObject("target").get("predicate_type").getAsString());

        JsonObject placed = json("modjs", "worldgen/placed_feature/ruby_ore.json");
        assertEquals("modjs:ruby_ore", placed.get("feature").getAsString());
        assertEquals(4, placed.getAsJsonArray("placement").size());

        JsonObject add = json("modjs", "forge/biome_modifier/ruby_ore.json");
        assertEquals("forge:add_features", add.get("type").getAsString());
        assertEquals("#minecraft:is_overworld", add.get("biomes").getAsString());
        assertEquals("underground_ores", add.get("step").getAsString());

        assertEquals("forge:remove_features", json("modjs", "forge/biome_modifier/no_coal.json").get("type").getAsString());
        assertEquals(20, json("modjs", "forge/biome_modifier/more_cows.json").getAsJsonArray("spawners").get(0).getAsJsonObject().get("weight").getAsInt());
        assertTrue(json("modjs", "forge/biome_modifier/no_zombies.json").has("entity_types"));
    }

    @Test
    void worldgenRequiresAnId() throws IOException {
        script(ScriptType.SERVER, "gen.js", "WorldgenEvents.add(e => e.addOre({ block: 'modjs:x' }));");
        DataBuilder.buildServerData(data);
        assertEquals(1, ScriptManager.of(ScriptType.SERVER).errors().size());
    }

    @Test
    void persistentDataSurvivesAReload() throws IOException {
        script(ScriptType.STARTUP, "p.js", """
            Persistent.set('count', 3);
            Persistent.set('name', 'ruby');
            Persistent.set('flags', { a: true, list: [1, 2] });
            Persistent.store('other').set('x', 1);
            """);
        ScriptManager.of(ScriptType.STARTUP).reload();
        assertTrue(ScriptManager.of(ScriptType.STARTUP).errors().isEmpty(), ScriptManager.of(ScriptType.STARTUP).errors().toString());
        assertTrue(Files.isRegularFile(root.resolve("persistent/main.json")));
        assertTrue(Files.isRegularFile(root.resolve("persistent/other.json")));

        PersistentBinding.resetCache();
        script(ScriptType.SERVER, "check.js", """
            ServerEvents.recipes(e => {
              if (Persistent.get('count') !== 3) throw new Error('count was ' + Persistent.get('count'));
              if (Persistent.get('name') !== 'ruby') throw new Error('name');
              if (Persistent.get('flags').list[1] !== 2) throw new Error('flags');
              if (Persistent.get('missing', 'fallback') !== 'fallback') throw new Error('fallback');
              if (!Persistent.has('count')) throw new Error('has');
              Persistent.remove('count');
              if (Persistent.has('count')) throw new Error('remove');
            });
            """);
        DataBuilder.buildServerData(data);
        assertEquals(0, ScriptManager.of(ScriptType.SERVER).errors().size(), ScriptManager.of(ScriptType.SERVER).errors().toString());
    }

    @Test
    void jsonIoReadsAndWritesInsideTheRootOnly() throws IOException {
        script(ScriptType.STARTUP, "io.js", """
            JsonIO.write('config/mine.json', { a: 1, list: [true, 'x'] });
            const back = JsonIO.read('config/mine.json');
            if (back.a !== 1 || back.list[1] !== 'x') throw new Error('round trip failed');
            if (JsonIO.read('config/missing.json') !== null) throw new Error('missing should be null');
            if (!JsonIO.exists('config/mine.json')) throw new Error('exists');
            """);
        script(ScriptType.CLIENT, "escape.js", "JsonIO.write('../escape.json', {});");
        ScriptManager.of(ScriptType.STARTUP).reload();
        ScriptManager.of(ScriptType.CLIENT).reload();

        assertTrue(ScriptManager.of(ScriptType.STARTUP).errors().isEmpty(), ScriptManager.of(ScriptType.STARTUP).errors().toString());
        assertTrue(Files.isRegularFile(root.resolve("config/mine.json")));
        assertEquals(1, ScriptManager.of(ScriptType.CLIENT).errors().size());
        assertTrue(!Files.exists(root.resolveSibling("escape.json")));
    }

    @Test
    void tagRemovalRewritesTheMergedTagFromGameJars() throws IOException {
        script(ScriptType.SERVER, "t.js", """
            ServerEvents.tags('item', event => {
              event.remove('minecraft:planks', ['minecraft:oak_planks']);
              event.add('minecraft:planks', 'mymod:plank');
            });
            """);
        DataBuilder.buildServerData(data);
        assertEquals(0, ScriptManager.of(ScriptType.SERVER).errors().size(), ScriptManager.of(ScriptType.SERVER).errors().toString());

        JsonObject planks = json("minecraft", com.itmecasey.modjs.format.DataFolders.tags("item") + "/planks.json");
        assertTrue(planks.get("replace").getAsBoolean());
        String values = planks.getAsJsonArray("values").toString();
        assertTrue(!values.contains("minecraft:oak_planks"), values);
        assertTrue(values.contains("minecraft:birch_planks"), values);
        assertTrue(values.contains("mymod:plank"), values);
    }

    @Test
    void lootTablesShippedInJarsCanBeModified() throws IOException {
        script(ScriptType.SERVER, "l.js", """
            ServerEvents.blockLootTables(e => {
              e.modify('minecraft:diamond_ore', t => t.replaceItem('minecraft:diamond', 'minecraft:emerald'));
              e.modify('minecraft:coal_ore', t => t.removeItem('minecraft:coal').addPool(p => p.addItem('minecraft:stick')));
            });
            ServerEvents.genericLootTables(e => e.modify('minecraft:missing/nothing_here', t => t.clearPools()));
            """);
        DataBuilder.buildServerData(data);

        String diamond = json("minecraft", com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/diamond_ore.json").toString();
        assertTrue(diamond.contains("minecraft:emerald"), diamond);
        assertTrue(!diamond.contains("\"minecraft:diamond\""), diamond);
        String coal = json("minecraft", com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/coal_ore.json").toString();
        assertTrue(!coal.contains("\"minecraft:coal\""), coal);
        assertTrue(coal.contains("minecraft:stick"), coal);
        assertEquals(1, ScriptManager.of(ScriptType.SERVER).errors().size(), "only the missing table should error");
    }
}
