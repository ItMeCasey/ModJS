package com.itmecasey.modjs.unify;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Works out a material's color from the textures already in the game and its mods, so no color list is needed: for tin it
 * looks for {@code tin_ingot}, {@code tin_nugget}, {@code raw_tin}, {@code tin_dust}... in every mod's item and block textures
 * and takes the base tone of the first one it finds.
 */
public final class ColorSampler {
    /** Finds a texture by path such as {@code textures/item/tin_ingot.png} in any loaded mod or the game; null if absent. */
    public interface Finder extends Function<String, BufferedImage> {
    }

    private static volatile Finder finder = path -> null;
    private static final Map<String, Integer> CACHE = new HashMap<>();

    private ColorSampler() {
    }

    public static synchronized void setFinder(Finder value) {
        finder = value;
        CACHE.clear();
    }

    /** A finder over the given roots, each holding an {@code assets/<namespace>/...} tree (a mod file's root, for example). */
    public static Finder overRoots(List<Path> roots, List<String> namespaces) {
        return path -> {
            for (Path root : roots) {
                for (String namespace : namespaces) {
                    Path file = root.resolve("assets").resolve(namespace).resolve(path);
                    if (Files.isRegularFile(file)) {
                        try (InputStream in = Files.newInputStream(file)) {
                            return javax.imageio.ImageIO.read(in);
                        } catch (IOException e) {
                            // try the next one
                        }
                    }
                }
            }
            return null;
        };
    }

    /** A finder that reads the game's own textures from the class path. */
    public static Finder classPath() {
        return path -> {
            for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), ColorSampler.class.getClassLoader()}) {
                if (loader == null) {
                    continue;
                }
                try (InputStream in = loader.getResourceAsStream("assets/minecraft/" + path)) {
                    if (in != null) {
                        return javax.imageio.ImageIO.read(in);
                    }
                } catch (IOException e) {
                    // try the next loader
                }
            }
            return null;
        };
    }

    /** Texture file names, most telling first, that a material's pieces are usually called. */
    static List<String> candidates(String material) {
        List<String> names = new ArrayList<>();
        for (String kind : List.of("ingot", "gem", "dust", "nugget", "plate", "raw", "ore", "block")) {
            switch (kind) {
                case "raw" -> names.add("item/raw_" + material);
                case "ore" -> names.add("block/" + material + "_ore");
                case "block" -> names.add("block/" + material + "_block");
                case "gem" -> {
                    names.add("item/" + material);
                    names.add("item/" + material + "_gem");
                    names.add("item/gem_" + material);
                }
                default -> {
                    names.add("item/" + material + "_" + kind);
                    names.add("item/" + kind + "_" + material);
                }
            }
        }
        return names;
    }

    /** The sampled color for a material (24-bit RGB), or null when no texture was found. */
    public static synchronized Integer sample(String material) {
        if (CACHE.containsKey(material)) {
            return CACHE.get(material);
        }
        Integer color = null;
        for (String name : candidates(material)) {
            BufferedImage image = finder.apply("textures/" + name + ".png");
            if (image != null) {
                color = baseTone(image);
                if (color != null) {
                    break;
                }
            }
        }
        CACHE.put(material, color);
        return color;
    }

    /** The middle tone of a texture: the average of the pixels between its darkest quarter and brightest quarter. */
    static Integer baseTone(BufferedImage image) {
        List<int[]> pixels = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) > 200) {
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    pixels.add(new int[] {r, g, b, (int) (0.299 * r + 0.587 * g + 0.114 * b)});
                }
            }
        }
        if (pixels.size() < 4) {
            return null;
        }
        pixels.sort((a, b) -> Integer.compare(a[3], b[3]));
        int from = pixels.size() / 4;
        int to = Math.max(from + 1, pixels.size() * 3 / 4);
        long r = 0;
        long g = 0;
        long b = 0;
        for (int i = from; i < to; i++) {
            r += pixels.get(i)[0];
            g += pixels.get(i)[1];
            b += pixels.get(i)[2];
        }
        int n = to - from;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }
}
