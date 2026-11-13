package com.itmecasey.modjs.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * How ModJS writes files in the user's {@code modjs} folder: always through a temporary file next to the target, so a crash never leaves a
 * truncated file, and never over a file that was not written by ModJS itself. Nothing here deletes a user file.
 */
public final class SafeFiles {
    private SafeFiles() {
    }

    private static Path temp(Path target) {
        return target.resolveSibling("." + target.getFileName() + ".tmp-" + Long.toHexString(System.nanoTime()));
    }

    /**
     * Creates the file with these bytes only when nothing exists at that path. Returns false (and touches nothing) when something is
     * there already, whatever it is.
     */
    public static boolean createIfAbsent(Path target, byte[] bytes) throws IOException {
        if (Files.exists(target)) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path temp = temp(target);
        try {
            Files.write(temp, bytes);
            // a move without REPLACE_EXISTING fails when the target appeared in the meantime: the other file wins
            Files.move(temp, target);
            return true;
        } catch (FileAlreadyExistsException e) {
            return false;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static boolean createIfAbsent(Path target, String text) throws IOException {
        return createIfAbsent(target, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Replaces (or creates) a file written by ModJS or by a script that asked for it: through a temporary file and an atomic move, so
     * readers see the old or the new content and never a partial file.
     */
    public static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = temp(target);
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void writeAtomic(Path target, String text) throws IOException {
        writeAtomic(target, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Like {@link #writeAtomic} but leaves the file alone when it already has exactly these bytes. Returns whether it wrote. */
    public static boolean writeIfChanged(Path target, byte[] bytes) throws IOException {
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) {
            return false;
        }
        writeAtomic(target, bytes);
        return true;
    }

    /**
     * Copies an existing file to {@code <name>.bak}, or {@code .bak-2}, {@code .bak-3} and so on when that name is taken, never replacing
     * a backup. Returns the backup path.
     */
    public static Path backup(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        for (int n = 1; ; n++) {
            Path candidate = file.resolveSibling(file.getFileName() + (n == 1 ? ".bak" : ".bak-" + n));
            if (createIfAbsent(candidate, bytes)) {
                return candidate;
            }
        }
    }
}
