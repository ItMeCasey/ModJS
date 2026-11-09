package com.itmecasey.modjs.script.names;

import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.Scriptable;

/** A Java class object (Items, Commands, ...) whose static members also answer to the official Minecraft names. */
public final class MappedJavaClass extends NativeJavaClass {
    private final NameMap map;
    private final Class<?> type;

    public MappedJavaClass(NameMap map, Scriptable scope, Class<?> type) {
        super(scope, type);
        this.map = map;
        this.type = type;
    }

    /** Rhino throws for a missing static member instead of returning NOT_FOUND; this does the second. */
    private Object rawGet(String name, Scriptable start) {
        try {
            return super.get(name, start);
        } catch (EvaluatorException e) {
            return NOT_FOUND;
        }
    }

    @Override
    public Object get(String name, Scriptable start) {
        EvaluatorException missing;
        try {
            return super.get(name, start);
        } catch (EvaluatorException e) {
            missing = e;
        }
        Object value = Resolver.find(map, type, name, start, this::rawGet, true);
        if (value == NOT_FOUND) {
            throw missing;
        }
        return value;
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return super.has(name, start) || Resolver.find(map, type, name, start, this::rawGet, true) != NOT_FOUND;
    }
}
