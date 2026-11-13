package com.itmecasey.modjs.probe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes files under {@code modjs/probe/} only: each write goes to a temporary file first and is moved into place. */
public final class ProbeWriter {
    private final Path root;

    public ProbeWriter(Path probeDir) {
        this.root = probeDir.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Resolves a relative path and refuses anything that would leave the probe folder. */
    public Path resolve(String relative) throws IOException {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Refusing to write outside the probe folder: " + relative);
        }
        return target;
    }

    public void write(String relative, String content) throws IOException {
        Path target = resolve(relative);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
