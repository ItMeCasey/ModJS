package com.itmecasey.modjs.unify;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Makes material art look like Minecraft's own: real vanilla textures recolored through a five-step ramp (so the pixel art,
 * outline and shading stay vanilla), plus a few shaped sprites for things vanilla has no picture of.
 */
final class ArtStyle {
    private ArtStyle() {
    }

    /** Reads a vanilla texture such as {@code item/iron_ingot} from the game assets, or null when they are not on the class path. */
    static BufferedImage vanilla(String path) {
        String resource = "assets/minecraft/textures/" + path + ".png";
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), ArtStyle.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in != null) {
                    return javax.imageio.ImageIO.read(in);
                }
            } catch (IOException e) {
                // try the next loader
            }
        }
        return null;
    }

    private static int mix(int rgb, int target, double amount) {
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * (1 - amount) + ((target >> 16) & 0xFF) * amount);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * (1 - amount) + ((target >> 8) & 0xFF) * amount);
        int b = (int) Math.round((rgb & 0xFF) * (1 - amount) + (target & 0xFF) * amount);
        return (r << 16) | (g << 8) | b;
    }

    /** Outline, shadow, base, light and highlight tones for a material color. */
    static int[] ramp(int rgb) {
        return new int[] {mix(rgb, 0x000000, 0.72), mix(rgb, 0x000000, 0.42), rgb & 0xFFFFFF, mix(rgb, 0xFFFFFF, 0.35), mix(rgb, 0xFFFFFF, 0.70)};
    }

    private static double luminance(int argb) {
        return (0.299 * ((argb >> 16) & 0xFF) + 0.587 * ((argb >> 8) & 0xFF) + 0.114 * (argb & 0xFF)) / 255.0;
    }

    private static boolean colorful(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 14;
    }

    /**
     * Recolors a vanilla texture: each pixel's brightness picks one of the five ramp tones. With {@code specksOnly} only colorful
     * pixels change, which keeps the grey stone of an ore. {@code dirty} blends the result toward brown.
     */
    static BufferedImage recolor(BufferedImage source, int rgb, boolean specksOnly, boolean dirty) {
        int[] ramp = ramp(rgb);
        int width = source.getWidth();
        int height = source.getHeight();
        double min = 1;
        double max = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                if ((argb >>> 24) == 0 || (specksOnly && !colorful(argb))) {
                    continue;
                }
                double l = luminance(argb);
                min = Math.min(min, l);
                max = Math.max(max, l);
            }
        }
        double range = Math.max(0.05, max - min);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = source.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                if (specksOnly && !colorful(argb)) {
                    out.setRGB(x, y, argb);
                    continue;
                }
                int step = (int) Math.round(Math.max(0, Math.min(1, (luminance(argb) - min) / range)) * 4);
                int tone = ramp[step];
                if (dirty) {
                    tone = mix(tone, 0x5E4A38, 0.45);
                }
                out.setRGB(x, y, (alpha << 24) | tone);
            }
        }
        return out;
    }

    /** A flat sprite from a shape: dark outline, light on the top-left edge, shadow on the bottom-right edge. */
    static BufferedImage shaped(boolean[][] mask, int rgb) {
        int[] ramp = ramp(rgb);
        int size = mask.length;
        boolean[][] outline = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                outline[y][x] = mask[y][x] && (y == 0 || x == 0 || y == size - 1 || x == size - 1
                    || !mask[y - 1][x] || !mask[y + 1][x] || !mask[y][x - 1] || !mask[y][x + 1]);
            }
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!mask[y][x]) {
                    continue;
                }
                int tone;
                if (outline[y][x]) {
                    tone = ramp[0];
                } else if (outline[y - 1][x] || outline[y][x - 1]) {
                    tone = ramp[4];
                } else if (outline[y + 1][x] || outline[y][x + 1]) {
                    tone = ramp[1];
                } else {
                    tone = ((x + y) % 4 == 0) ? ramp[3] : ramp[2];
                }
                out.setRGB(x, y, 0xFF000000 | tone);
            }
        }
        return out;
    }

    /** A filled circle; edge pixels get the outline. */
    static boolean[][] disc(double radius) {
        boolean[][] mask = new boolean[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                mask[y][x] = Math.hypot(x - 7.5, y - 7.5) <= radius;
            }
        }
        return mask;
    }

    static boolean[][] plate() {
        boolean[][] mask = new boolean[16][16];
        for (int y = 3; y <= 12; y++) {
            for (int x = 2; x <= 13; x++) {
                mask[y][x] = true;
            }
        }
        mask[3][2] = false;
        mask[3][13] = false;
        mask[12][2] = false;
        mask[12][13] = false;
        return mask;
    }

    /** An eight-tooth cog with a round hole. */
    static boolean[][] gear() {
        boolean[][] mask = new boolean[16][16];
        double teeth = 8;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x - 7.5;
                double dy = y - 7.5;
                double d = Math.hypot(dx, dy);
                double phase = (Math.atan2(dy, dx) / (2 * Math.PI) * teeth) % 1.0;
                if (phase < 0) {
                    phase += 1;
                }
                boolean tooth = d <= 7.6 && Math.abs(phase - 0.5) <= 0.3;
                mask[y][x] = d >= 2.0 && (d <= 5.8 || tooth);
            }
        }
        return mask;
    }
}
