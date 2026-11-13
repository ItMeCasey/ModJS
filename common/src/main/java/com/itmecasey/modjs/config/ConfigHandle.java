package com.itmecasey.modjs.config;

import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What {@code Config.register} returns: the entries read as properties ({@code cfg.speed}, {@code cfg.nested.limit}) and written by
 * assignment, plus {@code get}, {@code set}, {@code save}, {@code reload}, {@code onChange} and {@code toJSON}. A nested group is a live
 * view of the same config.
 */
public final class ConfigHandle extends ScriptableObject {
    /** Names of the methods; an entry of the schema may not use them. */
    public static final Set<String> RESERVED = Set.of("get", "set", "save", "reload", "onChange", "toJSON");

    private interface Body {
        Object call(Context cx, Scriptable scope, Object[] args);
    }

    private final ScriptConfig config;
    private final String prefix;
    private final ScriptType type;
    private final Map<String, Function> methods = new HashMap<>();

    ConfigHandle(ScriptConfig config, String prefix, Scriptable scope) {
        this(config, prefix, scope, scriptType());
    }

    ConfigHandle(ScriptConfig config, String prefix, Scriptable scope, ScriptType type) {
        this.config = config;
        this.prefix = prefix;
        this.type = type;
        ScriptRuntime.setObjectProtoAndParent(this, scope);
    }

    private static ScriptType scriptType() {
        ScriptType current = com.itmecasey.modjs.script.ScriptManager.currentType();
        return current == null ? ScriptType.SERVER : current;
    }

    @Override
    public String getClassName() {
        return "Config";
    }

    private Function method(String name) {
        Function found = methods.get(name);
        if (found != null) {
            return found;
        }
        Body body = switch (name) {
            case "get" -> (cx, scope, args) -> {
                String path = path(args, 0);
                if (!config.has(path)) {
                    return Undefined.instance;
                }
                return toScript(path, config.get(path), getParentScope());
            };
            case "set" -> (cx, scope, args) -> args.length >= 2 && config.set(path(args, 0), JsonUtil.unwrap(args[1]));
            case "save" -> (cx, scope, args) -> {
                config.save();
                return Undefined.instance;
            };
            case "reload" -> (cx, scope, args) -> config.reload();
            case "onChange" -> (cx, scope, args) -> {
                if (args.length < 1 || !(args[0] instanceof Function callback)) {
                    throw Context.reportRuntimeError("Config onChange needs a function: cfg.onChange((cfg, keys) => { ... })");
                }
                config.addListener(callback, getParentScope(), type);
                return Undefined.instance;
            };
            case "toJSON" -> (cx, scope, args) -> plain(config.snapshot(), getParentScope());
            default -> null;
        };
        BaseFunction function = new BaseFunction() {
            @Override
            public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
                return body.call(cx, scope, args);
            }

            @Override
            public String getFunctionName() {
                return name;
            }
        };
        ScriptRuntime.setFunctionProtoAndParent(function, getParentScope());
        methods.put(name, function);
        return function;
    }

    private String path(Object[] args, int index) {
        if (args.length <= index) {
            throw Context.reportRuntimeError("Config: a key such as 'speed' or 'nested.limit' is needed");
        }
        return prefix + Context.toString(args[index]);
    }

    // -- properties ----------------------------------------------------------------------------------------------------------

    @Override
    public Object get(String name, Scriptable start) {
        if (prefix.isEmpty()) {
            switch (name) {
                case "get", "set", "save", "reload", "onChange", "toJSON" -> {
                    return method(name);
                }
                default -> {
                }
            }
        }
        String path = prefix + name;
        if (!config.has(path)) {
            return NOT_FOUND;
        }
        return toScript(path, config.get(path), getParentScope());
    }

    @Override
    public Object get(int index, Scriptable start) {
        return NOT_FOUND;
    }

    @Override
    public boolean has(String name, Scriptable start) {
        return (prefix.isEmpty() && RESERVED.contains(name)) || config.has(prefix + name);
    }

    @Override
    public void put(String name, Scriptable start, Object value) {
        if (prefix.isEmpty() && RESERVED.contains(name)) {
            return;
        }
        config.set(prefix + name, JsonUtil.unwrap(value));
    }

    @Override
    public void delete(String name) {
        // entries cannot be removed
    }

    @Override
    public Object[] getIds() {
        Object group = prefix.isEmpty() ? config.get("") : config.get(prefix.substring(0, prefix.length() - 1));
        List<Object> ids = new ArrayList<>();
        if (group instanceof Map<?, ?> map) {
            ids.addAll(map.keySet());
        }
        return ids.toArray();
    }

    @Override
    public Object getDefaultValue(Class<?> hint) {
        return "[Config " + config.name + (prefix.isEmpty() ? "" : " " + prefix.substring(0, prefix.length() - 1)) + "]";
    }

    // -- conversion ----------------------------------------------------------------------------------------------------------

    private Object toScript(String path, Object value, Scriptable scope) {
        if (value instanceof Map<?, ?>) {
            return new ConfigHandle(config, path + ".", scope, type);
        }
        return plain(value, scope);
    }

    /** Plain Java values as script values: numbers, booleans, text, arrays and objects. */
    static Object plain(Object value, Scriptable scope) {
        if (value instanceof Long l) {
            return l.doubleValue();
        }
        if (value instanceof List<?> list) {
            Object[] items = new Object[list.size()];
            for (int i = 0; i < items.length; i++) {
                items[i] = plain(list.get(i), scope);
            }
            NativeArray array = new NativeArray(items);
            ScriptRuntime.setBuiltinProtoAndParent(array, scope, org.mozilla.javascript.TopLevel.Builtins.Array);
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            NativeObject object = new NativeObject();
            ScriptRuntime.setBuiltinProtoAndParent(object, scope, org.mozilla.javascript.TopLevel.Builtins.Object);
            map.forEach((k, v) -> object.put(String.valueOf(k), object, plain(v, scope)));
            return object;
        }
        return value;
    }
}
