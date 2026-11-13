package com.itmecasey.modjs.config;

import com.itmecasey.modjs.script.SafeFiles;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One registered config: the schema a script declared, the checked values, and the file they live in. Nothing here throws at a script:
 * a file that cannot be read, a wrong type or an out-of-range value is logged and replaced by the default.
 */
public final class ScriptConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    /** Which scripts and which side of the game use the config. */
    public enum Side {
        COMMON(""), CLIENT("-client"), SERVER("-server");

        final String suffix;

        Side(String suffix) {
            this.suffix = suffix;
        }

        public static Side parse(String text) {
            return switch (text == null ? "common" : text.toLowerCase(java.util.Locale.ROOT)) {
                case "client" -> CLIENT;
                case "server" -> SERVER;
                case "common", "" -> COMMON;
                default -> null;
            };
        }
    }

    /** A script function waiting for changes, with the keys that changed since it last ran. */
    static final class Listener {
        final Function function;
        final Scriptable scope;
        final ScriptType type;
        final Set<String> pending = new LinkedHashSet<>();

        Listener(Function function, Scriptable scope, ScriptType type) {
            this.function = function;
            this.scope = scope;
            this.type = type;
        }
    }

    public final String name;
    public final Side side;
    private ConfigSpec.GroupSpec schema;
    private final Path file;
    private Map<String, Object> values;
    private long stamp = Long.MIN_VALUE;
    private boolean loaded;
    private byte[] lastWritten = new byte[0];
    private final List<Listener> listeners = new ArrayList<>();

    ScriptConfig(String name, Side side, ConfigSpec.GroupSpec schema, Path file) {
        this.name = name;
        this.side = side;
        this.schema = schema;
        this.file = file;
        @SuppressWarnings("unchecked")
        Map<String, Object> defaults = (Map<String, Object>) schema.defaultValue();
        this.values = defaults;
    }

    private void warn(String message) {
        LOGGER.warn("[config:{}] {}", name, message);
        ConfigManager.record("config:" + name + " " + message);
    }

    public synchronized ConfigSpec.GroupSpec schema() {
        return schema;
    }

    /** The file this config lives in, or null for a config that has no file (a client config on a dedicated server). */
    public Path file() {
        return file;
    }

    // -- loading and saving -----------------------------------------------------------------------------------------------

    /** Replaces the schema (the script ran again) and reads the file. */
    synchronized void define(ConfigSpec.GroupSpec next) {
        this.schema = next;
        if (!loaded) {
            load();
            return;
        }
        // registered again (a script ran again, or another script declares the same name): the values in memory stay, including ones that
        // were set and not saved; they are checked against the new entries, and a file changed on disk is read first
        Map<String, Object> before = values;
        ConfigFormat.Validated checked = ConfigFormat.validate(schema, values);
        checked.problems().stream().filter(p -> !p.contains(": missing, added")).forEach(this::warn);
        values = checked.values();
        markChanged(diff(before, values));
        poll();
    }

    /** Reads the file: creates it with the defaults when missing (the only time it is written here); missing and bad entries use their defaults. Returns the keys that changed. */
    public synchronized Set<String> load() {
        loaded = true;
        Map<String, Object> before = values;
        if (file == null) {
            return Set.of();
        }
        if (!Files.exists(file)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> defaults = (Map<String, Object>) schema.defaultValue();
            values = defaults;
            byte[] first = ConfigFormat.render(schema, values).getBytes(StandardCharsets.UTF_8);
            try {
                if (SafeFiles.createIfAbsent(file, first)) {
                    lastWritten = first;
                    stamp = Files.getLastModifiedTime(file).toMillis();
                    return diff(before, values);
                }
            } catch (IOException e) {
                warn("could not create " + file.getFileName() + ": " + e.getMessage());
                return diff(before, values);
            }
            // the file appeared while this was creating it: read that one
            return load();
        }
        if (!Files.isRegularFile(file)) {
            warn(file.getFileName() + " is not a file; using the defaults");
            return Set.of();
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
            stamp = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            warn("could not read " + file.getFileName() + ": " + e.getMessage() + "; keeping the current values");
            return Set.of();
        }
        Object raw;
        try {
            raw = ConfigFormat.parse(text);
        } catch (ConfigFormat.ParseException e) {
            warn(file.getFileName() + " is not valid (" + e.getMessage() + "); keeping the current values and leaving the file as it is");
            return Set.of();
        }
        ConfigFormat.Validated checked = ConfigFormat.validate(schema, raw);
        checked.problems().forEach(this::warn);
        values = checked.values();
        // an existing file is never rewritten here: missing and refused entries are used with their defaults, and only cfg.save() writes
        Set<String> changed = diff(before, values);
        markChanged(changed);
        return changed;
    }

    /**
     * Replaces the file with the current values. When the file on disk is not what this config last wrote (the user edited it), a copy is
     * kept first as {@code <name>.bak}, so nothing the user wrote is lost. The new file is written through a temporary file.
     */
    private void write() {
        if (file == null) {
            return;
        }
        try {
            byte[] next = ConfigFormat.render(schema, values).getBytes(StandardCharsets.UTF_8);
            if (Files.isRegularFile(file) && !java.util.Arrays.equals(Files.readAllBytes(file), lastWritten)) {
                Path kept = SafeFiles.backup(file);
                warn("kept a copy of the file you edited as " + kept.getFileName());
            }
            SafeFiles.writeAtomic(file, next);
            lastWritten = next;
            stamp = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            warn("could not write " + file.getFileName() + ": " + e.getMessage());
        }
    }

    /** Writes the current values to the file. */
    public synchronized void save() {
        write();
    }

    /** Reads the file again; true when a value changed. */
    public synchronized boolean reload() {
        return !load().isEmpty();
    }

    /** Called about once a second: reads the file again when it was changed on disk. */
    synchronized void poll() {
        if (file == null) {
            return;
        }
        try {
            if (Files.isRegularFile(file) && Files.getLastModifiedTime(file).toMillis() != stamp) {
                load();
            }
        } catch (IOException e) {
            // the file is being replaced; the next poll sees it
        }
    }

    // -- values -----------------------------------------------------------------------------------------------------------

    /** The value at a dotted path: a plain Java value, or a map for a group, or null when the path is not in the schema. */
    public synchronized Object get(String path) {
        Object current = values;
        for (String part : path.split("\\.", -1)) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return null;
            }
            current = map.get(part);
        }
        return deepCopy(current);
    }

    /** Whether the dotted path names an entry or a group of the schema. */
    public synchronized boolean has(String path) {
        return schema.find(path) != null;
    }

    /** Whether the dotted path names a group. */
    public synchronized boolean isGroup(String path) {
        return path.isEmpty() || schema.find(path) instanceof ConfigSpec.GroupSpec;
    }

    /** Checks and stores a value. Returns false (and logs) when the path is unknown or the value is refused; the old value stays. */
    @SuppressWarnings("unchecked")
    public synchronized boolean set(String path, Object raw) {
        ConfigSpec spec = schema.find(path);
        if (spec == null) {
            warn("set: there is no entry '" + path + "'");
            return false;
        }
        if (spec instanceof ConfigSpec.GroupSpec) {
            warn("set: '" + path + "' is a group; set its entries");
            return false;
        }
        ConfigSpec.Result result = spec.coerce(raw);
        if (!result.ok()) {
            warn("set " + path + ": " + result.problem() + "; the value stays " + ConfigFormat.display(get(path)));
            return false;
        }
        String[] parts = path.split("\\.", -1);
        Map<String, Object> target = values;
        for (int i = 0; i < parts.length - 1; i++) {
            target = (Map<String, Object>) target.get(parts[i]);
        }
        Object old = target.get(parts[parts.length - 1]);
        if (old != null && old.equals(result.value())) {
            return true;
        }
        target.put(parts[parts.length - 1], result.value());
        markChanged(Set.of(path));
        return true;
    }

    /** A copy of all values as nested maps and lists. */
    public synchronized Map<String, Object> snapshot() {
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) deepCopy(values);
        return copy;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), deepCopy(v)));
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(v -> out.add(deepCopy(v)));
            return out;
        }
        return value;
    }

    /** The dotted paths of entries whose values differ. */
    static Set<String> diff(Map<String, Object> a, Map<String, Object> b) {
        Set<String> out = new LinkedHashSet<>();
        diff("", a, b, out);
        return out;
    }

    private static void diff(String prefix, Map<String, Object> a, Map<String, Object> b, Set<String> out) {
        for (Map.Entry<String, Object> entry : b.entrySet()) {
            Object before = a.get(entry.getKey());
            Object after = entry.getValue();
            if (after instanceof Map<?, ?> afterMap && before instanceof Map<?, ?> beforeMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> x = (Map<String, Object>) beforeMap;
                @SuppressWarnings("unchecked")
                Map<String, Object> y = (Map<String, Object>) afterMap;
                diff(prefix + entry.getKey() + ".", x, y, out);
            } else if (before == null || !before.equals(after)) {
                out.add(prefix + entry.getKey());
            }
        }
    }

    // -- change callbacks -------------------------------------------------------------------------------------------------

    synchronized void addListener(Function function, Scriptable scope, ScriptType type) {
        listeners.add(new Listener(function, scope, type));
    }

    /** Forgets the callbacks of scripts that ran again (their functions belong to a scope that is gone). */
    synchronized void dropListeners(ScriptType type) {
        listeners.removeIf(l -> l.type == type);
    }

    private void markChanged(Set<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        for (Listener listener : listeners) {
            listener.pending.addAll(keys);
        }
    }

    /** Runs the callbacks of one script type that have changes waiting; returns how many ran. */
    int deliver(ScriptType type) {
        List<Listener> due = new ArrayList<>();
        List<Set<String>> keys = new ArrayList<>();
        synchronized (this) {
            for (Listener listener : listeners) {
                if (listener.type == type && !listener.pending.isEmpty()) {
                    due.add(listener);
                    keys.add(new LinkedHashSet<>(listener.pending));
                    listener.pending.clear();
                }
            }
        }
        for (int i = 0; i < due.size(); i++) {
            Listener listener = due.get(i);
            Set<String> changed = keys.get(i);
            try {
                com.itmecasey.modjs.script.ModJSContextFactory.INSTANCE.call(cx -> {
                    Object[] args = {new ConfigHandle(this, "", listener.scope, listener.type), cx.newArray(listener.scope, changed.toArray())};
                    return listener.function.call(cx, listener.scope, listener.scope, args);
                });
            } catch (org.mozilla.javascript.RhinoException e) {
                com.itmecasey.modjs.script.ScriptManager.of(type).reportError("config '" + name + "' onChange", e);
            }
        }
        return due.size();
    }
}
