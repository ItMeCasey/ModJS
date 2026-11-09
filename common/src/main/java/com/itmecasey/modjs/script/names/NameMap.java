package com.itmecasey.modjs.script.names;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Translates Minecraft's official member names (what scripts and this mod's source use) into the names a real Minecraft 1.20.1
 * game has (Forge's SRG names such as {@code m_82127_} and {@code f_42415_}).
 *
 * <p>A real 1.20.1 game only knows the second kind, a development game only the first. The table is the resource
 * {@code modjs/names.txt.gz}, made from the build's own mappings by {@code scripts/gen_name_map.py}; builds for versions that need no
 * translation do not ship it, and then {@link #enabled()} is false and nothing here costs anything.
 *
 * <p>Format, one entry per line: {@code #binary.class.Name} starts a class, then {@code m<TAB>official<TAB>srg} for a method and
 * {@code f<TAB>official<TAB>srg} for a field. Overloaded methods have several lines.
 */
public final class NameMap {
    public static final String RESOURCE = "modjs/names.txt.gz";

    private static volatile NameMap instance;
    private static volatile boolean loaded;

    private final Map<String, Map<String, List<String>>> methods = new HashMap<>();
    private final Map<String, Map<String, List<String>>> fields = new HashMap<>();
    private final Map<Class<?>, List<Class<?>>> hierarchies = new ConcurrentHashMap<>();

    private NameMap() {
    }

    /** The table of this build, or null when the build ships none. */
    public static NameMap get() {
        if (!loaded) {
            synchronized (NameMap.class) {
                if (!loaded) {
                    instance = load(NameMap.class.getClassLoader());
                    loaded = true;
                }
            }
        }
        return instance;
    }

    /** A script-visible class object: one that knows the official names too when this build ships a table. */
    public static org.mozilla.javascript.NativeJavaClass classObject(org.mozilla.javascript.Scriptable scope, Class<?> type) {
        NameMap map = get();
        return map == null ? new org.mozilla.javascript.NativeJavaClass(scope, type) : new MappedJavaClass(map, scope, type);
    }

    public static boolean enabled() {
        return get() != null;
    }

    static NameMap load(ClassLoader loader) {
        try (InputStream raw = loader.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                return null;
            }
            NameMap map = new NameMap();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
                map.parse(reader);
            }
            return map;
        } catch (java.io.IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("modjs").warn("ModJS could not read the name table {}: {}", RESOURCE, e.toString());
            return null;
        }
    }

    /** For tests: a table from text in the resource format. */
    public static NameMap parse(String text) {
        NameMap map = new NameMap();
        try {
            map.parse(new BufferedReader(new java.io.StringReader(text)));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return map;
    }

    private void parse(BufferedReader reader) throws java.io.IOException {
        Map<String, List<String>> classMethods = null;
        Map<String, List<String>> classFields = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) == '#') {
                String name = line.substring(1);
                classMethods = methods.computeIfAbsent(name, k -> new HashMap<>());
                classFields = fields.computeIfAbsent(name, k -> new HashMap<>());
                continue;
            }
            int first = line.indexOf('\t');
            int second = line.indexOf('\t', first + 1);
            if (first != 1 || second < 0 || classMethods == null) {
                continue;
            }
            String official = line.substring(2, second);
            String srg = line.substring(second + 1);
            Map<String, List<String>> target = line.charAt(0) == 'm' ? classMethods : classFields;
            List<String> list = target.computeIfAbsent(official, k -> new ArrayList<>(1));
            if (!list.contains(srg)) {
                list.add(srg);
            }
        }
    }

    /** The class, its superclasses and every interface, nearest first. */
    private List<Class<?>> hierarchy(Class<?> type) {
        return hierarchies.computeIfAbsent(type, t -> {
            List<Class<?>> out = new ArrayList<>();
            Set<Class<?>> seen = new HashSet<>();
            ArrayDeque<Class<?>> queue = new ArrayDeque<>();
            queue.add(t);
            while (!queue.isEmpty()) {
                Class<?> c = queue.poll();
                if (c == null || !seen.add(c) || c == Object.class) {
                    continue;
                }
                out.add(c);
                if (c.getSuperclass() != null) {
                    queue.add(c.getSuperclass());
                }
                queue.addAll(java.util.Arrays.asList(c.getInterfaces()));
            }
            return out;
        });
    }

    /** Real-game names for the method with this official name, looked up through the class and everything it extends. */
    public List<String> methodNames(Class<?> type, String official) {
        return lookup(methods, type, official);
    }

    public List<String> fieldNames(Class<?> type, String official) {
        return lookup(fields, type, official);
    }

    private List<String> lookup(Map<String, Map<String, List<String>>> table, Class<?> type, String official) {
        List<String> found = null;
        for (Class<?> c : hierarchy(type)) {
            Map<String, List<String>> inClass = table.get(c.getName());
            if (inClass == null) {
                continue;
            }
            List<String> names = inClass.get(official);
            if (names == null) {
                continue;
            }
            if (found == null) {
                found = new ArrayList<>(names);
            } else {
                for (String n : names) {
                    if (!found.contains(n)) {
                        found.add(n);
                    }
                }
            }
        }
        return found == null ? Collections.emptyList() : found;
    }

    // -- for code that looks members up by name with reflection --------------------------------------------------------------

    /** A public method by its official name, or by its real-game name when the game only has that. */
    public static java.lang.reflect.Method method(Class<?> type, String official, Class<?>... parameters) throws NoSuchMethodException {
        try {
            return type.getMethod(official, parameters);
        } catch (NoSuchMethodException first) {
            NameMap map = get();
            if (map != null) {
                for (String srg : map.methodNames(type, official)) {
                    try {
                        return type.getMethod(srg, parameters);
                    } catch (NoSuchMethodException ignored) {
                        // try the next name
                    }
                }
            }
            throw first;
        }
    }

    /** A declared field of exactly this class by its official or real-game name, or null. */
    public static java.lang.reflect.Field declaredField(Class<?> type, String official) {
        try {
            return type.getDeclaredField(official);
        } catch (NoSuchFieldException first) {
            NameMap map = get();
            if (map != null) {
                for (String srg : map.fieldNames(type, official)) {
                    try {
                        return type.getDeclaredField(srg);
                    } catch (NoSuchFieldException ignored) {
                        // try the next name
                    }
                }
            }
            return null;
        }
    }

    /** True when a method called {@code actual} is the one scripts know as {@code official} in this class. */
    public static boolean sameMethodName(Class<?> type, String actual, String official) {
        if (actual.equals(official)) {
            return true;
        }
        NameMap map = get();
        return map != null && map.methodNames(type, official).contains(actual);
    }

    /** Number of classes in the table (for tests and logs). */
    public int classCount() {
        return methods.size();
    }
}
