package com.itmecasey.modjs.client.overlay;

/** Draws the overlay elements of one surface ("hud" or "screen") onto a canvas. Called once per frame by the version modules. */
public final class OverlayRenderer {
    private OverlayRenderer() {
    }

    public static void draw(OverlayCanvas canvas, String surface) {
        long now = Overlay.now();
        for (OverlayElement element : Overlay.elements(surface)) {
            element.tick(now);
            if (!element.visible || element.alpha <= 0.0) {
                continue;
            }
            double factor = Math.max(0.0, element.scale);
            int w = (int) Math.round(element.baseWidth(canvas) * factor);
            int h = (int) Math.round(element.baseHeight(canvas) * factor);
            int left = (int) Math.round(anchorX(element.anchor, canvas.width(), w) + element.x);
            int top = (int) Math.round(anchorY(element.anchor, canvas.height(), h) + element.y);
            element.draw(canvas, left, top, w, h, Math.min(1.0, element.alpha));
        }
    }

    static double anchorX(String anchor, int screen, int size) {
        String a = anchor == null ? "topleft" : anchor;
        if (a.endsWith("right")) return screen - size;
        if (a.equals("top") || a.equals("center") || a.equals("bottom")) return (screen - size) / 2.0;
        return 0.0;
    }

    static double anchorY(String anchor, int screen, int size) {
        String a = anchor == null ? "topleft" : anchor;
        if (a.startsWith("bottom")) return screen - size;
        if (a.equals("left") || a.equals("center") || a.equals("right")) return (screen - size) / 2.0;
        return 0.0;
    }
}
