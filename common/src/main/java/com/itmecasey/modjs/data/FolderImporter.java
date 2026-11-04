package com.itmecasey.modjs.data;

import com.itmecasey.modjs.pack.GeneratedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Copies real files into the generated packs so scripters can drop textures and JSON next to their scripts:
 * {@code modjs/assets/<namespace>/...} and {@code modjs/data/<namespace>/...}.
 */
public final class FolderImporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private FolderImporter() {
    }

    public static int importInto(Path root, GeneratedData data) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> namespaces = Files.list(root)) {
            for (Path namespaceDir : (Iterable<Path>) namespaces.filter(Files::isDirectory)::iterator) {
                String namespace = namespaceDir.getFileName().toString();
                try (Stream<Path> files = Files.walk(namespaceDir)) {
                    for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                        String relative = namespaceDir.relativize(file).toString().replace('\\', '/');
                        data.putBytes(namespace, relative, Files.readAllBytes(file));
                        count++;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not import files from {}", root, e);
        }
        return count;
    }
}
