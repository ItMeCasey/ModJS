package com.itmecasey.modjs.probe;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the real script-facing classes (events and builders) and writes {@code api.d.ts}. The hand-written {@code modjs.d.ts}
 * stays the source of docs and exact signatures; this file adds every public method that class has and the hand-written
 * interface lacks, and whole interfaces for classes it does not describe at all. Interfaces with the same name merge in
 * TypeScript, so nothing already declared is changed.
 */
public final class ApiReflector {
    private static final Pattern INTERFACE = Pattern.compile("^(?:export\\s+)?interface\\s+(\\w+)[^{]*\\{", Pattern.MULTILINE);
    private static final Pattern MEMBER = Pattern.compile("^\\s{1,4}(?:readonly\\s+)?(\\w+)\\s*[?(<:]", Pattern.MULTILINE);

    private ApiReflector() {
    }

    /** Interface name to the member names it already declares in the hand-written declarations. */
    public static Map<String, Set<String>> declaredMembers(String curated) {
        Map<String, Set<String>> result = new HashMap<>();
        Matcher start = INTERFACE.matcher(curated);
        while (start.find()) {
            int depth = 1;
            int i = start.end();
            while (i < curated.length() && depth > 0) {
                char c = curated.charAt(i++);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            }
            String body = curated.substring(start.end(), Math.max(start.end(), i - 1));
            Set<String> members = result.computeIfAbsent(start.group(1), k -> new HashSet<>());
            Matcher member = MEMBER.matcher(body);
            while (member.find()) {
                members.add(member.group(1));
            }
        }
        return result;
    }

    /** The script-facing name of a Java class: {@code RecipesEventJS} is {@code RecipesEvent}. */
    public static String scriptName(Class<?> type) {
        String name = type.getSimpleName();
        return name.endsWith("JS") ? name.substring(0, name.length() - 2) : name;
    }

    public static boolean isScriptFacing(Class<?> type) {
        String name = type.getSimpleName();
        return !type.isAnonymousClass() && !type.isInterface() && Modifier.isPublic(type.getModifiers())
            && (name.endsWith("EventJS") || name.endsWith("BuilderJS"));
    }

    /** Maps a Java type to a TypeScript type; classes outside {@code known} become {@code any}. */
    static String tsType(Class<?> type, Set<String> known) {
        if (type == void.class || type == Void.class) {
            return "void";
        }
        if (type == String.class || type == CharSequence.class || type == char.class || type == Character.class) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isPrimitive() || Number.class.isAssignableFrom(type)) {
            return "number";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "any[]";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "Record<string, any>";
        }
        if (type == Runnable.class || type == java.util.function.Function.class || type == java.util.function.Consumer.class
            || type == java.util.function.Supplier.class || type == java.util.function.Predicate.class
            || type == org.mozilla.javascript.Function.class || type == org.mozilla.javascript.Callable.class) {
            return "(...args: any[]) => any";
        }
        String name = scriptName(type);
        return known.contains(name) ? name : "any";
    }

    private static String method(Method method, Set<String> known) {
        StringBuilder out = new StringBuilder("  ").append(method.getName()).append('(');
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].isNamePresent() ? parameters[i].getName() : "arg" + i;
            if (isReserved(name)) {
                name = name + "_";
            }
            out.append(i == 0 ? "" : ", ").append(name).append(": ").append(tsType(parameters[i].getType(), known));
        }
        return out.append("): ").append(tsType(method.getReturnType(), known)).append(';').toString();
    }

    private static boolean isReserved(String name) {
        return Set.of("function", "var", "let", "class", "default", "new", "delete", "in", "typeof", "with", "this", "enum", "export")
            .contains(name);
    }

    /** Builds the {@code api.d.ts} text. {@code curated} is the text of the hand-written {@code modjs.d.ts}. */
    public static String generate(String curated, Collection<Class<?>> classes) {
        Map<String, Set<String>> declared = declaredMembers(curated);
        Set<String> known = new HashSet<>(declared.keySet());
        for (Class<?> type : classes) {
            known.add(scriptName(type));
        }
        Map<String, TreeSet<String>> members = new TreeMap<>();
        Map<String, Boolean> isEvent = new HashMap<>();
        for (Class<?> type : classes) {
            String name = scriptName(type);
            Set<String> have = declared.getOrDefault(name, Set.of());
            TreeSet<String> lines = members.computeIfAbsent(name, k -> new TreeSet<>());
            isEvent.put(name, type.getSimpleName().endsWith("EventJS"));
            Method[] methods;
            try {
                methods = type.getMethods();
            } catch (Throwable e) {
                continue;
            }
            for (Method method : methods) {
                if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic() || method.isBridge()
                    || method.getDeclaringClass() == Object.class || have.contains(method.getName())
                    || method.getName().startsWith("lambda$")
                    || method.getDeclaringClass().getName().startsWith("org.mozilla")
                    || method.getDeclaringClass().getName().startsWith("java.")) {
                    continue;
                }
                lines.add(method(method, known));
            }
        }
        StringBuilder out = new StringBuilder(Typings.HEADER);
        out.append("// Reflected from the ModJS classes that scripts can reach. Interfaces here merge with modjs.d.ts.\n\n");
        for (Map.Entry<String, TreeSet<String>> entry : members.entrySet()) {
            boolean exists = declared.containsKey(entry.getKey());
            if (entry.getValue().isEmpty() && exists) {
                continue;
            }
            out.append("interface ").append(entry.getKey());
            if (!exists && isEvent.get(entry.getKey()) && declared.containsKey("EventBase")) {
                out.append(" extends EventBase");
            }
            out.append(" {\n");
            for (String line : entry.getValue()) {
                out.append(line).append('\n');
            }
            out.append("}\n");
        }
        return out.toString();
    }

    /** Finds every script-facing class in the jar or folder that holds {@code anchor}; empty when the code cannot be listed. */
    public static List<Class<?>> scan(Class<?> anchor) {
        List<Class<?>> found = new ArrayList<>();
        try {
            String packagePath = anchor.getPackageName().replace('.', '/');
            URI uri = anchor.getResource("/" + packagePath + "/").toURI();
            Path resolved;
            try {
                resolved = Path.of(uri);
            } catch (FileSystemNotFoundException e) {
                resolved = FileSystems.newFileSystem(uri, Map.of()).getPath("/" + packagePath);
            }
            Path base = resolved;
            List<String> names = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(base)) {
                walk.forEach(path -> {
                    String file = path.getFileName() == null ? "" : path.getFileName().toString();
                    if (file.endsWith("EventJS.class") || file.endsWith("BuilderJS.class")) {
                        String relative = base.relativize(path).toString().replace('\\', '/').replace('/', '.');
                        names.add(anchor.getPackageName() + "." + relative.substring(0, relative.length() - ".class".length()));
                    }
                });
            }
            for (String name : names) {
                try {
                    Class<?> type = Class.forName(name, false, anchor.getClassLoader());
                    if (isScriptFacing(type)) {
                        found.add(type);
                    }
                } catch (Throwable ignored) {
                    // A class that needs client-only code cannot load on a dedicated server; it is left out.
                }
            }
        } catch (Throwable e) {
            return found;
        }
        return found;
    }
}
