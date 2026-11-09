package com.itmecasey.modjs.client.overlay;

/**
 * What the overlay renderer needs from the game: a few drawing calls in scaled GUI pixels with ARGB colors. Each version module
 * implements this on its own graphics class, so everything else (layout, animation, colors) lives in shared code.
 */
public interface OverlayCanvas {
    int width();

    int height();

    void fill(int x, int y, int width, int height, int argb);

    /** Draws text with its top left corner at x, y. {@code scale} 1 is the normal font size. */
    void text(String text, int x, int y, int argb, boolean shadow, float scale);

    /** Width of the text at scale 1, in GUI pixels. */
    int textWidth(String text);

    int fontHeight();

    /** Draws a region (u, v, regionWidth, regionHeight) of a texture of the given size, stretched to width x height. */
    void texture(String texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight,
                 int textureWidth, int textureHeight, int argb);

    /** Draws an item icon (16 pixels at scale 1) with its top left corner at x, y. */
    void item(String itemId, int x, int y, float scale);
}
