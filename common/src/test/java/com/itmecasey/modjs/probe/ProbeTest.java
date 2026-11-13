package com.itmecasey.modjs.probe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProbeTest {
    private static DumpData sample() {
        DumpData data = new DumpData();
        data.minecraftVersion = "1.21.1";
        data.loader = "neoforge";
        data.mods.add(new DumpData.Mod("minecraft", "Minecraft", "1.21.1"));
        data.mods.add(new DumpData.Mod("modjs", "ModJS", "2026.11.37"));
        data.registries.put("item", List.of("minecraft:apple", "minecraft:stone", "weird:it's"));
        data.registries.put("recipe_type", List.of("minecraft:crafting_shaped"));
        data.registries.put("mob_effect", List.of("minecraft:speed"));
        data.registries.put("worldgen/biome", List.of("minecraft:plains"));
        data.tags.put("item", Map.of("minecraft:logs", List.of("minecraft:oak_log"), "c:gems", List.of("minecraft:diamond")));
        data.blockStates.put("minecraft:oak_stairs", Map.of("facing", List.of("north", "south"), "waterlogged", List.of("false", "true")));
        data.blockStates.put("minecraft:wheat", Map.of("age", List.of("0", "1", "2")));
        return data;
    }

    @Test
    void quoteEscapesBackslashQuoteAndLineBreaks() {
        assertEquals("'a\\\\b'", Typings.quote("a\\b"));
        assertEquals("'it\\'s'", Typings.quote("it's"));
        assertEquals("'a\\nb\\r'", Typings.quote("a\nb\r"));
        assertEquals("'\\u2028\\u0001'", Typings.quote(" \u0001"));
        assertEquals("'minecraft:stone'", Typings.quote("minecraft:stone"));
    }

    @Test
    void typeNamesAreValidIdentifiers() {
        assertEquals("ItemId", Typings.idType("item"));
        assertEquals("WorldgenBiomeId", Typings.idType("worldgen/biome"));
        assertEquals("OthermodBigThingId", Typings.idType("othermod:big_thing"));
        assertEquals("_2dId", Typings.idType("2d"));
        assertEquals("ItemTagId", Typings.tagType("item"));
    }

    @Test
    void idsFileHasUnionsLooseFormsAliasesAndEscapedIds() {
        String ids = Typings.ids(sample());
        assertTrue(ids.startsWith(Typings.HEADER));
        assertTrue(ids.contains("type ItemId =\n  | 'minecraft:apple'\n  | 'minecraft:stone'\n  | 'weird:it\\'s';"));
        assertTrue(ids.contains("type ItemIdLike = ItemId | (string & {});"));
        assertTrue(ids.contains("type ModId =\n  | 'minecraft'\n  | 'modjs';"));
        assertTrue(ids.contains("type EffectId = MobEffectId;"));
        assertTrue(ids.contains("type BiomeId = WorldgenBiomeId;"));
        assertTrue(ids.contains("'worldgen/biome': WorldgenBiomeId;"));
    }

    @Test
    void emptyRegistryBecomesNever() {
        DumpData data = new DumpData();
        data.registries.put("fluid", List.of());
        assertTrue(Typings.ids(data).contains("type FluidId = never;"));
    }

    @Test
    void tagsFileListsTagsWithoutHashAndDefinesHashedReferences() {
        String tags = Typings.tags(sample());
        assertTrue(tags.contains("type ItemTagId =\n  | 'c:gems'\n  | 'minecraft:logs';"));
        assertTrue(tags.contains("type ItemTagRef = `#${ItemTagId}`;"));
        assertTrue(tags.contains("type BlockTagRef = never;"));
        assertTrue(tags.contains("type AnyTagIdLike = ItemTagId | (string & {});"));
    }

    @Test
    void statesFileTypesBooleansNumbersAndStrings() {
        String states = Typings.states(sample());
        assertTrue(states.contains("'minecraft:wheat': { age: number | '0' | '1' | '2'; };"));
        assertTrue(states.contains("waterlogged: boolean | 'false' | 'true';"));
        assertTrue(states.contains("facing: 'north' | 'south';"));
    }

    @Test
    void overloadsUseIdTypesAndTagReferences() {
        String overloads = Typings.overloads(sample());
        assertTrue(overloads.contains("shaped(result: ItemIdLike, pattern: string[], key: Record<string, ItemIdLike | ItemTagRef>): RecipeBuilder;"));
        assertTrue(overloads.contains("custom(json: ModJSRecipeJson): RecipeBuilder;"));
        assertTrue(overloads.contains("type AnyIdLike = ItemId | (string & {});"));
    }

    @Test
    void generatedFilesCoverEveryDeclarationKind() {
        assertEquals(List.of("ids.d.ts", "tags.d.ts", "states.d.ts", "recipes.d.ts", "overloads.d.ts"), List.copyOf(Typings.files(sample()).keySet()));
    }

    @Test
    void idsReadsLocationsFromKeyText() {
        assertEquals("minecraft:item", Ids.location("ResourceKey[minecraft:root / minecraft:item]"));
        assertEquals("minecraft:logs", Ids.location("TagKey[minecraft:item / minecraft:logs]"));
        assertEquals("item", Ids.registryName("minecraft:item"));
        assertEquals("other:thing", Ids.registryName("other:thing"));
        assertEquals("c:gems", Ids.stripHash("#c:gems"));
    }

    // --- API reflection ---

    public interface Marker {
    }

    public static class SampleEventJS implements Marker {
        public String alreadyDeclared() {
            return "";
        }

        public void newMethod(String name, int count, boolean flag, List<String> items, Runnable callback) {
        }

        public Marker other(SampleEventJS self) {
            return null;
        }

        public static void ignoredStatic() {
        }
    }

    @Test
    void apiReflectionAddsOnlyMissingMembers() {
        String curated = "interface EventBase { cancel(): void; }\ninterface SampleEvent extends EventBase {\n  alreadyDeclared(): string;\n}\n";
        String api = ApiReflector.generate(curated, List.of(SampleEventJS.class));
        assertTrue(api.contains("interface SampleEvent {"));
        assertFalse(api.contains("alreadyDeclared"));
        assertFalse(api.contains("ignoredStatic"));
        assertTrue(api.contains("newMethod("));
        assertTrue(api.contains(": string, ") && api.contains(": number, ") && api.contains(": boolean, "));
        assertTrue(api.contains("any[]") && api.contains("(...args: any[]) => any"));
        assertTrue(api.contains("other(") && api.contains("): any;"));
    }

    @Test
    void apiReflectionWritesWholeInterfaceForUndeclaredEvent() {
        String api = ApiReflector.generate("interface EventBase { cancel(): void; }", List.of(SampleEventJS.class));
        assertTrue(api.contains("interface SampleEvent extends EventBase {"));
        assertTrue(api.contains("alreadyDeclared(): string;"));
    }

    @Test
    void declaredMembersReadsInterfaceBodies() {
        Map<String, java.util.Set<String>> declared = ApiReflector.declaredMembers("interface A {\n  foo(x: string): void;\n  bar?: number;\n  readonly baz: string;\n}\ninterface B {\n  qux(): void;\n}\n");
        assertEquals(java.util.Set.of("foo", "bar", "baz"), declared.get("A"));
        assertEquals(java.util.Set.of("qux"), declared.get("B"));
    }

    @Test
    void scriptFacingClassesAreEventsAndBuilders() {
        assertTrue(ApiReflector.isScriptFacing(SampleEventJS.class));
        assertFalse(ApiReflector.isScriptFacing(ProbeTest.class));
        assertEquals("SampleEvent", ApiReflector.scriptName(SampleEventJS.class));
    }

    // --- writing ---

    @Test
    void writerRefusesPathsOutsideTheProbeFolder(@TempDir Path temp) {
        ProbeWriter writer = new ProbeWriter(temp.resolve("probe"));
        assertThrows(IOException.class, () -> writer.write("../escape.txt", "x"));
        assertThrows(IOException.class, () -> writer.write("data/../../escape.txt", "x"));
        assertThrows(IOException.class, () -> writer.write("", "x"));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }

    @Test
    void writerReplacesFilesWithoutLeavingTemporaryFiles(@TempDir Path temp) throws IOException {
        ProbeWriter writer = new ProbeWriter(temp.resolve("probe"));
        writer.write("data/a.json", "one");
        writer.write("data/a.json", "two");
        assertEquals("two", Files.readString(temp.resolve("probe/data/a.json")));
        try (var files = Files.list(temp.resolve("probe/data"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void dumpWritesDeclarationsDataAndProjectConfig(@TempDir Path temp) throws IOException {
        DumpData data = sample();
        data.hasClientData = true;
        data.lang.put("item.minecraft.stone", "Stone");
        data.assets.put("texture", List.of("minecraft:block/stone"));
        Path root = temp.resolve("modjs");
        Files.createDirectories(root);
        List<String> written = DumpOutput.write(new ProbeWriter(root.resolve("probe")), root, data, List.of("note"), "interface EventBase {}", List.of(SampleEventJS.class));
        assertTrue(written.contains("ids.d.ts") && written.contains("api.d.ts") && written.contains("data/lang.json") && written.contains("data/meta.json"));
        JsonObject meta = JsonParser.parseString(Files.readString(root.resolve("probe/data/meta.json"))).getAsJsonObject();
        assertEquals("1.21.1", meta.get("minecraft").getAsString());
        assertEquals("neoforge", meta.get("loader").getAsString());
        assertEquals(2, meta.getAsJsonArray("mods").size());
        assertEquals("note", meta.getAsJsonArray("warnings").get(0).getAsString());
        assertTrue(Files.readString(root.resolve("jsconfig.json")).contains("probe/**/*.d.ts"));
    }

    @Test
    void serverOnlyDumpSkipsClientFiles(@TempDir Path temp) throws IOException {
        Path root = temp.resolve("modjs");
        Files.createDirectories(root);
        List<String> written = DumpOutput.write(new ProbeWriter(root.resolve("probe")), root, sample(), List.of(), "", List.of());
        assertFalse(written.contains("data/lang.json"));
        assertFalse(written.contains("api.d.ts"));
    }

    @Test
    void projectConfigUpgradesOnlyTheFileModJSGenerated(@TempDir Path temp) throws IOException {
        Path config = temp.resolve("jsconfig.json");
        Files.writeString(config, "{\n  \"include\": [\"probe/modjs.d.ts\", \"startup_scripts/**/*.js\", \"server_scripts/**/*.js\", \"client_scripts/**/*.js\"]\n}\n");
        assertTrue(DumpOutput.ensureProjectConfig(temp));
        assertTrue(Files.readString(config).contains("probe/**/*.d.ts"));

        Files.writeString(config, "{ \"include\": [\"my_scripts/**/*.js\"] }");
        assertFalse(DumpOutput.ensureProjectConfig(temp));
        assertEquals("{ \"include\": [\"my_scripts/**/*.js\"] }", Files.readString(config));
    }
}
