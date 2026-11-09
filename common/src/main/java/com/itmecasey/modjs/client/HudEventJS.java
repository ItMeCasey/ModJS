package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;
import java.util.ArrayList;
import java.util.List;

/** One HUD frame, in scaled GUI pixels. Colors use ARGB (0xAARRGGBB). */
public final class HudEventJS extends EventJS {
    public record Label(String text, int x, int y, int color) {}
    public record Rectangle(int x, int y, int width, int height, int color) {}
    private final int width;
    private final int height;
    private final List<Label> labels = new ArrayList<>();
    private final List<Rectangle> rectangles = new ArrayList<>();

    public HudEventJS(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    /** Queues text above the filled rectangles; the default color is opaque white. */
    public void text(String text, int x, int y) { text(text, x, y, 0xFFFFFFFFL); }
    public void text(String text, int x, int y, long color) {
        labels.add(new Label(text, x, y, (int) color));
    }

    /** Queues a filled rectangle behind the text. Empty rectangles are ignored. */
    public void fill(int x, int y, int width, int height, long color) {
        if (width > 0 && height > 0) rectangles.add(new Rectangle(x, y, width, height, (int) color));
    }

    public List<Label> labels() { return List.copyOf(labels); }
    public List<Rectangle> rectangles() { return List.copyOf(rectangles); }
}
