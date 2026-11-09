package com.itmecasey.modjs.client.overlay;

/** An item icon: {@code Overlay.item('weapon', { x: 10, y: 40, item: 'minecraft:diamond_sword', scale: 2 })}. 16 pixels at scale 1. */
public final class OverlayItem extends OverlayElement {
    public String item = "minecraft:air";

    public OverlayItem(String id) {
        super(id);
    }

    @Override
    public double baseWidth(OverlayCanvas canvas) {
        return 16;
    }

    @Override
    public double baseHeight(OverlayCanvas canvas) {
        return 16;
    }

    @Override
    public void draw(OverlayCanvas canvas, int left, int top, int w, int h, double a) {
        if (a > 0.0 && item != null && !item.isEmpty()) {
            canvas.item(item.contains(":") ? item : "minecraft:" + item, left, top, (float) Math.max(0.1, scale));
        }
    }
}
