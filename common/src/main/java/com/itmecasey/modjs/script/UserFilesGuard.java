package com.itmecasey.modjs.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Checks that a routine leaves a folder's existing files alone: the headless self test runs the startup routines through it against the
 * real {@code modjs} folder. Files ModJS owns ({@code probe/}) and files the test itself writes are skipped by the caller's prefix list.
 */
public final class UserFilesGuard {
    private static final long LIMIT = 8L * 1024 * 1024;

    private UserFilesGuard() {
    }

    private static Map<String, byte[]> snapshot(Path root, String[] ignoredPrefixes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String name = root.relativize(file).toString().replace('\\', '/');
                boolean skipped = false;
                for (String prefix : ignoredPrefixes) {
                    skipped |= name.startsWith(prefix);
                }
                if (!skipped && Files.size(file) <= LIMIT) {
                    files.put(name, Files.readAllBytes(file));
                }
            }
        }
        return files;
    }

    /** Runs the routine; returns an empty text when every file that existed before is still there with the same bytes, else what changed. */
    public static String run(Path root, Runnable routine, String... ignoredPrefixes) {
        try {
            Map<String, byte[]> before = snapshot(root, ignoredPrefixes);
            routine.run();
            Map<String, byte[]> after = snapshot(root, ignoredPrefixes);
            StringBuilder problems = new StringBuilder();
            for (Map.Entry<String, byte[]> entry : before.entrySet()) {
                byte[] now = after.get(entry.getKey());
                if (now == null) {
                    problems.append(" removed:").append(entry.getKey());
                } else if (!Arrays.equals(now, entry.getValue())) {
                    problems.append(" changed:").append(entry.getKey());
                }
            }
            return problems.toString().trim();
        } catch (IOException e) {
            return "could not compare: " + e.getMessage();
        }
    }
}
