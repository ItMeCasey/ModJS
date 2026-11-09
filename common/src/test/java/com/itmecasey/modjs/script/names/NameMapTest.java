package com.itmecasey.modjs.script.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/** Classes that carry only the names a real 1.20.1 game has, scripts that use the official ones. */
class NameMapTest {
    public static class Base {
        public String m_10_() { return "base-hello"; }                      // official: hello
        public boolean m_11_() { return true; }                              // official: isAlive
    }

    public static class Thing extends Base implements Named {
        public int f_1_ = 7;                                                  // official: count
        public static final String f_2_ = "DIAMOND-VALUE";                    // official: DIAMOND
        public static String m_2_(String s) { return "literal:" + s; }       // official: literal(String)
        public static String m_3_(int n) { return "literal-int:" + n; }      // official: literal(int)
        public float m_5_() { return 20.5f; }                                 // official: getHealth
        public void m_6_(float v) { f_1_ = (int) v; }                         // official: setHealth
        public String name() { return "plain"; }                              // a name that needs no translation
        public String m_20_() { return "from-interface-default"; }            // official: label (declared in Named)
    }

    public interface Named {
        String m_20_();
    }

    private static final String TABLE = String.join("\n",
        "#" + NameMapTest.class.getName() + "$Thing",
        "f\tcount\tf_1_",
        "f\tDIAMOND\tf_2_",
        "m\tliteral\tm_2_",
        "m\tliteral\tm_3_",
        "m\tgetHealth\tm_5_",
        "m\tsetHealth\tm_6_",
        "#" + NameMapTest.class.getName() + "$Base",
        "m\thello\tm_10_",
        "m\tisAlive\tm_11_",
        "#" + NameMapTest.class.getName() + "$Named",
        "m\tlabel\tm_20_");

    private NameMap map;
    private Context cx;
    private ScriptableObject scope;

    @BeforeEach
    void setUp() {
        map = NameMap.parse(TABLE);
        cx = Context.enter();
        scope = cx.initStandardObjects();
        ScriptableObject.putProperty(scope, "Thing", new MappedJavaClass(map, scope, Thing.class));
        ScriptableObject.putProperty(scope, "thing", new MappedJavaObject(map, scope, new Thing(), null));
    }

    @AfterEach
    void tearDown() {
        Context.exit();
    }

    private Object run(String script) {
        Object result = cx.evaluateString(scope, script, "test", 1, null);
        return result instanceof org.mozilla.javascript.Wrapper w ? w.unwrap() : result;
    }

    @Test
    void tableLooksThroughTheWholeClassHierarchy() {
        assertEquals(List.of("m_2_", "m_3_"), map.methodNames(Thing.class, "literal"));
        assertEquals(List.of("m_10_"), map.methodNames(Thing.class, "hello"), "declared in the superclass");
        assertEquals(List.of("m_20_"), map.methodNames(Thing.class, "label"), "declared in an interface");
        assertEquals(List.of("f_2_"), map.fieldNames(Thing.class, "DIAMOND"));
        assertTrue(map.methodNames(Thing.class, "nothing").isEmpty());
    }

    @Test
    void staticFieldsAndOverloadedStaticMethodsAreFoundByOfficialName() {
        assertEquals("DIAMOND-VALUE", String.valueOf(run("Thing.DIAMOND")));
        assertEquals("literal:abc", String.valueOf(run("Thing.literal('abc')")));
        assertEquals("literal-int:5", String.valueOf(run("Thing.literal(5)")), "the other overload has its own real-game name");
    }

    @Test
    void instanceFieldsMethodsAndInheritedMethodsWork() {
        assertEquals(7, ((Number) run("thing.count")).intValue());
        assertEquals("base-hello", String.valueOf(run("thing.hello()")));
        assertEquals("from-interface-default", String.valueOf(run("thing.label()")));
        run("thing.count = 9");
        assertEquals(9, ((Number) run("thing.count")).intValue());
    }

    @Test
    void propertiesStyleAccessReachesGettersByTheirOfficialNames() {
        assertEquals(20.5, ((Number) run("thing.health")).doubleValue(), 1e-6);
        assertEquals(Boolean.TRUE, run("thing.alive"));
        assertEquals(20.5, ((Number) run("thing.getHealth()")).doubleValue(), 1e-6);
        run("thing.setHealth(3)");
        assertEquals(3, ((Number) run("thing.count")).intValue());
    }

    @Test
    void namesThatNeedNoTranslationAndMissingNamesKeepWorkingAsBefore() {
        assertEquals("plain", String.valueOf(run("thing.name()")));
        assertEquals(Scriptable.NOT_FOUND, ((Scriptable) scope.get("thing", scope)).get("definitelyMissing", scope));
        RhinoException e = assertThrows(RhinoException.class, () -> run("thing.definitelyMissing()"));
        assertNotNull(e.getMessage());
        assertFalse(((Scriptable) scope.get("thing", scope)).has("definitelyMissing", scope));
        assertTrue(((Scriptable) scope.get("thing", scope)).has("hello", scope));
    }

    @Test
    void parseIgnoresBrokenLinesAndTheResourceIsAbsentWhenABuildShipsNone() {
        NameMap small = NameMap.parse("garbage\n#a.B\nm\tx\ty\nbroken line\nf\tq\tr\n");
        assertEquals(1, small.classCount());
        assertEquals(null, NameMap.load(new ClassLoader(null) { }));
    }
}
