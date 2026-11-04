package com.itmecasey.modjs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.format.ModernFormat;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.recipe.RecipeChanges;
import com.itmecasey.modjs.unify.TagIndex;
import com.itmecasey.modjs.unify.Unifier;
import com.itmecasey.modjs.unify.UnifyConfig;
import com.itmecasey.modjs.unify.UnifyFile;
import com.itmecasey.modjs.unify.UnifyMaterials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifyExtrasTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @BeforeEach
    void setUp() {
        UnifyConfig.resetStartup();
        UnifyConfig.resetServer();
        VersionFormat.use(new ModernFormat());
    }

    @AfterEach
    void tearDown() {
        UnifyConfig.resetStartup();
        UnifyConfig.resetServer();
        Unifier.clear();
    }

    @Test
    void oreKindAddsItsDropAndUsesVanillaRawWhenSkipped() {
        List<UnifyMaterials.Entry> entries = UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("tin", "iron", "ruby"), List.of("ore"), false));
        UnifyMaterials.Entry tinOre = entries.stream().filter(e -> e.id().equals("tin_ore")).findFirst().orElseThrow();
        assertTrue(tinOre.block());
        assertEquals("c:ores/tin", tinOre.tag());
        assertEquals("modjs:raw_tin", tinOre.dropId());
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("raw_tin")));
        UnifyMaterials.Entry ironOre = entries.stream().filter(e -> e.id().equals("iron_ore")).findFirst().orElseThrow();
        assertEquals("minecraft:raw_iron", ironOre.dropId());
        assertEquals("modjs:ruby_gem", entries.stream().filter(e -> e.id().equals("ruby_ore")).findFirst().orElseThrow().dropId());
        for (String kind : List.of("crushed", "clump", "shard", "dirty_dust", "ore")) {
            assertTrue(UnifyMaterials.texture(kind, 0xAA5522).length > 50, kind);
        }
        List<UnifyMaterials.Entry> extras = UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("tin"), List.of("crushed", "clump", "shard", "dirty_dust"), false));
        assertEquals(List.of("crushed_tin", "tin_clump", "tin_shard", "tin_dirty_dust"), extras.stream().map(UnifyMaterials.Entry::id).toList());
        assertEquals("c:crushed_raw_materials/tin", extras.get(0).tag());
    }

    @Test
    void rodsAreAMaterialKindWithTheirOwnTag() {
        List<UnifyMaterials.Entry> entries = UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("tin", "steel"), List.of("rod"), false));
        assertEquals(List.of("tin_rod", "steel_rod"), entries.stream().map(UnifyMaterials.Entry::id).toList());
        assertEquals("c:rods/steel", entries.get(1).tag());
        assertEquals("Steel Rod", entries.get(1).displayName());
        assertTrue(UnifyMaterials.texture("rod", 0x6C7380).length > 50);
        assertTrue(UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("tin"), List.of(), false)).stream().anyMatch(e -> e.id().equals("tin_rod")));
    }

    @Test
    void colorsCanBeSetAndTextureIsStillAValidPng() {
        UnifyConfig.setColor("mythril", 0x7FD6F5);
        List<UnifyMaterials.Entry> entries = UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("mythril"), List.of("ingot"), false));
        assertEquals(0x7FD6F5, entries.get(0).color());
        byte[] png = UnifyMaterials.texture("ingot", entries.get(0).color());
        assertTrue(png.length > 50 && png[1] == 'P');
        for (String kind : List.of("plate", "gear", "coin", "dust", "gem", "raw", "ore")) {
            assertTrue(UnifyMaterials.texture(kind, 0x7FD6F5).length > 50, kind);
        }
    }

    @Test
    void colorsAreSampledFromTexturesTheGameAlreadyHas() {
        java.awt.image.BufferedImage ingot = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                // dark outline on the edge, light highlight on one pixel, the material tone elsewhere
                int rgb = (y == 0) ? 0x101010 : (x == 3 && y == 3) ? 0xFFFFFF : 0x80A0C0;
                ingot.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        com.itmecasey.modjs.unify.ColorSampler.setFinder(path -> path.equals("textures/item/mythril_ingot.png") ? ingot : null);
        try {
            assertEquals(0x80A0C0, com.itmecasey.modjs.unify.ColorSampler.sample("mythril"));
            assertEquals(null, com.itmecasey.modjs.unify.ColorSampler.sample("unknownium"));
            assertEquals(0x80A0C0, UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("mythril"), List.of("dust"), false)).get(0).color());
            UnifyConfig.setColor("mythril", 0x123456);
            assertEquals(0x123456, UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("mythril"), List.of("dust"), false)).get(0).color());
        } finally {
            com.itmecasey.modjs.unify.ColorSampler.setFinder(path -> null);
        }
    }

    @Test
    void itemGroupsCollectNamesIconsAndSelectors() {
        com.itmecasey.modjs.client.RecipeViewerEventJS.GroupItems event = new com.itmecasey.modjs.client.RecipeViewerEventJS.GroupItems();
        event.group("Gems", List.of("minecraft:diamond", "#c:gems/ruby", "@mymod"));
        event.group("Ores", "#c:ores", null);
        assertEquals(2, event.groups().size());
        assertEquals("Gems", event.groups().get(0).name());
        assertEquals(List.of("minecraft:diamond", "#c:gems/ruby", "@mymod"), event.groups().get(0).selectors());
        assertEquals(null, event.groups().get(0).icon());
        assertEquals(List.of("#c:ores"), event.groups().get(1).selectors());
    }

    @Test
    void fluidsAreRewrittenInRecipes() {
        UnifyConfig.addCanonicalFluid("c:oil", "modjs:oil");
        TagIndex fluids = new TagIndex(Map.of("c:oil", List.of(json("{\"values\":[\"mymod:oil\",\"othermod:crude\",\"modjs:oil\"]}"))));
        Unifier unifier = Unifier.build(new TagIndex(Map.of()), id -> true, fluids, id -> true);
        assertTrue(unifier.active());
        assertEquals("modjs:oil", unifier.fluidCanonicalFor("mymod:oil"));
        JsonObject recipe = json("{\"type\":\"x:refining\",\"input\":{\"fluid\":\"mymod:oil\",\"amount\":100},\"outputs\":[{\"fluid\":{\"id\":\"othermod:crude\"},\"amount\":5}]}");
        JsonObject out = unifier.apply("x:r", recipe);
        assertEquals("modjs:oil", out.getAsJsonObject("input").get("fluid").getAsString());
        assertEquals("modjs:oil", out.getAsJsonArray("outputs").get(0).getAsJsonObject().getAsJsonObject("fluid").get("id").getAsString());
        assertEquals(1, unifier.fluidsRewritten());
        assertFalse(RecipeChanges.mapFluids(json("{\"fluid\":\"keep:me\"}"), id -> null));
    }

    @Test
    void configFileIsCreatedAndRead(@TempDir Path root) throws Exception {
        UnifyFile.applyStartup(root);
        Path file = root.resolve("config").resolve("unify.json");
        assertTrue(Files.isRegularFile(file));
        assertTrue(UnifyConfig.materialSpecs().isEmpty());
        Files.writeString(file, "{\"registerMaterials\":true,\"materials\":[\"tin\"],\"kinds\":[\"ingot\"],\"prefer\":[\"create\"],\"auto\":[\"ingots\"],\"inputs\":\"tag\",\"skipMods\":[\"mekanism\"],\"swapInventories\":false}");
        UnifyFile.applyStartup(root);
        assertEquals(1, UnifyConfig.materialSpecs().size());
        UnifyFile.applyServer(root);
        assertFalse(UnifyConfig.swapInventories());
        TagIndex index = new TagIndex(Map.of("c:ingots/tin", List.of(json("{\"values\":[\"create:tin_ingot\",\"mekanism:tin_ingot\",\"other:tin_ingot\"]}"))));
        Unifier unifier = Unifier.build(index, id -> true);
        assertEquals("create:tin_ingot", unifier.groups().get(0).canonical());
        assertEquals(List.of("other:tin_ingot"), unifier.groups().get(0).members());
        assertEquals(UnifyConfig.InputMode.TAG, unifier.groups().get(0).inputs());
    }
}
