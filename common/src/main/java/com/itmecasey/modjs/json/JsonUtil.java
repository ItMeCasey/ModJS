package com.itmecasey.modjs.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.itmecasey.modjs.item.ItemRef;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.util.List;
import java.util.Map;

/** Converts values coming out of Rhino (native objects, arrays, Java wrappers) into Gson trees. */
public final class JsonUtil {
    private JsonUtil() {
    }

    public static Object unwrap(Object value) {
        while (value instanceof Wrapper wrapper) {
            value = wrapper.unwrap();
        }
        return value;
    }

    public static JsonElement toJson(Object raw) {
        Object value = unwrap(raw);
        if (value == null || value instanceof Undefined) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof JsonElement element) {
            return element;
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return new JsonPrimitive((long) d);
            }
            return new JsonPrimitive(d);
        }
        if (value instanceof CharSequence text) {
            return new JsonPrimitive(text.toString());
        }
        if (value instanceof ItemRef ref) {
            return new JsonPrimitive(ref.id());
        }
        if (value instanceof NativeArray array) {
            JsonArray out = new JsonArray();
            for (int i = 0; i < array.getLength(); i++) {
                out.add(toJson(array.get(i, array)));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            JsonArray out = new JsonArray();
            for (Object item : list) {
                out.add(toJson(item));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject out = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.add(String.valueOf(entry.getKey()), toJson(entry.getValue()));
            }
            return out;
        }
        if (value instanceof Callable) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof Scriptable scriptable) {
            JsonObject out = new JsonObject();
            for (Object id : scriptable.getIds()) {
                Object child = id instanceof Number n ? scriptable.get(n.intValue(), scriptable) : scriptable.get(String.valueOf(id), scriptable);
                if (child == Scriptable.NOT_FOUND || child instanceof Callable) {
                    continue;
                }
                out.add(String.valueOf(id), toJson(child));
            }
            return out;
        }
        return new JsonPrimitive(String.valueOf(value));
    }

    /** Returns the value as a plain string, or null if it is not string-like. */
    public static String asString(Object raw) {
        Object value = unwrap(raw);
        if (value instanceof CharSequence text) {
            return text.toString();
        }
        if (value instanceof ItemRef ref) {
            return ref.id();
        }
        return null;
    }

    /** Reads a property of a script object, or null when the value is not an object or lacks the property. */
    public static Object property(Object raw, String name) {
        Object value = unwrap(raw);
        if (value instanceof Scriptable scriptable) {
            Object child = scriptable.get(name, scriptable);
            return child == Scriptable.NOT_FOUND ? null : unwrap(child);
        }
        return null;
    }
}
