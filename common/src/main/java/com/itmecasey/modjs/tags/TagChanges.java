package com.itmecasey.modjs.tags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tag entries scripts asked to remove. Keyed by registry, then tag id. */
public final class TagChanges {
    private static volatile TagChanges current = new TagChanges();

    private final Map<String, Map<String, Set<String>>> removals = new HashMap<>();

    public static TagChanges current() {
        return current;
    }

    public static void reset() {
        current = new TagChanges();
    }

    synchronized void remove(String registry, String tag, String entry) {
        removals.computeIfAbsent(registry, k -> new HashMap<>()).computeIfAbsent(tag, k -> new HashSet<>()).add(entry);
    }

    public synchronized boolean isEmpty() {
        return removals.isEmpty();
    }

    /** Entries to drop from {@code tag} in {@code registry}; entries are ids or {@code #tag} references. */
    public synchronized Set<String> removalsFor(String registry, String tag) {
        Map<String, Set<String>> perTag = removals.get(registry);
        if (perTag == null) {
            return Set.of();
        }
        return perTag.getOrDefault(tag, Set.of());
    }

    public synchronized Set<String> registries() {
        return new HashSet<>(removals.keySet());
    }
}
