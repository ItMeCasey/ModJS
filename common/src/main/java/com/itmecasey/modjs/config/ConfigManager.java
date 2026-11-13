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
    private static final long POLL_MS = 1000;
    private static long lastClientPoll;
    private static long lastServerPoll;

    private ConfigManager() {
    }

    /** {@code modjs/config}, where the files are. */
    public static Path directory() {
        return ScriptManager.root().resolve("config");
    }

    /** Registers (or registers again) a config and reads its file. */
    public static synchronized ScriptConfig register(String name, ScriptConfig.Side side, ConfigSpec.GroupSpec schema) {
        ScriptConfig existing = CONFIGS.get(name);
        if (existing != null && existing.side == side) {
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
