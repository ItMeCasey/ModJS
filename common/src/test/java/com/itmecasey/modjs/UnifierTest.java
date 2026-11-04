package com.itmecasey.modjs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.format.LegacyFormat;
import com.itmecasey.modjs.format.ModernFormat;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.unify.TagIndex;
import com.itmecasey.modjs.unify.Unifier;
import com.itmecasey.modjs.unify.UnifyConfig;
import com.itmecasey.modjs.unify.UnifyMaterials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifierTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private TagIndex index() {
        return new TagIndex(Map.of(
            "c:ingots/tin", List.of(json("{\"values\":[\"othermod:tin_ingot\",\"#c:ingots/tin_extra\"]}"), json("{\"values\":[\"modjs:tin_ingot\"]}")),
            "c:ingots/tin_extra", List.of(json("{\"values\":[\"create:tin_ingot\",{\"id\":\"missing:tin_ingot\",\"required\":false}]}"))));
    }

    @BeforeEach
    void setUp() {
        UnifyConfig.resetStartup();
        UnifyConfig.resetServer();
        VersionFormat.use(new ModernFormat());
        UnifyConfig.addCanonicalItem("c:ingots/tin", "modjs:tin_ingot");
    }

    @AfterEach
    void tearDown() {
        UnifyConfig.resetStartup();
        UnifyConfig.resetServer();
        Unifier.clear();
    }

    @Test
    void tagIndexExpandsNestedTags() {
        assertEquals(java.util.Set.of("othermod:tin_ingot", "create:tin_ingot", "missing:tin_ingot", "modjs:tin_ingot"), index().resolve("c:ingots/tin"));
    }

    @Test
    void ourItemIsCanonicalAndRecipesAreRewritten() {
        Unifier unifier = Unifier.build(index(), id -> !id.startsWith("missing:"));
        assertEquals(1, unifier.groups().size());
        assertEquals("modjs:tin_ingot", unifier.groups().get(0).canonical());
        assertEquals("modjs:tin_ingot", unifier.canonicalFor("create:tin_ingot"));
        assertEquals(null, unifier.canonicalFor("missing:tin_ingot"));

        JsonObject crafting = json("{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"TT\"],\"key\":{\"T\":\"create:tin_ingot\"},\"result\":{\"id\":\"othermod:tin_ingot\",\"count\":2}}");
        JsonObject rewritten = unifier.apply("create:x", crafting);
        assertNotSame(crafting, rewritten);
        assertEquals("modjs:tin_ingot", rewritten.getAsJsonObject("key").get("T").getAsString());
        assertEquals("modjs:tin_ingot", rewritten.getAsJsonObject("result").get("id").getAsString());
        assertEquals(2, rewritten.getAsJsonObject("result").get("count").getAsInt());

        JsonObject untouched = json("{\"type\":\"minecraft:smelting\",\"ingredient\":\"minecraft:iron_ore\",\"result\":{\"id\":\"minecraft:iron_ingot\"}}");
        assertSame(untouched, unifier.apply("minecraft:iron", untouched));
    }

    @Test
    void conversionRecipesSkipTheCanonicalItemAndAreNotRewritten() {
        Unifier unifier = Unifier.build(index(), id -> !id.startsWith("missing:"));
        Map<String, JsonObject> conversions = unifier.conversionRecipes();
        assertEquals(2, conversions.size());
        String id = "modjs:unify/modjs_tin_ingot_from_create_tin_ingot";
        assertTrue(conversions.containsKey(id));
        JsonObject recipe = conversions.get(id);
        assertSame(recipe, unifier.apply(id, recipe));
    }

    @Test
    void tagModeKeepsEveryMemberUsable() {
        UnifyConfig.group("c:ingots/tin", null);
        UnifyConfig.Binding binding = new UnifyConfig.Binding();
        binding.inputs("tag");
        Unifier unifier = Unifier.build(index(), id -> true);
        JsonObject rewritten = unifier.apply("a:b", json("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"create:tin_ingot\"],\"result\":{\"id\":\"minecraft:stone\"}}"));
        assertEquals("#c:ingots/tin", rewritten.getAsJsonArray("ingredients").get(0).getAsString());
    }

    @Test
    void preferOrderPicksAnExistingItemWhenNoneIsOurs() {
        UnifyConfig.resetStartup();
        new UnifyConfig.Binding().prefer(List.of("create", "othermod"));
        new UnifyConfig.Binding().group("c:ingots/tin");
        Unifier unifier = Unifier.build(new TagIndex(Map.of("c:ingots/tin",
            List.of(json("{\"values\":[\"othermod:tin_ingot\",\"create:tin_ingot\"]}")))), id -> true);
        assertEquals("create:tin_ingot", unifier.groups().get(0).canonical());
    }

    @Test
    void legacyFormatRewritesObjectIngredients() {
        VersionFormat.use(new LegacyFormat());
        Unifier unifier = Unifier.build(index(), id -> true);
        JsonObject rewritten = unifier.apply("a:b", json("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"create:tin_ingot\"}],\"result\":{\"id\":\"minecraft:stone\"}}"));
        assertEquals("modjs:tin_ingot", rewritten.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString());
    }

    @Test
    void materialExpansionSkipsVanillaAndBuildsTags() {
        List<UnifyMaterials.Entry> entries = UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("iron", "tin", "ruby"), List.of(), false));
        assertTrue(entries.stream().noneMatch(e -> e.id().equals("iron_ingot")));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("tin_ingot") && e.tag().equals("c:ingots/tin")));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("raw_tin") && e.tag().equals("c:raw_materials/tin")));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("tin_block") && e.block()));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("ruby_gem") && e.tag().equals("c:gems/ruby")));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals("iron_plate")));
        List<UnifyMaterials.Entry> withVanilla = UnifyMaterials.expand(new UnifyMaterials.Spec(List.of("iron"), List.of("ingot"), true));
        assertEquals(1, withVanilla.size());
        byte[] png = UnifyMaterials.texture("ingot", 0xD8D8D8);
        assertTrue(png.length > 50 && png[1] == 'P');
    }
}
