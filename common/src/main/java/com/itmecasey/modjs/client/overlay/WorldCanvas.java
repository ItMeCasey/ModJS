package com.itmecasey.modjs.client.overlay;

/**
 * What world overlays draw with (one small adapter per Minecraft version). After {@link #push} the drawing frame has x to the
 * right, y up and the front of the element facing +z (toward whoever looks at it); sizes are in blocks, and everything is drawn
 * fullbright. The adapter hides the engine's buffer or submit-collector details.
 */
public interface WorldCanvas {
    /**
     * Starts an element at a world position. {@code face} is {@code billboard} (turns to the camera) or the side a flat element
     * lies on: {@code up}, {@code down}, {@code north}, {@code south}, {@code east}, {@code west}. Positions are absolute world
     * coordinates; the adapter subtracts the camera.
     */
    void push(double x, double y, double z, String face);

    void pop();

    /** Text centered on the origin; {@code lineHeight} is the height of one text line in blocks. */
    void text(String text, double lineHeight, int argb, boolean shadow, int backgroundArgb);

    /** Width of the text in blocks for a line height, used for bars and backgrounds that follow the text. */
    double textWidth(String text, double lineHeight);

    /** An item icon centered on the origin, about {@code size} blocks tall. */
    void item(String itemId, double size);

    /** A filled rectangle; {@code (left, bottom)} is its lower left corner relative to the origin. */
    void quad(double left, double bottom, double width, double height, int argb);

    /** A texture stretched over a rectangle centered on the origin, multiplied by the color. */
    void texture(String texture, double width, double height, int argb);
}
