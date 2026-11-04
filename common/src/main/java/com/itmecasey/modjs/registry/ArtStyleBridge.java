package com.itmecasey.modjs.registry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** Lets the registry package use the unify package's recoloring without making that class public. */
final class ArtStyleBridge {
    private ArtStyleBridge() {
    }

    static BufferedImage load(String resource) {
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), ArtStyleBridge.class.getClassLoader()}) {
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

    static BufferedImage recolor(BufferedImage source, int rgb) {
        return com.itmecasey.modjs.unify.ArtStyleAccess.recolor(source, rgb);
    }
}
