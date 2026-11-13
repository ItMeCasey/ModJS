package com.itmecasey.modjs.config;

import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        ConfigManager.clear();
    }

    @AfterEach
    void tearDown() {
        ConfigManager.clear();
    }

    private static ConfigSpec.GroupSpec schema() {
        ConfigSpec.GroupSpec s = new ConfigSpec.GroupSpec();
        ConfigSpec.NumberSpec speed = new ConfigSpec.NumberSpec(false, 1.5, 0.0, 10.0);
        speed.comment = "How fast it goes";
        s.children.put("speed", speed);
        s.children.put("count", new ConfigSpec.NumberSpec(true, 3, 1.0, 9.0));
        s.children.put("enabled", new ConfigSpec.BoolSpec(true));
        s.children.put("mode", new ConfigSpec.ChoiceSpec("fast", List.of("fast", "slow")));
        s.children.put("name", new ConfigSpec.StringSpec("x", 8, null));
        s.children.put("tags", new ConfigSpec.ListSpec("string", List.of("a"), 4, null, null));
        ConfigSpec.GroupSpec nested = new ConfigSpec.GroupSpec();
        nested.children.put("limit", new ConfigSpec.NumberSpec(true, 5, null, null));
        s.children.put("nested", nested);
        return s;
    }

    private static ConfigFormat.Validated validate(String text) throws ConfigFormat.ParseException {
        return ConfigFormat.validate(schema(), ConfigFormat.parse(text));
    }

    @Test
    void defaultsRenderWithCommentsAndReadBackUnchanged() throws Exception {
        ConfigSpec.GroupSpec s = schema();
        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) s.defaultValue();
        String text = ConfigFormat.render(s, defaults);
        assertTrue(text.contains("// How fast it goes"));
        assertTrue(text.contains("// Number from 0 to 10. Default: 1.5"));
        assertTrue(text.contains("// One of fast, slow. Default: \"fast\""));
        ConfigFormat.Validated again = ConfigFormat.validate(s, ConfigFormat.parse(text));
        assertEquals(defaults, again.values());
        assertTrue(again.problems().isEmpty(), again.problems().toString());
        assertFalse(again.rewrite());
    }

    @Test
    void missingKeysAreFilledInAndFlaggedForRewrite() throws Exception {
        ConfigFormat.Validated v = validate("{ \"speed\": 4 }");
        assertEquals(4.0, v.values().get("speed"));
        assertEquals(3L, v.values().get("count"));
        assertEquals(true, v.values().get("enabled"));
        assertEquals(5L, ((Map<?, ?>) v.values().get("nested")).get("limit"));
        assertTrue(v.rewrite());
        assertTrue(v.problems().stream().anyMatch(p -> p.startsWith("count: missing")));
    }

    @Test
    void wrongTypesAndOutOfRangeValuesResetToTheDefaultWithAMessage() throws Exception {
        ConfigFormat.Validated v = validate("{ speed: 99, count: 2.5, enabled: 'maybe', mode: 'medium', name: 'much too long', tags: 'a', nested: 4 }");
        assertEquals(1.5, v.values().get("speed"));
        assertEquals(3L, v.values().get("count"));
        assertEquals(true, v.values().get("enabled"));
        assertEquals("fast", v.values().get("mode"));
        assertEquals("x", v.values().get("name"));
        assertEquals(List.of("a"), v.values().get("tags"));
        assertEquals(5L, ((Map<?, ?>) v.values().get("nested")).get("limit"));
        assertTrue(v.problems().stream().anyMatch(p -> p.contains("speed: 99 is above the maximum 10")));
        assertTrue(v.problems().stream().anyMatch(p -> p.contains("count: expected a whole number")));
        assertTrue(v.problems().stream().anyMatch(p -> p.contains("mode: expected one of fast, slow")));
        assertTrue(v.rewrite());
    }

    @Test
    void valuesAreCoercedFromTextAndNumbers() throws Exception {
        ConfigFormat.Validated v = validate("{ speed: '2.5', count: '4', enabled: 'TRUE', mode: 'SLOW', name: 12, tags: ['b', 7, true] }");
        assertEquals(2.5, v.values().get("speed"));
        assertEquals(4L, v.values().get("count"));
        assertEquals(true, v.values().get("enabled"));
        assertEquals("slow", v.values().get("mode"));
        assertEquals("12", v.values().get("name"));
        assertEquals(List.of("b", "7", "true"), v.values().get("tags"));
    }

    @Test
    void unknownKeysAreDroppedWithAMessage() throws Exception {
        ConfigFormat.Validated v = validate("{ speed: 1, extra: 5 }");
        assertFalse(v.values().containsKey("extra"));
        assertTrue(v.problems().stream().anyMatch(p -> p.equals("extra: unknown key, ignored")));
    }

    @Test
    void theParserAcceptsCommentsAndUnquotedNamesButNotBrokenText() throws Exception {
        assertEquals(Map.of("a", 1.0), ConfigFormat.parse("// note\n{ a: 1 /* inline */ }"));
        assertEquals(Map.of(), ConfigFormat.parse("   "));
        assertThrows(ConfigFormat.ParseException.class, () -> ConfigFormat.parse("{ speed: 1,, }"));
        assertThrows(ConfigFormat.ParseException.class, () -> ConfigFormat.parse("{ speed: 1 } extra"));
        assertThrows(ConfigFormat.ParseException.class, () -> ConfigFormat.parse("{ speed: "));
    }

    @Test
    void stringLimitsAndPatternsAreChecked() {
        ConfigSpec.StringSpec spec = new ConfigSpec.StringSpec("ab", 4, Pattern.compile("[a-z]+"));
        assertTrue(spec.coerce("abc").ok());
        assertFalse(spec.coerce("abcde").ok());
        assertFalse(spec.coerce("AB").ok());
        assertFalse(spec.coerce(List.of()).ok());
    }

    @Test
    void theFileIsCreatedOnFirstRunNeverRewrittenByReloadsAndKeptWhenBroken() throws IOException {
        ScriptConfig config = ConfigManager.register("pack", ScriptConfig.Side.COMMON, schema());
        Path file = root.resolve("config/pack.json5");
        assertTrue(Files.isRegularFile(file));
        assertEquals(1.5, config.get("speed"));
        // a file from an older version of the schema: only one key. It is read, never rewritten by a reload.
        String old = "{ \"speed\": 7 } // my note";
        Files.writeString(file, old);
        assertTrue(config.reload());
        assertEquals(7.0, config.get("speed"));
        assertEquals("fast", config.get("mode"));
        assertEquals(old, Files.readString(file));
        // save() writes the whole file, and keeps a copy of the file the user wrote
        config.save();
        assertTrue(Files.readString(file).contains("\"mode\": \"fast\""));
        assertTrue(Files.readString(file).contains("// How fast it goes"));
        assertEquals(old, Files.readString(root.resolve("config/pack.json5.bak")));
        config.save(); // unchanged since the last write: no second copy
        assertFalse(Files.exists(root.resolve("config/pack.json5.bak-2")));
        // a broken file is left alone and the values stay
        Files.writeString(file, "{ speed: ");
        assertFalse(config.reload());
        assertEquals("{ speed: ", Files.readString(file));
        assertEquals(7.0, config.get("speed"));
    }

    @Test
    void setChecksTheValueAndSaveWritesIt() throws IOException {
        ScriptConfig config = ConfigManager.register("pack", ScriptConfig.Side.COMMON, schema());
        assertTrue(config.set("speed", 2));
        assertFalse(config.set("speed", 50));
        assertFalse(config.set("nope", 1));
        assertFalse(config.set("nested", 1));
        assertTrue(config.set("nested.limit", "8"));
        assertEquals(2.0, config.get("speed"));
        assertEquals(8L, config.get("nested.limit"));
        config.save();
        assertTrue(Files.readString(root.resolve("config/pack.json5")).contains("\"speed\": 2.0"));
        assertEquals(null, config.get("nope"));
    }

    @Test
    void sidesChooseTheFileName() {
        ConfigManager.register("a", ScriptConfig.Side.COMMON, schema());
        ConfigManager.register("b", ScriptConfig.Side.SERVER, schema());
        assertTrue(Files.isRegularFile(root.resolve("config/a.json5")));
        assertTrue(Files.isRegularFile(root.resolve("config/b-server.json5")));
        assertEquals(List.of("a", "b"), ConfigManager.serverSide().stream().map(c -> c.name).toList());
    }

    private static Object eval(String source) {
        return ScriptManager.of(ScriptType.SERVER).eval(source, "config-test.js");
    }

    @Test
    void scriptsReadWriteAndNestAsProperties() {
        ScriptManager.of(ScriptType.SERVER).reload();
        eval("var cfg = Config.register('pack', { speed: Config.number(1.5, { min: 0, max: 10, comment: 'x' }), enabled: Config.bool(true),"
            + " mode: Config.choice('fast', ['fast', 'slow']), name: Config.string('x'), tags: Config.list('string', ['a']), count: Config.integer(3),"
            + " nested: { limit: 5, label: 'hi' }, plain: 2.5 });");
        assertEquals(List.of(), ScriptManager.of(ScriptType.SERVER).errors());
        assertEquals(1.5, ((Number) eval("cfg.speed")).doubleValue());
        assertEquals("fast", String.valueOf(eval("cfg.get('mode')")));
        assertEquals(5.0, ((Number) eval("cfg.nested.limit")).doubleValue());
        assertEquals(5.0, ((Number) eval("cfg.get('nested.limit')")).doubleValue());
        assertEquals("a", String.valueOf(eval("cfg.tags[0]")));
        assertEquals(2.5, ((Number) eval("cfg.plain")).doubleValue());
        eval("cfg.speed = 4; cfg.nested.limit = 9;");
        assertEquals(4.0, ((Number) eval("cfg.speed")).doubleValue());
        assertEquals(9.0, ((Number) eval("cfg.nested.limit")).doubleValue());
        eval("cfg.speed = 99;");
        assertEquals(4.0, ((Number) eval("cfg.speed")).doubleValue());
        assertEquals(Boolean.FALSE, eval("cfg.set('speed', -1)"));
        assertEquals(Boolean.TRUE, eval("cfg.set('mode', 'slow')"));
        assertEquals("{\"speed\":4,\"enabled\":true,\"mode\":\"slow\",\"name\":\"x\",\"tags\":[\"a\"],\"count\":3,\"nested\":{\"limit\":9,\"label\":\"hi\"},\"plain\":2.5}",
            String.valueOf(eval("JSON.stringify(cfg)")));
        assertEquals(List.of(), ScriptManager.of(ScriptType.SERVER).errors());
    }

    @Test
    void schemaMistakesAreScriptErrorsButFileProblemsAreNot() {
        ScriptManager.of(ScriptType.SERVER).reload();
        assertEquals(null, eval("Config.register('bad', { get: 1 })"));
        assertEquals(null, eval("Config.register('bad', { x: Config.number(50, { max: 10 }) })"));
        assertEquals(null, eval("Config.register('bad', { x: Config.choice('c', ['a', 'b']) })"));
        assertEquals(null, eval("Config.register('bad', { 'a b': 1 })"));
        assertTrue(ScriptManager.of(ScriptType.SERVER).errors().size() >= 4);
    }

    @Test
    void onChangeRunsForSetsAndForEditedFiles() throws IOException {
        ScriptManager.of(ScriptType.SERVER).reload();
        eval("var seen = []; var cfg = Config.register('pack', { speed: Config.number(1.5), mode: Config.choice('fast', ['fast', 'slow']) });"
            + " cfg.onChange(function (c, keys) { seen.push(keys.join('+') + '=' + c.speed); });");
        eval("cfg.set('speed', 3);");
        ConfigManager.pollNow(false);
        assertEquals("speed=3", String.valueOf(eval("seen.join(',')")));
        // setting the same value again is not a change
        eval("cfg.set('speed', 3);");
        ConfigManager.pollNow(false);
        assertEquals("speed=3", String.valueOf(eval("seen.join(',')")));
        // an edit of the file
        Path file = root.resolve("config/pack.json5");
        Files.writeString(file, "{ \"speed\": 6, \"mode\": \"slow\" }");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));
        ConfigManager.pollNow(false);
        assertEquals("speed=3,speed+mode=6", String.valueOf(eval("seen.join(',')")));
        // a script that runs again drops the old callbacks
        ScriptManager.of(ScriptType.SERVER).reload();
        eval("var seen2 = 0; var cfg = Config.register('pack', { speed: Config.number(1.5), mode: Config.choice('fast', ['fast', 'slow']) }); cfg.onChange(function () { seen2++; });");
        eval("cfg.set('speed', 2);");
        ConfigManager.pollNow(false);
        assertEquals(1.0, ((Number) eval("seen2")).doubleValue());
    }
}
