package com.itmecasey.modjs.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradesTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        TradeOps.clear();
        ScriptManager.of(ScriptType.SERVER).reload();
        ScriptManager.of(ScriptType.STARTUP).reload();
    }

    @AfterEach
    void tearDown() {
        TradeOps.clear();
    }

    private static Object eval(String source) {
        return ScriptManager.of(ScriptType.SERVER).eval(source, "villagers-test.js");
    }

    private static JsonObject json(GeneratedData data, String namespace, String path) {
        byte[] bytes = data.get(namespace, path);
        assertNotNull(bytes, namespace + "/" + path + " was not written");
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    // -- parsing --------------------------------------------------------------------------------------------------------------

    @Test
    void aTradeTakesCostsResultLimitsAndConditions() {
        eval("Villagers.addTrade('minecraft:librarian', 2, { give: { item: 'minecraft:diamond_sword', count: 1, components: { 'minecraft:damage': 3 } },"
            + " want: ['5x minecraft:emerald', 'minecraft:book'], maxUses: 4, xp: 7, priceMultiplier: 0.2, demand: 3,"
            + " villagerType: ['minecraft:desert', 'swamp'], biome: 'minecraft:plains' });");
        assertEquals(1, TradeOps.snapshot().size());
        TradeOps.Op op = TradeOps.snapshot().get(0);
        assertEquals(TradeOps.Kind.ADD, op.kind());
        assertEquals("minecraft:librarian", op.profession());
        assertEquals(2, op.level());
        TradeSpec t = op.trades().get(0);
        assertEquals("minecraft:diamond_sword", t.give.id());
        assertEquals(3, t.give.components().get("minecraft:damage").getAsInt());
        assertEquals(5, t.want.count());
        assertEquals("minecraft:book", t.want2.id());
        assertEquals(4, t.maxUses);
        assertEquals(7, t.xp);
        assertEquals(0.2f, t.priceMultiplier);
        assertEquals(3, t.demand);
        assertEquals(List.of("minecraft:desert", "minecraft:swamp"), t.villagerTypes);
        assertEquals(List.of("minecraft:plains"), t.biomes);
    }

    @Test
    void defaultsAndItemShorthands() {
        eval("Villagers.addTrade('farmer', 1, { give: Item.of('minecraft:bread', 2), want: 'minecraft:wheat' });");
        TradeSpec t = TradeOps.snapshot().get(0).trades().get(0);
        assertEquals("minecraft:farmer", TradeOps.snapshot().get(0).profession());
        assertEquals(2, t.give.count());
        assertEquals(12, t.maxUses);
        assertEquals(1, t.xp);
        assertEquals(0.05f, t.priceMultiplier);
        assertNull(t.want2);
    }

    @Test
    void mistakesAreScriptErrorsThatNameTheField() {
        eval("Villagers.addTrade('minecraft:librarian', 9, { give: 'minecraft:diamond', want: 'minecraft:emerald' });");
        eval("Villagers.addTrade('minecraft:librarian', 1, { want: 'minecraft:emerald' });");
        eval("Villagers.addTrade('minecraft:librarian', 1, { give: 'minecraft:diamond' });");
        eval("Villagers.addTrade('minecraft:librarian', 1, { give: 'minecraft:diamond', want: ['a:b', 'c:d', 'e:f'] });");
        eval("Villagers.addTrade('minecraft:librarian', 1, { give: 'minecraft:diamond', want: 'minecraft:emerald', maxUses: 0 });");
        eval("Villagers.addTrade('minecraft:librarian', 1, { give: '1000x minecraft:diamond', want: 'minecraft:emerald' });");
        eval("Villagers.addTrade('Not A Profession', 1, { give: 'minecraft:diamond', want: 'minecraft:emerald' });");
        eval("Villagers.addTrade('minecraft:librarian', 1, { give: 'minecraft:diamond', want: 'minecraft:emerald', when: 5 });");
        eval("Villagers.removeTrades('minecraft:librarian', { level: 'x' });");
        eval("Villagers.replaceTrades('minecraft:librarian', 1, 'nope');");
        eval("Villagers.addTrade('minecraft:wandering_trader', 4, { give: 'minecraft:diamond', want: 'minecraft:emerald' });");
        List<String> errors = ScriptManager.of(ScriptType.SERVER).errors();
        assertEquals(11, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("the level of minecraft:librarian is 1 to 5"));
        assertTrue(errors.get(1).contains("a trade needs 'give'"));
        assertTrue(errors.get(2).contains("a trade needs 'want'"));
        assertTrue(errors.get(3).contains("'want' takes one or two costs"));
        assertTrue(errors.get(4).contains("'maxUses' must be 1 to 9999"));
        assertTrue(errors.get(5).contains("'give'") && errors.get(5).contains("1 to 99"));
        assertTrue(errors.get(6).contains("is not a profession id"));
        assertTrue(errors.get(7).contains("'when' must be a function"));
        assertTrue(errors.get(8).contains("'level' must be a number"));
        assertTrue(errors.get(9).contains("a list of trades is needed"));
        assertTrue(errors.get(10).contains("1 to 3"));
        assertTrue(TradeOps.snapshot().isEmpty());
    }

    @Test
    void operationsOfAScriptTypeAreDroppedWhenItsScriptsRunAgain() {
        eval("Villagers.addTrade('minecraft:farmer', 1, { give: 'minecraft:bread', want: 'minecraft:wheat' });");
        ScriptManager.of(ScriptType.STARTUP).eval("Villagers.clearTrades('minecraft:cleric');", "startup-test.js");
        assertEquals(2, TradeOps.snapshot().size());
        ScriptManager.of(ScriptType.SERVER).reload();
        assertEquals(1, TradeOps.snapshot().size());
        assertEquals(TradeOps.Kind.CLEAR, TradeOps.snapshot().get(0).kind());
    }

    @Test
    void clientScriptsCannotChangeTrades() {
        ScriptManager.of(ScriptType.CLIENT).reload();
        ScriptManager.of(ScriptType.CLIENT).eval("Villagers.clearTrades('minecraft:cleric');", "client-test.js");
        // the global is not installed in client scripts at all
        assertFalse(ScriptManager.of(ScriptType.CLIENT).errors().isEmpty());
        assertTrue(TradeOps.snapshot().isEmpty());
    }

    // -- 26.x data ------------------------------------------------------------------------------------------------------------

    private static List<String> original(String profession, int level) {
        if (profession.equals("minecraft:farmer") && level == 1) {
            return List.of("minecraft:farmer/1/wheat_emerald", "minecraft:farmer/1/potato_emerald", "minecraft:farmer/1/carrot_emerald");
        }
        if (profession.equals("minecraft:farmer") && level == 2) {
            return List.of("minecraft:farmer/2/pumpkin_emerald");
        }
        if (profession.equals("minecraft:wandering_trader") && level == 1) {
            return List.of("minecraft:wandering_trader/emerald_cactus", "#minecraft:something");
        }
        return List.of();
    }

    private static List<String> values(JsonObject tag) {
        JsonArray array = tag.getAsJsonArray("values");
        return array.asList().stream().map(e -> e.getAsString()).toList();
    }

    @Test
    void addingATradeWritesTheTradeFileAndAReplacingTagThatKeepsTheOriginalEntries() {
        eval("Villagers.addTrade('minecraft:farmer', 1, { give: { item: 'minecraft:bread', count: 3 }, want: ['2x minecraft:wheat', 'minecraft:emerald'],"
            + " maxUses: 5, xp: 4, priceMultiplier: 0.1, villagerType: 'minecraft:plains', biome: ['minecraft:plains'] });");
        GeneratedData data = new GeneratedData();
        TradeData.writeTo(data, TradeOps.snapshot(), TradesTest::original);
        JsonObject tag = json(data, "minecraft", "tags/villager_trade/farmer/level_1.json");
        assertTrue(tag.get("replace").getAsBoolean());
        assertEquals(List.of("minecraft:farmer/1/wheat_emerald", "minecraft:farmer/1/potato_emerald", "minecraft:farmer/1/carrot_emerald", "modjs:villagers/minecraft/farmer/level_1/1"), values(tag));
        JsonObject trade = json(data, "modjs", "villager_trade/villagers/minecraft/farmer/level_1/1.json");
        assertEquals("minecraft:bread", trade.getAsJsonObject("gives").get("id").getAsString());
        assertEquals(3, trade.getAsJsonObject("gives").get("count").getAsInt());
        assertEquals("minecraft:wheat", trade.getAsJsonObject("wants").get("id").getAsString());
        assertEquals(2, trade.getAsJsonObject("wants").get("count").getAsInt());
        assertEquals("minecraft:emerald", trade.getAsJsonObject("additional_wants").get("id").getAsString());
        assertEquals(5, trade.get("max_uses").getAsInt());
        assertEquals(4, trade.get("xp").getAsInt());
        assertEquals(0.1f, trade.get("reputation_discount").getAsFloat());
        JsonObject predicate = trade.getAsJsonObject("merchant_predicate");
        assertEquals("minecraft:all_of", predicate.get("type").getAsString());
        assertEquals(2, predicate.getAsJsonArray("terms").size());
        // an untouched level writes nothing
        assertFalse(data.has("minecraft", "tags/villager_trade/farmer/level_2.json"));
    }

    @Test
    void replacingATierUsesOnlyTheNewTradesAndClearingEmptiesEveryLevel() {
        eval("Villagers.replaceTrades('minecraft:farmer', 1, [{ give: 'minecraft:bread', want: 'minecraft:wheat' }, { give: 'minecraft:cake', want: 'minecraft:emerald' }]);"
            + " Villagers.clearTrades('minecraft:cleric');");
        GeneratedData data = new GeneratedData();
        TradeData.writeTo(data, TradeOps.snapshot(), TradesTest::original);
        assertEquals(List.of("modjs:villagers/minecraft/farmer/level_1/1", "modjs:villagers/minecraft/farmer/level_1/2"),
            values(json(data, "minecraft", "tags/villager_trade/farmer/level_1.json")));
        for (int level = 1; level <= 5; level++) {
            JsonObject tag = json(data, "minecraft", "tags/villager_trade/cleric/level_" + level + ".json");
            assertTrue(tag.get("replace").getAsBoolean());
            assertEquals(List.of(), values(tag));
        }
    }

    @Test
    void removingByObjectOrFunctionDropsMatchingTradesOfOneOrAllLevels() {
        // the original pools are described by trade files the test cannot read from jars, so the removal sees none of them: only generated trades
        eval("Villagers.addTrade('minecraft:farmer', 1, { give: 'minecraft:bread', want: 'minecraft:wheat' });"
            + " Villagers.addTrade('minecraft:farmer', 2, { give: 'minecraft:cake', want: 'minecraft:emerald' });"
            + " Villagers.removeTrades('minecraft:farmer', { give: 'minecraft:bread' });"
            + " Villagers.removeTrades('minecraft:farmer', 2, trade => trade.want[0].id === 'minecraft:emerald');");
        GeneratedData data = new GeneratedData();
        TradeData.writeTo(data, TradeOps.snapshot(), TradesTest::original);
        assertEquals(List.of("minecraft:farmer/1/wheat_emerald", "minecraft:farmer/1/potato_emerald", "minecraft:farmer/1/carrot_emerald"),
            values(json(data, "minecraft", "tags/villager_trade/farmer/level_1.json")));
        assertEquals(List.of("minecraft:farmer/2/pumpkin_emerald"), values(json(data, "minecraft", "tags/villager_trade/farmer/level_2.json")));
    }

    @Test
    void theWanderingTraderUsesItsOwnPoolNames() {
        eval("Villagers.addTrade('minecraft:wandering_trader', 1, { give: 'minecraft:cactus', want: 'minecraft:emerald' });"
            + " Villagers.addTrade('minecraft:wandering_trader', 3, { give: 'minecraft:emerald', want: 'minecraft:cactus' });");
        GeneratedData data = new GeneratedData();
        TradeData.writeTo(data, TradeOps.snapshot(), TradesTest::original);
        assertEquals(List.of("minecraft:wandering_trader/emerald_cactus", "#minecraft:something", "modjs:villagers/minecraft/wandering_trader/common/1"),
            values(json(data, "minecraft", "tags/villager_trade/wandering_trader/common.json")));
        assertEquals(List.of("modjs:villagers/minecraft/wandering_trader/buying/1"), values(json(data, "minecraft", "tags/villager_trade/wandering_trader/buying.json")));
    }

    @Test
    void otherNamespacesGetTheirOwnTagFolder() {
        eval("Villagers.addTrade('mypack:smith', 2, { give: 'minecraft:iron_ingot', want: 'minecraft:emerald' });");
        GeneratedData data = new GeneratedData();
        TradeData.writeTo(data, TradeOps.snapshot(), TradesTest::original);
        assertEquals(List.of("modjs:villagers/mypack/smith/level_2/1"), values(json(data, "mypack", "tags/villager_trade/smith/level_2.json")));
    }

    @Test
    void theFilterReadsTradeFilesFromTheClassPath() {
        JsonObject trade = new JsonObject();
        JsonObject gives = new JsonObject();
        gives.addProperty("id", "minecraft:emerald");
        trade.add("gives", gives);
        Map<String, Object> view = TradeData.view(trade, "minecraft:farmer", 1, "minecraft:farmer/1/x");
        assertEquals("minecraft:emerald", ((Map<?, ?>) view.get("give")).get("id"));
        assertEquals(List.of(), view.get("want"));
        assertTrue(TradeFilter.parse(null, ScriptType.SERVER).matches(view));
        assertThrows(IllegalArgumentException.class, () -> TradeFilter.parse(ScriptManager.of(ScriptType.SERVER).eval("({ give: 5 })", "f.js"), ScriptType.SERVER));
    }
}
