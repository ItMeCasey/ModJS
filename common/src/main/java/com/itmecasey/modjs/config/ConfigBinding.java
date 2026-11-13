package com.itmecasey.modjs.config;

import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The {@code Config} global: {@code Config.register(name, schema, options)} plus the entry builders {@code Config.number}, {@code integer},
 * {@code bool}, {@code string}, {@code choice}, {@code list} and {@code group}. Mistakes in a schema (a default outside its own range, a
 * reserved key) are script errors; problems in a file are never errors, see {@link ScriptConfig}.
 */
public final class ConfigBinding extends ScriptableObject {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private interface Body {
        Object call(Context cx, Scriptable scope, Object[] args);
    }

    public ConfigBinding(Scriptable scope) {
        ScriptRuntime.setObjectProtoAndParent(this, scope);
        define("register", ConfigBinding::register);
        define("get", (cx, s, args) -> {
            ScriptConfig config = ConfigManager.get(text(args, 0, "Config.get"));
            return config == null ? null : new ConfigHandle(config, "", s, typeOf(s));
        });
        define("has", (cx, s, args) -> ConfigManager.get(text(args, 0, "Config.has")) != null);
        define("reload", (cx, s, args) -> {
            if (args.length == 0) {
                for (String name : ConfigManager.names()) {
                    ConfigManager.reload(name);
                }
                return true;
            }
            return ConfigManager.reload(text(args, 0, "Config.reload"));
        });
        define("file", (cx, s, args) -> {
            ScriptConfig config = ConfigManager.get(text(args, 0, "Config.file"));
            return config == null || config.file() == null ? null : config.file().toString();
        });
        define("names", (cx, s, args) -> {
            NativeArray array = new NativeArray(ConfigManager.names().toArray());
            ScriptRuntime.setBuiltinProtoAndParent(array, s, org.mozilla.javascript.TopLevel.Builtins.Array);
            return array;
        });
        define("number", (cx, s, args) -> spec(s, number(false, args)));
        define("integer", (cx, s, args) -> spec(s, number(true, args)));
        define("int", (cx, s, args) -> spec(s, number(true, args)));
        define("bool", (cx, s, args) -> {
            if (args.length < 1 || !(JsonUtil.unwrap(args[0]) instanceof Boolean b)) {
                throw Context.reportRuntimeError("Config.bool needs a default of true or false: Config.bool(true)");
            }
            return spec(s, comment(new ConfigSpec.BoolSpec(b), args, 1));
        });
        define("string", (cx, s, args) -> {
            String fallback = args.length < 1 ? "" : text(args, 0, "Config.string");
            Object maxLength = option(args, 1, "maxLength");
            Object pattern = option(args, 1, "pattern");
            Pattern compiled = null;
            if (pattern != null) {
                try {
                    compiled = Pattern.compile(Context.toString(pattern));
                } catch (PatternSyntaxException e) {
                    throw Context.reportRuntimeError("Config.string: the pattern is not valid: " + e.getDescription());
                }
            }
            ConfigSpec.StringSpec result = new ConfigSpec.StringSpec(fallback, maxLength instanceof Number n ? n.intValue() : 0, compiled);
            if (!result.coerce(fallback).ok()) {
                throw Context.reportRuntimeError("Config.string: the default " + ConfigSpec.show(fallback) + " does not fit its own limits");
            }
            return spec(s, comment(result, args, 1));
        });
        define("choice", (cx, s, args) -> {
            String fallback = text(args, 0, "Config.choice");
            if (args.length < 2 || !(JsonUtil.unwrap(args[1]) instanceof List<?> raw) || raw.isEmpty()) {
                throw Context.reportRuntimeError("Config.choice needs the default and a list of options: Config.choice('fast', ['fast', 'slow'])");
            }
            List<String> options = new ArrayList<>();
            raw.forEach(o -> options.add(Context.toString(o)));
            if (!options.contains(fallback)) {
                throw Context.reportRuntimeError("Config.choice: the default '" + fallback + "' is not one of " + String.join(", ", options));
            }
            return spec(s, comment(new ConfigSpec.ChoiceSpec(fallback, options), args, 2));
        });
        define("list", (cx, s, args) -> {
            String element = text(args, 0, "Config.list");
            if (!List.of("string", "number", "int", "bool").contains(element)) {
                throw Context.reportRuntimeError("Config.list: the element type is string, number, int or bool, not '" + element + "'");
            }
            List<Object> fallback = new ArrayList<>();
            if (args.length >= 2 && args[1] != null && !(args[1] instanceof Undefined)) {
                if (!(JsonUtil.unwrap(args[1]) instanceof List<?> items)) {
                    throw Context.reportRuntimeError("Config.list: the default is a list, for example Config.list('string', ['a', 'b'])");
                }
                fallback.addAll(items);
            }
            Object maxSize = option(args, 2, "maxSize");
            int limit = maxSize instanceof Number n ? n.intValue() : 0;
            Double min = optionNumber(args, 2, "min");
            Double max = optionNumber(args, 2, "max");
            ConfigSpec.Result checked = new ConfigSpec.ListSpec(element, fallback, limit, min, max).coerce(fallback);
            if (!checked.ok()) {
                throw Context.reportRuntimeError("Config.list: the default does not fit its own limits: " + checked.problem());
            }
            @SuppressWarnings("unchecked")
            List<Object> converted = (List<Object>) checked.value();
            return spec(s, comment(new ConfigSpec.ListSpec(element, converted, limit, min, max), args, 2));
        });
        define("group", (cx, s, args) -> {
            ConfigSpec.GroupSpec group = group(args.length < 1 ? null : args[0], "Config.group", false);
            return spec(s, comment(group, args, 1));
        });
    }

    @Override
    public String getClassName() {
        return "ConfigApi";
    }

    private void define(String name, Body body) {
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
        put(name, this, function);
    }

    // -- helpers ---------------------------------------------------------------------------------------------------------------

    /** The script type of the caller: the type that owns its scope, else the type being loaded, else server. */
    static ScriptType typeOf(Scriptable scope) {
        ScriptType type = ScriptManager.typeOf(scope);
        if (type == null) {
            type = ScriptManager.currentType();
        }
        return type == null ? ScriptType.SERVER : type;
    }

    private static Object spec(Scriptable scope, ConfigSpec spec) {
        return Context.javaToJS(spec, scope);
    }

    private static String text(Object[] args, int index, String caller) {
        if (args.length <= index || args[index] == null || args[index] instanceof Undefined) {
            throw Context.reportRuntimeError(caller + " needs a text argument");
        }
        return Context.toString(args[index]);
    }

    private static Object option(Object[] args, int index, String key) {
        if (args.length <= index) {
            return null;
        }
        Object value = JsonUtil.property(args[index], key);
        return value instanceof Undefined ? null : value;
    }

    private static Double optionNumber(Object[] args, int index, String key) {
        Object value = option(args, index, key);
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static <T extends ConfigSpec> T comment(T spec, Object[] args, int optionsIndex) {
        Object comment = option(args, optionsIndex, "comment");
        if (comment != null) {
            spec.comment = Context.toString(comment);
        }
        return spec;
    }

    private static ConfigSpec number(boolean integer, Object[] args) {
        String caller = integer ? "Config.integer" : "Config.number";
        if (args.length < 1 || !(JsonUtil.unwrap(args[0]) instanceof Number n)) {
            throw Context.reportRuntimeError(caller + " needs a number as its default: " + caller + "(1.5, { min: 0, max: 10 })");
        }
        ConfigSpec.NumberSpec result = new ConfigSpec.NumberSpec(integer, n.doubleValue(), optionNumber(args, 1, "min"), optionNumber(args, 1, "max"));
        ConfigSpec.Result checked = result.coerce(n);
        if (!checked.ok()) {
            throw Context.reportRuntimeError(caller + ": the default does not fit its own limits: " + checked.problem());
        }
        return comment(result, args, 1);
    }

    // -- schema ----------------------------------------------------------------------------------------------------------------

    private static ConfigSpec.GroupSpec group(Object schema, String caller, boolean root) {
        Object plain = JsonUtil.unwrap(schema);
        if (!(plain instanceof Scriptable object) || plain instanceof List<?>) {
            throw Context.reportRuntimeError(caller + " needs an object of entries: { speed: Config.number(1.5), enabled: Config.bool(true) }");
        }
        ConfigSpec.GroupSpec group = new ConfigSpec.GroupSpec();
        for (Object id : ScriptableObject.getPropertyIds(object)) {
            String key = String.valueOf(id);
            if (!KEY.matcher(key).matches()) {
                throw Context.reportRuntimeError(caller + ": the key '" + key + "' must be letters, digits and underscores, not starting with a digit");
            }
            if (root && ConfigHandle.RESERVED.contains(key)) {
                throw Context.reportRuntimeError(caller + ": the key '" + key + "' is the name of a config method; pick another");
            }
            group.children.put(key, entry(ScriptableObject.getProperty(object, key), caller + "." + key));
        }
        return group;
    }

    private static ConfigSpec entry(Object value, String where) {
        Object plain = JsonUtil.unwrap(value);
        if (plain instanceof ConfigSpec spec) {
            return spec;
        }
        if (plain instanceof Boolean b) {
            return new ConfigSpec.BoolSpec(b);
        }
        if (plain instanceof Number n) {
            return new ConfigSpec.NumberSpec(false, n.doubleValue(), null, null);
        }
        if (plain instanceof CharSequence text) {
            return new ConfigSpec.StringSpec(text.toString(), 0, null);
        }
        if (plain instanceof List<?> items) {
            String element = "string";
            if (!items.isEmpty()) {
                Object first = JsonUtil.unwrap(items.get(0));
                element = first instanceof Boolean ? "bool" : first instanceof Number ? "number" : "string";
            }
            ConfigSpec.ListSpec list = new ConfigSpec.ListSpec(element, new ArrayList<>(items), 0, null, null);
            ConfigSpec.Result checked = list.coerce(new ArrayList<>(items));
            if (!checked.ok()) {
                throw Context.reportRuntimeError(where + ": " + checked.problem());
            }
            @SuppressWarnings("unchecked")
            List<Object> converted = (List<Object>) checked.value();
            return new ConfigSpec.ListSpec(element, converted, 0, null, null);
        }
        if (plain instanceof Scriptable) {
            return group(plain, where, false);
        }
        throw Context.reportRuntimeError(where + ": use Config.number, Config.bool, Config.string, Config.choice, Config.list, a nested object, or a plain number, boolean, text or array");
    }

    // -- register --------------------------------------------------------------------------------------------------------------

    /**
     * {@code Config.register(name, schema, { side: 'common' | 'client' | 'server' })}. The file is {@code modjs/config/<name>.json5}
     * ({@code <name>-client.json5} and {@code <name>-server.json5} for the other sides).
     */
    private static Object register(Context cx, Scriptable scope, Object[] args) {
        String name = text(args, 0, "Config.register");
        if (!NAME.matcher(name).matches()) {
            throw Context.reportRuntimeError("Config.register: the name '" + name + "' must be letters, digits, '_', '-' or '.'");
        }
        if (args.length < 2) {
            throw Context.reportRuntimeError("Config.register needs a schema: Config.register('" + name + "', { speed: Config.number(1.5) })");
        }
        Object sideOption = option(args, 2, "side");
        ScriptConfig.Side side = ScriptConfig.Side.parse(sideOption == null ? null : Context.toString(sideOption));
        if (side == null) {
            throw Context.reportRuntimeError("Config.register: side is 'common', 'client' or 'server', not '" + Context.toString(sideOption) + "'");
        }
        ScriptType type = typeOf(scope);
        if (type == ScriptType.CLIENT && side == ScriptConfig.Side.SERVER) {
            throw Context.reportRuntimeError("Config.register: a server config cannot be registered from a client script");
        }
        if (type == ScriptType.SERVER && side == ScriptConfig.Side.CLIENT) {
            throw Context.reportRuntimeError("Config.register: a client config cannot be registered from a server script");
        }
        ConfigSpec.GroupSpec schema = group(args[1], "Config.register", true);
        ScriptConfig config = ConfigManager.register(name, side, schema, type);
        return new ConfigHandle(config, "", scope, type);
    }
}
