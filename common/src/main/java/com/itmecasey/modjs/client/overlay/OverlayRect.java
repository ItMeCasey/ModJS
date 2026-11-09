package com.itmecasey.modjs.client.overlay;

/** A filled rectangle, optionally with a color gradient and a border: {@code Overlay.rect('panel', { x: 8, y: 8, width: 120, height: 40, color: '#cc182432' })}. */
public final class OverlayRect extends OverlayElement {
    public double width = 10;
    public double height = 10;
    public Object color = 0xFFFFFFFF;
    /** When set, the fill blends from {@link #color} to this color. */
    public Object color2 = null;
    /** True blends left to right, false (the default) top to bottom. */
    public boolean horizontal = false;
    public Object border = null;
    public double borderSize = 1;

    public OverlayRect(String id) {
        super(id);
    }

    @Override
    public double baseWidth(OverlayCanvas canvas) {
        return width;
    }

    @Override
    public double baseHeight(OverlayCanvas canvas) {
        return height;
    }

    @Override
    public void draw(OverlayCanvas canvas, int left, int top, int w, int h, double a) {
        int bs = Math.max(0, (int) Math.round(borderSize));
        if (border != null && bs > 0) {
            int edge = Colors.withAlpha(Colors.parse(border, 0xFF000000), a);
            if ((edge >>> 24) != 0) {
                canvas.fill(left - bs, top - bs, w + 2 * bs, h + 2 * bs, edge);
            }
        }
        int from = Colors.withAlpha(Colors.parse(color, 0xFFFFFFFF), a);
        if (color2 == null) {
            canvas.fill(left, top, w, h, from);
            return;
        }
        int to = Colors.withAlpha(Colors.parse(color2, from), a);
        int length = horizontal ? w : h;
        for (int i = 0; i < length; i++) {
            int c = Colors.mix(from, to, length <= 1 ? 0.0 : i / (double) (length - 1));
            if (horizontal) {
                canvas.fill(left + i, top, 1, h, c);
            } else {
                canvas.fill(left, top + i, w, 1, c);
            }
        }
    }
}
