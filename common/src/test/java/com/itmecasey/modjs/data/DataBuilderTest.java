package com.itmecasey.modjs.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.recipe.RecipeChanges;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.itmecasey.modjs.tags.TagChanges;
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

class DataBuilderTest {
    @TempDir
    Path root;

    private final GeneratedData data = new GeneratedData();

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

    private JsonObject json(String namespace, String path) {
        byte[] bytes = data.get(namespace, path);
        assertNotNull(bytes, "missing " + namespace + "/" + path);
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void shapedRecipeUsesThe26_1Format() throws IOException {
        script(ScriptType.SERVER, "r.js", """
            ServerEvents.recipes(event => {
              event.shaped('3x minecraft:diamond_block', ['AB', ' B'], { A: 'minecraft:stick', B: '#c:gems/diamond' });
            });
            """);
        DataBuilder.buildServerData(data);

        JsonObject recipe = json("modjs", "recipe/crafting_shaped/minecraft/diamond_block.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals("minecraft:diamond_block", recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(3, recipe.getAsJsonObject("result").get("count").getAsInt());
        assertEquals("minecraft:stick", recipe.getAsJsonObject("key").get("A").getAsString());
        assertEquals("#c:gems/diamond", recipe.getAsJsonObject("key").get("B").getAsString());
        assertEquals(2, recipe.getAsJsonArray("pattern").size());
    }

    @Test
    void shapelessCookingAndCustomIds() throws IOException {
        script(ScriptType.SERVER, "r.js", """
            ServerEvents.recipes(event => {
              event.shapeless(Item.of('minecraft:stone', 2), ['minecraft:dirt', ['minecraft:gravel', 'minecraft:sand']]);
              event.smelting('minecraft:glass', 'minecraft:sand').xp(0.5).cookingTime(100).id('mypack:glass_fast');
              event.blasting('minecraft:iron_ingot', '#c:ores/iron');
              event.stonecutting('2x minecraft:stone_slab', 'minecraft:stone');
              event.smithing('minecraft:netherite_axe', 'minecraft:netherite_upgrade_smithing_template', 'minecraft:diamond_axe', '#minecraft:netherite_tool_materials');
              event.custom({ type: 'minecraft:smelting', ingredient: 'minecraft:coal', result: { id: 'minecraft:diamond' } });
            });
            """);
        DataBuilder.buildServerData(data);

        JsonObject shapeless = json("modjs", "recipe/crafting_shapeless/minecraft/stone.json");
        assertEquals(2, shapeless.getAsJsonArray("ingredients").size());
        assertTrue(shapeless.getAsJsonArray("ingredients").get(1).isJsonArray());

        JsonObject glass = json("mypack", "recipe/glass_fast.json");
        assertEquals(0.5, glass.get("experience").getAsDouble());
        assertEquals(100, glass.get("cookingtime").getAsInt());

        assertEquals(100, json("modjs", "recipe/blasting/minecraft/iron_ingot.json").get("cookingtime").getAsInt());
        assertNotNull(data.get("modjs", "recipe/stonecutting/minecraft/stone_slab.json"));
        assertNotNull(data.get("modjs", "recipe/smithing_transform/minecraft/netherite_axe.json"));
        assertNotNull(data.get("modjs", "recipe/smelting/minecraft/diamond.json"));
    }

    @Test
    void duplicateAutoIdsGetSuffixes() throws IOException {
        script(ScriptType.SERVER, "r.js", """
            ServerEvents.recipes(event => {
              event.smelting('minecraft:glass', 'minecraft:sand');
              event.smelting('minecraft:glass', 'minecraft:red_sand');
            });
            """);
        DataBuilder.buildServerData(data);

        assertNotNull(data.get("modjs", "recipe/smelting/minecraft/glass.json"));
        assertNotNull(data.get("modjs", "recipe/smelting/minecraft/glass_1.json"));
    }

    @Test
    void tagsAreWrittenPerRegistry() throws IOException {
        script(ScriptType.SERVER, "t.js", """
            ServerEvents.tags('item', event => {
              event.add('c:ingots/copper', ['mymod:copper_ingot', '#c:ingots/tin']);
              event.removeAll('minecraft:planks');
              event.add('minecraft:planks', 'mymod:plank');
            });
            ServerEvents.tags('block', event => event.add('mymod:fancy', 'minecraft:stone'));
            """);
        DataBuilder.buildServerData(data);

        JsonObject copper = json("c", "tags/item/ingots/copper.json");
        assertEquals(2, copper.getAsJsonArray("values").size());
        assertEquals(false, copper.get("replace").getAsBoolean());
        JsonObject planks = json("minecraft", "tags/item/planks.json");
        assertEquals(true, planks.get("replace").getAsBoolean());
        assertNotNull(data.get("mymod", "tags/block/fancy.json"));
    }

    @Test
    void rawDataEventsWriteFiles() throws IOException {
        script(ScriptType.SERVER, "d.js", """
            ServerEvents.generateData(event => event.addJson('mymod:loot_table/x', { type: 'minecraft:block', pools: [] }));
            ServerEvents.highPriorityData(event => event.addText('mymod:notes.txt', 'hello'));
            """);
        DataBuilder.buildServerData(data);

        assertEquals("minecraft:block", json("mymod", "loot_table/x.json").get("type").getAsString());
        assertEquals("hello", new String(data.get("mymod", "notes.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void removalAndReplacementFiltersWork() throws IOException {
        script(ScriptType.SERVER, "m.js", """
            ServerEvents.recipes(event => {
              event.remove({ output: 'minecraft:torch' });
              event.remove({ id: 'minecraft:stick' });
              event.replaceInput({ mod: 'minecraft' }, 'minecraft:iron_ingot', 'mymod:steel_ingot');
              event.replaceOutput({ type: 'minecraft:smelting' }, 'minecraft:glass', 'mymod:fancy_glass');
            });
            """);
        DataBuilder.buildServerData(data);
        RecipeChanges changes = RecipeChanges.current();

        JsonObject torch = JsonParser.parseString("{\"type\":\"minecraft:crafting_shaped\",\"result\":{\"id\":\"minecraft:torch\",\"count\":4}}").getAsJsonObject();
        assertNull(changes.apply("minecraft:torch", torch));
        assertNull(changes.apply("minecraft:stick", JsonParser.parseString("{\"type\":\"x\"}").getAsJsonObject()));

        JsonObject pickaxe = JsonParser.parseString("""
            {"type":"minecraft:crafting_shaped","key":{"X":"minecraft:iron_ingot","S":["minecraft:stick"]},"result":{"id":"minecraft:iron_pickaxe"}}
            """).getAsJsonObject();
        JsonObject rewritten = changes.apply("minecraft:iron_pickaxe", pickaxe);
        assertEquals("mymod:steel_ingot", rewritten.getAsJsonObject("key").get("X").getAsString());
        assertEquals("minecraft:iron_ingot", pickaxe.getAsJsonObject("key").get("X").getAsString(), "original untouched");

        JsonObject glass = JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"ingredient\":\"minecraft:sand\",\"result\":{\"id\":\"minecraft:glass\"}}").getAsJsonObject();
        assertEquals("mymod:fancy_glass", changes.apply("minecraft:glass", glass).getAsJsonObject("result").get("id").getAsString());
        assertEquals(2, changes.removedCount());
        assertEquals(2, changes.replacedCount());
    }

    @Test
    void tagRemovalsAreRecorded() throws IOException {
        script(ScriptType.SERVER, "t.js", "ServerEvents.tags('item', e => e.remove('c:gems', ['minecraft:emerald']));");
        DataBuilder.buildServerData(data);

        assertTrue(TagChanges.current().removalsFor("item", "c:gems").contains("minecraft:emerald"));
    }

    @Test
    void langEventsWriteAssets() throws IOException {
        script(ScriptType.CLIENT, "l.js", "ClientEvents.lang('en_us', e => e.add('item.mymod.thing', 'Thing'));");
        DataBuilder.buildClientAssets(data);

        assertEquals("Thing", json("modjs", "lang/en_us.json").get("item.mymod.thing").getAsString());
    }

    @Test
    void badRecipeArgumentsBecomeScriptErrors() throws IOException {
        script(ScriptType.SERVER, "bad.js", "ServerEvents.recipes(e => e.shaped('minecraft:stone', ['A'], 'not an object'));");
        DataBuilder.buildServerData(data);

        assertEquals(1, ScriptManager.of(ScriptType.SERVER).errors().size());
    }

    @Test
    void replaceInputByIdFilterRewritesOnlyThatRecipe() throws IOException {
        script(ScriptType.SERVER, "m.js", """
            ServerEvents.recipes(event => {
              event.replaceInput({ id: 'minecraft:iron_pickaxe' }, 'minecraft:stick', 'minecraft:blaze_rod');
            });
            """);
        DataBuilder.buildServerData(data);
        RecipeChanges changes = RecipeChanges.current();

        JsonObject pickaxe = JsonParser.parseString("""
            {"type":"minecraft:crafting_shaped","key":{"#":"minecraft:stick","X":"#minecraft:iron_tool_materials"},"result":{"id":"minecraft:iron_pickaxe"}}
            """).getAsJsonObject();
        JsonObject rewritten = changes.apply("minecraft:iron_pickaxe", pickaxe);
        assertEquals("minecraft:blaze_rod", rewritten.getAsJsonObject("key").get("#").getAsString());
        assertEquals("#minecraft:iron_tool_materials", rewritten.getAsJsonObject("key").get("X").getAsString());
        assertTrue(changes.apply("minecraft:iron_axe", pickaxe) == pickaxe, "other recipes untouched");
    }
}
