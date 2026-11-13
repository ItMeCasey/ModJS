package com.itmecasey.modjs.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.itmecasey.modjs.villager.TradeOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionsTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        TradeOps.clear();
        RegistryManager.forgetProfessions();
        EventGroups.clear(ScriptType.STARTUP);
        ScriptManager.of(ScriptType.STARTUP).reload();
    }

    @AfterEach
    void tearDown() {
        TradeOps.clear();
        EventGroups.clear(ScriptType.STARTUP);
    }

    private static Object startup(String source) {
        return ScriptManager.of(ScriptType.STARTUP).eval(source, "professions-test.js");
    }

    private static ProfessionBuilderJS find(String id) {
        return RegistryManager.professions().stream().filter(p -> p.fullId().equals(id)).findFirst().orElse(null);
    }

    @Test
    void aProfessionComesFromTheRegistryEventOrFromVillagersProfession() {
        startup("StartupEvents.registry('villager_profession', event => {"
            + " event.create('mypack:smith').jobSite('minecraft:smithing_table', 'minecraft:anvil').secondarySite('minecraft:grindstone').requests('minecraft:iron_ingot')"
            + ".workSound('minecraft:entity.villager.work_toolsmith').name('Smith').texture('minecraft:toolsmith')"
            + ".trade(1, { give: 'minecraft:iron_axe', want: '3x minecraft:emerald' }).trades(2, [{ give: 'minecraft:anvil', want: '8x minecraft:emerald' }]); });"
            + " Villagers.profession('mypack:baker', { jobSite: 'minecraft:smoker', requests: ['minecraft:wheat'], name: 'Baker', trades: { 1: [{ give: 'minecraft:bread', want: 'minecraft:wheat' }] } });");
        RegistryManager.collect();
        assertEquals(List.of(), ScriptManager.of(ScriptType.STARTUP).errors());
        ProfessionBuilderJS smith = find("mypack:smith");
        assertNotNull(smith);
        assertEquals(List.of("minecraft:smithing_table", "minecraft:anvil"), smith.jobSites());
        assertEquals(List.of("minecraft:grindstone"), smith.secondarySites());
        assertEquals(List.of("minecraft:iron_ingot"), smith.requestedItems());
        assertEquals("minecraft:entity.villager.work_toolsmith", smith.workSoundId());
        assertEquals("Smith", smith.displayName());
        assertEquals("minecraft:toolsmith", smith.textureBase());
        assertEquals(2, smith.tradesByLevel().size());
        ProfessionBuilderJS baker = find("mypack:baker");
        assertNotNull(baker);
        assertEquals("Baker", baker.displayName());
        assertEquals(1, baker.tradesByLevel().get(1).size());
        // the trades of professions become requests that scripts running again do not drop
        assertEquals(3, TradeOps.snapshot().size());
        ScriptManager.of(ScriptType.STARTUP).reload();
        assertEquals(3, TradeOps.snapshot().size());
    }

    @Test
    void aPlainIdGoesToModjsAndTheDefaultNameIsTheCapitalizedId() {
        startup("Villagers.profession('glass_blower', { jobSite: 'minecraft:glass' });");
        RegistryManager.collect();
        ProfessionBuilderJS p = find("modjs:glass_blower");
        assertNotNull(p);
        assertEquals("Glass Blower", p.displayName());
        assertEquals("minecraft:nitwit", p.textureBase());
    }

    @Test
    void mistakesAreScriptErrors() {
        startup("Villagers.profession('Not Valid', { jobSite: 'minecraft:anvil' });");
        startup("Villagers.profession('minecraft:thing', { jobSite: 'minecraft:anvil' });");
        startup("Villagers.profession('mypack:a', { jobSite: 'not a block' });");
        startup("Villagers.profession('mypack:b', { jobSite: 'minecraft:anvil', trades: { 1: [{ give: 'minecraft:bread' }] } });");
        startup("Villagers.profession('mypack:c', { jobSite: 'minecraft:anvil' }); Villagers.profession('mypack:c', { jobSite: 'minecraft:anvil' });");
        startup("StartupEvents.registry('villager_profession', event => { event.create('mypack:d'); });");
        RegistryManager.collect();
        List<String> errors = ScriptManager.of(ScriptType.STARTUP).errors();
        assertTrue(errors.stream().anyMatch(e -> e.contains("Not Valid")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("reserved")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("'not a block' is not a block id")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("a trade needs 'want'")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Duplicate villager profession id 'mypack:c'")), errors.toString());
        assertTrue(errors.stream().anyMatch(e -> e.contains("villager profession mypack:d needs a job site")), errors.toString());
        assertEquals(1, RegistryManager.professions().stream().filter(p -> p.fullId().equals("mypack:c")).count());
        assertTrue(RegistryManager.professions().stream().noneMatch(p -> p.fullId().equals("mypack:d")));
    }

    @Test
    void professionsAreStartupOnly() {
        ScriptManager.of(ScriptType.SERVER).reload();
        ScriptManager.of(ScriptType.SERVER).eval("Villagers.profession('mypack:late', { jobSite: 'minecraft:anvil' });", "server-test.js");
        assertTrue(ScriptManager.of(ScriptType.SERVER).errors().stream().anyMatch(e -> e.contains("professions are registered from startup scripts")));
    }

    @Test
    void theNameAndTheSkinAreWrittenAsAssetsAndAnExistingTextureIsKept() {
        startup("Villagers.profession('mypack:smith', { jobSite: 'minecraft:anvil', name: 'Master Smith', texture: 'minecraft:not_a_skin' });"
            + " Villagers.profession('mypack:own', { jobSite: 'minecraft:anvil', name: 'Own' });");
        RegistryManager.collect();
        GeneratedData data = new GeneratedData();
        data.putBytes("mypack", "textures/entity/villager/profession/own.png", new byte[] {1, 2, 3});
        RegistryManager.writeAssets(data);
        JsonObject lang = JsonParser.parseString(new String(data.get("mypack", "lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Master Smith", lang.get("entity.mypack.villager.smith").getAsString());
        assertEquals("Master Smith", lang.get("entity.minecraft.villager.smith").getAsString());
        assertEquals("Master Smith", lang.get("entity.minecraft.villager.mypack.smith").getAsString());
        assertEquals("Own", lang.get("entity.mypack.villager.own").getAsString());
        assertEquals(3, data.get("mypack", "textures/entity/villager/profession/own.png").length); // the pack's own texture stays
        assertFalse(data.has("mypack", "textures/entity/villager/profession/smith.png")); // an unknown base skin is only logged
    }

    @Test
    void every26xProfessionGetsATradeSetPerLevelAndAnEmptyTagWhereNothingFillsIt() {
        startup("Villagers.profession('mypack:smith', { jobSite: 'minecraft:anvil', trades: { 2: [{ give: 'minecraft:anvil', want: '8x minecraft:emerald' }] } });");
        RegistryManager.collect();
        GeneratedData data = new GeneratedData();
        com.itmecasey.modjs.villager.TradeData.writeProfessions(data, RegistryManager.professions());
        for (int level = 1; level <= 5; level++) {
            JsonObject set = JsonParser.parseString(new String(data.get("mypack", "trade_set/smith/level_" + level + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("#mypack:smith/level_" + level, set.get("trades").getAsString());
            assertEquals(2, set.get("amount").getAsInt());
            assertTrue(data.has("mypack", "tags/villager_trade/smith/level_" + level + ".json"));
        }
    }
}
