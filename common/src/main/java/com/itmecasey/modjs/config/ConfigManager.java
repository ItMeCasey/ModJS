package com.itmecasey.modjs.config;

import com.itmecasey.modjs.script.PlatformBinding;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every config scripts registered, by name. A config lives for the whole game session: scripts that run again (a reload) register the same
 * name again and get the same config with the schema they now declare; callbacks of the scripts that ran again are dropped first.
 */
public final class ConfigManager {
    private static final Map<String, ScriptConfig> CONFIGS = new LinkedHashMap<>();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("modjs");
    private static final java.util.ArrayDeque<String> NOTICES = new java.util.ArrayDeque<>();
    private static final long POLL_MS = 1000;
    private static long lastClientPoll;
    private static long lastServerPoll;

    private ConfigManager() {
    }

    /** {@code modjs/config}, where the files are. */
    public static Path directory() {
        return ScriptManager.root().resolve("config");
    }

    public static ScriptConfig register(String name, ScriptConfig.Side side, ConfigSpec.GroupSpec schema) {
        return register(name, side, schema, null);
    }

    /** Logs a plain line about configs and keeps the last few for tools and tests. */
    static void notice(String line) {
        LOGGER.warn("[config] {}", line);
        synchronized (NOTICES) {
            NOTICES.addLast(line);
            while (NOTICES.size() > 50) {
                NOTICES.removeFirst();
            }
        }
    }

    static void record(String line) {
        synchronized (NOTICES) {
            NOTICES.addLast(line);
            while (NOTICES.size() > 50) {
                NOTICES.removeFirst();
            }
        }
    }

    /** The most recent config log lines, oldest first. */
    public static List<String> notices() {
        synchronized (NOTICES) {
            return new ArrayList<>(NOTICES);
        }
    }

    /**
     * Registers (or registers again) a config and reads its file. Registering a name again with the same side keeps the same config
     * object (every script that holds it sees the change) and the latest schema wins; a line is logged when the entries differ from
     * the earlier registration. Registering a name with another side is refused with a log line and returns the existing config.
     */
    public static synchronized ScriptConfig register(String name, ScriptConfig.Side side, ConfigSpec.GroupSpec schema, ScriptType by) {
        ScriptConfig existing = CONFIGS.get(name);
        if (existing != null && existing.side != side) {
            notice("config '" + name + "' is already registered as a " + existing.side.name().toLowerCase(java.util.Locale.ROOT) + " config; the "
                + side.name().toLowerCase(java.util.Locale.ROOT) + " registration from " + (by == null ? "a script" : by.name().toLowerCase(java.util.Locale.ROOT) + " scripts") + " is ignored");
            return existing;
        }
        if (existing != null) {
            if (!existing.schema().signature().equals(schema.signature())) {
                notice("config '" + name + "' was registered again with different entries"
                    + (by == null ? "" : " by " + by.name().toLowerCase(java.util.Locale.ROOT) + " scripts") + "; the latest entries are used");
            }
            existing.define(schema);
            return existing;
        }
        // a client config has no file on a dedicated server: it holds its defaults and nothing else
        boolean hasFile = side != ScriptConfig.Side.CLIENT || new PlatformBinding().isClient();
        ScriptConfig created = new ScriptConfig(name, side, schema, hasFile ? directory().resolve(name + side.suffix + ".json5") : null);
        CONFIGS.put(name, created);
        created.load();
        return created;
    }

    /** A registered config, or null when no script has registered that name (yet). */
    public static synchronized ScriptConfig get(String name) {
        return CONFIGS.get(name);
    }

    public static synchronized List<String> names() {
        return new ArrayList<>(CONFIGS.keySet());
    }

    /** Reads the file of one config again. Returns false when no config has that name. */
    public static boolean reload(String name) {
        ScriptConfig config = get(name);
        if (config == null) {
            return false;
        }
        config.reload();
        return true;
    }

    /** Server and common configs, which {@code /modjs reload config} reaches. */
    public static synchronized List<ScriptConfig> serverSide() {
        List<ScriptConfig> out = new ArrayList<>();
        for (ScriptConfig c : CONFIGS.values()) {
            if (c.side != ScriptConfig.Side.CLIENT) {
                out.add(c);
            }
        }
        return out;
    }

    /** A script type is about to run its files again: its change callbacks belong to the old scope. */
    public static synchronized void scriptsReloading(ScriptType type) {
        CONFIGS.values().forEach(c -> c.dropListeners(type));
    }

    /** Forgets everything (tests). */
    public static synchronized void clear() {
        CONFIGS.clear();
        lastClientPoll = 0;
        lastServerPoll = 0;
    }

    /** Called every server tick: about once a second, reads changed files of server and common configs and runs server-side callbacks. */
    public static void tickServer() {
        tick(false);
    }

    /** Called every client tick, the same for client and common configs. */
    public static void tickClient() {
        tick(true);
    }

    /** Polls and delivers right now, without waiting for the one-second gap (tests and tools). */
    public static void pollNow(boolean client) {
        synchronized (ConfigManager.class) {
            if (client) {
                lastClientPoll = 0;
            } else {
                lastServerPoll = 0;
            }
        }
        tick(client);
    }

    private static void tick(boolean client) {
        long now = System.currentTimeMillis();
        List<ScriptConfig> all;
        synchronized (ConfigManager.class) {
            if (CONFIGS.isEmpty()) {
                return;
            }
            long last = client ? lastClientPoll : lastServerPoll;
            if (now - last < POLL_MS) {
                return;
            }
            if (client) {
                lastClientPoll = now;
            } else {
                lastServerPoll = now;
            }
            all = new ArrayList<>(CONFIGS.values());
        }
        for (ScriptConfig config : all) {
            boolean relevant = client ? config.side != ScriptConfig.Side.SERVER : config.side != ScriptConfig.Side.CLIENT;
            if (!relevant) {
                continue;
            }
            config.poll();
            if (client) {
                config.deliver(ScriptType.CLIENT);
            } else {
                config.deliver(ScriptType.SERVER);
                config.deliver(ScriptType.STARTUP);
            }
        }
    }
}
