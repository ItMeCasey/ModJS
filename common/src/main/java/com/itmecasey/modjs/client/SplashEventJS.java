package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The event passed to {@code ClientEvents.splashes(event => ...)}: the yellow text on the title screen. Starts with the
 * game's own list; the result is written as a resource pack file that replaces {@code texts/splashes.txt}.
 */
public final class SplashEventJS extends EventJS {
    private final List<String> lines;

    public SplashEventJS(List<String> vanilla) {
        this.lines = new ArrayList<>(vanilla);
    }

    /** The game's own splash list, read from the game jar; empty if it cannot be found. */
    public static List<String> readVanilla() {
        List<String> out = new ArrayList<>();
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), SplashEventJS.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream("assets/minecraft/texts/splashes.txt")) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            out.add(line.strip());
                        }
                    }
                    return out;
                }
            } catch (IOException e) {
                // try the next loader
            }
        }
        return out;
    }

    /** Adds splash lines, for example {@code add("Hello!", "Welcome to the server")}. */
    public void add(String... splashes) {
        for (String splash : splashes) {
            if (!splash.isBlank()) {
                lines.add(splash.strip());
            }
        }
    }

    /** Removes every splash that contains the text (case-insensitive). */
    public void remove(String text) {
        lines.removeIf(line -> line.toLowerCase().contains(text.toLowerCase()));
    }

    /** Removes all splashes, including the game's. */
    public void clear() {
        lines.clear();
    }

    /** Replaces the whole list. */
    public void set(String... splashes) {
        lines.clear();
        add(splashes);
    }

    public List<String> getLines() {
        return lines;
    }

    /** The file contents: one splash per line. */
    public String toFile() {
        return String.join("\n", lines.isEmpty() ? List.of("") : lines) + "\n";
    }
}
