package com.itmecasey.modjs.script.names;

import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

/** A wrapped Java object that also answers to the official Minecraft member names when the game only has its own. */
public final class MappedJavaObject extends NativeJavaObject {
    private final NameMap map;

    public MappedJavaObject(NameMap map, Scriptable scope, Object javaObject, Class<?> staticType) {
        super(scope, javaObject, staticType);
        this.map = map;
    }

    private Object rawGet(String name, Scriptable start) {
        try {
            return super.get(name, start);
        } catch (org.mozilla.javascript.EvaluatorException e) {
            return NOT_FOUND;
        }
    }

    @Override
    public Object get(String name, Scriptable start) {
        Object value = super.get(name, start);
        if (value != NOT_FOUND || javaObject == null) {
            return value;
        }
        return Resolver.find(map, javaObject.getClass(), name, start, this::rawGet, false);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (javaObject != null && !super.has(name, start)) {
            for (String srg : map.fieldNames(javaObject.getClass(), name)) {
                if (super.has(srg, start)) {
                    super.put(srg, start, value);
                    return;
                }
            }
        }
        super.put(name, start, value);
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return super.has(name, start) || (javaObject != null && get(name, start) != NOT_FOUND);
    }
}
