package com.itmecasey.modjs.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Calls a script function from engine code (block ticks, button presses, overlay templates). A function that throws is reported
 * once to its script type, then skipped, so a broken tick handler cannot flood the log twenty times a second. Java objects are
 * wrapped for scripts; a {@link Map} becomes a plain script object.
 */
public final class ScriptBlockCalls {
    private static final Set<Function> FAILED = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private ScriptBlockCalls() {
    }

    public static void forgetFailures() {
        FAILED.clear();
    }

    public static boolean hasFailed(Function fn) {
        return FAILED.contains(fn);
    }

    public static void invoke(ScriptType type, String where, Function fn, Object... args) {
        if (fn == null || FAILED.contains(fn)) {
            return;
        }
        try {
            ModJSContextFactory.INSTANCE.call(cx -> {
                Scriptable scope = fn.getParentScope();
                Object[] wrapped = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    wrapped[i] = wrap(cx, scope, args[i]);
                }
                return fn.call(cx, scope, scope, wrapped);
            });
        } catch (RhinoException e) {
            FAILED.add(fn);
            ScriptManager.of(type).reportError(where, e);
        }
    }

    private static Object wrap(Context cx, Scriptable scope, Object value) {
        if (value instanceof Map<?, ?> map) {
            return toObject(map, scope);
        }
        return Context.javaToJS(value, scope);
    }

    /** A plain script object with the entries of a map. Without a scope the object has no prototype, which is enough to read fields. */
    public static NativeObject toObject(Map<?, ?> map, Scriptable scope) {
        NativeObject object = new NativeObject();
        if (scope != null) {
            object.setParentScope(scope);
            object.setPrototype(ScriptableObjectPrototype.of(scope));
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            object.put(String.valueOf(entry.getKey()), object, entry.getValue());
        }
        return object;
    }

    /** {@link #toObject(Map, Scriptable)} in the client script scope, for values returned from Java to client scripts. */
    public static NativeObject toClientObject(Map<?, ?> map) {
        Scriptable scope = null;
        try {
            scope = ScriptManager.of(ScriptType.CLIENT).scope();
        } catch (RuntimeException ignored) {
            // no script engine yet (unit tests): fall back to a bare object
        }
        return toObject(map, scope);
    }

    /** The shared Object prototype of a scope, so plain objects made here behave like script literals. */
    private static final class ScriptableObjectPrototype {
        static Scriptable of(Scriptable scope) {
            return org.mozilla.javascript.ScriptableObject.getObjectPrototype(scope);
        }
    }
}
