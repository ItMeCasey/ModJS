package com.itmecasey.modjs.script;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/** Every script under examples/ must compile in the script engine's language level, so a tour never dies on a syntax error. */
class ExampleScriptsSyntaxTest {
    @Test
    void everyExampleScriptCompiles() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("examples"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            return;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir.resolve("examples"))) {
            files = walk.filter(p -> p.toString().endsWith(".js")).toList();
        }
        assertFalse(files.isEmpty());
        Context cx = ModJSContextFactory.INSTANCE.enterContext();
        try {
            for (Path file : files) {
                try {
                    cx.compileString(Files.readString(file), file.getFileName().toString(), 1, null);
                } catch (RuntimeException e) {
                    fail(file + ": " + e.getMessage());
                }
            }
        } finally {
            Context.exit();
        }
    }
}
