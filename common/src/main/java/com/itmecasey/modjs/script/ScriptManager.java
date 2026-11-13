package com.itmecasey.modjs.script;

import com.itmecasey.modjs.ModJSInfo;
import com.itmecasey.modjs.event.EventGroups;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/** Loads and runs the scripts of one {@link ScriptType}, and collects their errors. */
public final class ScriptManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModJSInfo.MOD_ID);
    private static final Map<ScriptType, ScriptManager> MANAGERS = new EnumMap<>(ScriptType.class);
    private static final ThreadLocal<ScriptType> LOADING = new ThreadLocal<>();
    private static Path root = Path.of("modjs");

    static {
        for (ScriptType type : ScriptType.values()) {
            MANAGERS.put(type, new ScriptManager(type));
        }
    }

    public static ScriptManager of(ScriptType type) {
        return MANAGERS.get(type);
    }

    /** The script type a scope belongs to (the scope a script was run in), or null when it is none of them. */
    public static ScriptType typeOf(Scriptable scope) {
        if (scope == null) {
            return null;
        }
        Scriptable top = ScriptableObject.getTopLevelScope(scope);
        for (ScriptType type : ScriptType.values()) {
            if (MANAGERS.get(type).scope == top) {
                return type;
            }
        }
        return null;
    }

    /** The type whose scripts are currently being loaded on this thread, or null. */
    public static ScriptType currentType() {
        return LOADING.get();
    }

    public static void setRoot(Path scriptRoot) {
        root = scriptRoot;
    }

    public static Path root() {
        return root;
    }

    private final ScriptType type;
    private final ScriptConsole console;
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private volatile Scriptable scope;
    private int loadedCount;

    private ScriptManager(ScriptType type) {
        this.type = type;
        this.console = new ScriptConsole(type, LOGGER);
    }

    public ScriptType type() {
        return type;
    }

    public Scriptable scope() {
        return scope;
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public int loadedCount() {
        return loadedCount;
    }

    public void addError(String message) {
        errors.add(message);
    }

    public void reportError(String where, RhinoException e) {
        String message = where + ": " + e.details()
            + (e.sourceName() != null ? " (" + e.sourceName() + "#" + e.lineNumber() + ")" : "");
        LOGGER.error("[{}] {}", type, message);
        errors.add(message);
    }

    public Path directory() {
        return root.resolve(type.directory);
    }

    /** Clears this type's handlers and errors, then runs every {@code .js} file under its directory in path order. */
    public synchronized void reload() {
        errors.clear();
        loadedCount = 0;
        EventGroups.clear(type);
        com.itmecasey.modjs.config.ConfigManager.scriptsReloading(type);
        com.itmecasey.modjs.villager.TradeOps.scriptsReloading(type);
        if (type == ScriptType.CLIENT) {
            com.itmecasey.modjs.client.overlay.Overlay.clear();
        }
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("Could not create {}", dir, e);
            errors.add("Could not create " + dir + ": " + e.getMessage());
            return;
        }

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".js")).sorted().forEach(files::add);
        } catch (IOException e) {
            LOGGER.error("Could not list {}", dir, e);
            errors.add("Could not list " + dir + ": " + e.getMessage());
            return;
        }

        LOADING.set(type);
        try {
            scope = newScope();
            for (Path file : files) {
                runFile(dir, file);
            }
        } finally {
            LOADING.remove();
        }
        if (type == ScriptType.SERVER) {
            com.itmecasey.modjs.worldcraft.WorldRecipes.rebuild();
        }
        LOGGER.info("[{}] loaded {} script(s), {} error(s)", type, loadedCount, errors.size());
    }

    private Scriptable newScope() {
        return ModJSContextFactory.INSTANCE.call(cx -> {
            ScriptableObject fresh = cx.initSafeStandardObjects();
            Bindings.install(type, cx, fresh, console);
            return fresh;
        });
    }

    /** Evaluates source text in this type's scope, creating the scope first if needed. Used by tests and tools. */
    public synchronized Object eval(String source, String name) {
        ScriptType previous = LOADING.get();
        LOADING.set(type);
        try {
            if (scope == null) {
                scope = newScope();
            }
            return ModJSContextFactory.INSTANCE.call(cx -> cx.evaluateString(scope, source, name, 1, null));
        } catch (RhinoException e) {
            reportError(name, e);
            return null;
        } finally {
            if (previous == null) {
                LOADING.remove();
            } else {
                LOADING.set(previous);
            }
        }
    }

    private void runFile(Path dir, Path file) {
        String name = dir.relativize(file).toString().replace('\\', '/');
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[{}] could not read {}", type, name, e);
            errors.add("Could not read " + name + ": " + e.getMessage());
            return;
        }
        try {
            ModJSContextFactory.INSTANCE.call(cx -> cx.evaluateString(scope, source, name, 1, null));
            loadedCount++;
        } catch (RhinoException e) {
            reportError(name, e);
        }
    }
}
