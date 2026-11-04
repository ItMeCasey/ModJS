package com.itmecasey.modjs.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** In-memory files served as a virtual pack. Keys are {@code namespace/path} (path includes the extension). */
public final class GeneratedData {
    public static final GeneratedData SERVER = new GeneratedData();
    public static final GeneratedData CLIENT = new GeneratedData();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, byte[]> files = Collections.synchronizedMap(new TreeMap<>());

    public void clear() {
        files.clear();
    }

    public void putJson(String namespace, String path, JsonElement json) {
        putText(namespace, path + ".json", GSON.toJson(json));
    }

    public void putText(String namespace, String path, String text) {
        files.put(namespace + "/" + path, text.getBytes(StandardCharsets.UTF_8));
    }

    public void putBytes(String namespace, String path, byte[] bytes) {
        files.put(namespace + "/" + path, bytes);
    }

    public byte[] get(String namespace, String path) {
        return files.get(namespace + "/" + path);
    }

    public boolean has(String namespace, String path) {
        return files.containsKey(namespace + "/" + path);
    }

    /** Removes a file; returns true if it existed. {@code path} includes the extension. */
    public boolean remove(String namespace, String path) {
        return files.remove(namespace + "/" + path) != null;
    }

    public int size() {
        return files.size();
    }

    public Set<String> namespaces() {
        Set<String> out = new TreeSet<>();
        synchronized (files) {
            for (String key : files.keySet()) {
                out.add(key.substring(0, key.indexOf('/')));
            }
        }
        return out;
    }

    /** Calls {@code consumer} with the path (without namespace) of every file in {@code namespace} under {@code prefix}. */
    public void list(String namespace, String prefix, java.util.function.BiConsumer<String, byte[]> consumer) {
        String start = namespace + "/" + prefix + "/";
        Map<String, byte[]> snapshot;
        synchronized (files) {
            snapshot = new TreeMap<>(files);
        }
        for (Map.Entry<String, byte[]> entry : snapshot.entrySet()) {
            if (entry.getKey().startsWith(start)) {
                consumer.accept(entry.getKey().substring(namespace.length() + 1), entry.getValue());
            }
        }
    }
}
