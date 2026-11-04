package com.itmecasey.modjs.unify;

import java.awt.image.BufferedImage;

/** Public entry to the recoloring used for generated art. */
public final class ArtStyleAccess {
    private ArtStyleAccess() {
    }

    /** Recolors a vanilla texture through the five-tone ramp of {@code rgb}. */
    public static BufferedImage recolor(BufferedImage source, int rgb) {
        return ArtStyle.recolor(source, rgb, false, false);
    }
}
