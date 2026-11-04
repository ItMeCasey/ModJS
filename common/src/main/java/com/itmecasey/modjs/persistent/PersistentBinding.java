package com.itmecasey.modjs.persistent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code Persistent} global. {@code Persistent.get/set/remove} use the default store; {@code Persistent.store('name')}
 * gives a separate named store.
 */
public final class PersistentBinding {
    private static final Map<String, PersistentStore> STORES = new ConcurrentHashMap<>();

    public PersistentStore store(String name) {
        if (!name.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Store names use letters, digits, _ and -");
        }
        return STORES.computeIfAbsent(name, PersistentStore::new);
    }

    private PersistentStore main() {
        return store("main");
    }

    public Object get(String key) {
        return main().get(key);
    }

    public Object get(String key, Object fallback) {
        return main().get(key, fallback);
    }

    public void set(String key, Object value) {
        main().set(key, value);
    }

    public boolean has(String key) {
        return main().has(key);
    }

    public void remove(String key) {
        main().remove(key);
    }

    /** Forgets cached stores so edits made on disk are picked up; called when scripts reload. */
    public static void resetCache() {
        STORES.clear();
    }
}
