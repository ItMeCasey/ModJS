package com.itmecasey.modjs.client.overlay;

/**
 * A progress bar: {@code Overlay.bar('hp', { x: 10, y: 10, width: 100, height: 8, value: 60, max: 100, color: '#cc2222' })}.
 * Change {@code value} (or {@code max}) whenever you like; with {@code smooth} set, the shown fill eases to the new value.
 */
public final class OverlayBar extends OverlayElement {
    public double width = 100;
    public double height = 8;
    public double value = 0;
    public double min = 0;
    public double max = 100;
    /** Milliseconds the fill takes to catch up with a new value. 0 jumps at once. */
    public double smooth = 0;
    /** The fill that is drawn right now; follows {@link #value} (read only for scripts). */
    public double displayValue = Double.NaN;
    /** Fill direction: right, left, up or down. */
    public String direction = "right";
    public Object color = 0xFF3CB371;
    /** When set, the fill blends from {@link #color} to this color along the bar. */
    public Object color2 = null;
    public Object background = 0xAA000000;
    public Object border = 0xFF000000;
    public double borderSize = 1;
    /** Text drawn on the bar. {value}, {max} and {percent} are replaced; for example "{value} / {max}". */
    public String label = null;
    public Object labelColor = 0xFFFFFFFF;
    public boolean labelShadow = true;
    public double labelScale = 1.0;

    public OverlayBar(String id) {
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
    protected void onTick(long now, double deltaMs) {
        if (Double.isNaN(displayValue) || smooth <= 0) {
            displayValue = value;
        } else {
            displayValue += (value - displayValue) * (1.0 - Math.exp(-deltaMs / (smooth / 3.0)));
            if (Math.abs(value - displayValue) < 0.001 * Math.max(1.0, Math.abs(max - min))) {
                displayValue = value;
            }
        }
    }

    /** Fraction of the bar that is filled, 0..1. */
    public double fraction() {
        double span = max - min;
        if (span <= 0) {
            return 0.0;
        }
        double shown = Double.isNaN(displayValue) ? value : displayValue;
        return Math.max(0.0, Math.min(1.0, (shown - min) / span));
    }

    @Override
    public void draw(OverlayCanvas canvas, int left, int top, int w, int h, double a) {
        int bs = Math.max(0, (int) Math.round(borderSize));
        int borderColor = Colors.withAlpha(Colors.parse(border, 0xFF000000), a);
        if (bs > 0 && (borderColor >>> 24) != 0) {
            canvas.fill(left - bs, top - bs, w + 2 * bs, h + 2 * bs, borderColor);
        }
        int back = Colors.withAlpha(Colors.parse(background, 0xAA000000), a);
        if ((back >>> 24) != 0) {
            canvas.fill(left, top, w, h, back);
        }
        double fraction = fraction();
        int from = Colors.withAlpha(Colors.parse(color, 0xFF3CB371), a);
        int to = color2 == null ? from : Colors.withAlpha(Colors.parse(color2, from), a);
        boolean horizontal = direction.equals("right") || direction.equals("left");
        int length = (int) Math.round((horizontal ? w : h) * fraction);
        for (int i = 0; i < length; i++) {
            double t = (horizontal ? w : h) <= 1 ? 0.0 : i / (double) ((horizontal ? w : h) - 1);
            int c = from == to ? from : Colors.mix(from, to, t);
            if (direction.equals("right")) {
                canvas.fill(left + i, top, 1, h, c);
            } else if (direction.equals("left")) {
                canvas.fill(left + w - 1 - i, top, 1, h, c);
            } else if (direction.equals("down")) {
                canvas.fill(left, top + i, w, 1, c);
            } else {
                canvas.fill(left, top + h - 1 - i, w, 1, c);
            }
        }
        if (label != null && !label.isEmpty()) {
            String shown = label
                .replace("{value}", format(Double.isNaN(displayValue) ? value : displayValue))
                .replace("{max}", format(max))
                .replace("{percent}", String.valueOf(Math.round(fraction * 100)) + "%");
            float s = (float) Math.max(0.1, labelScale);
            int tw = (int) Math.round(canvas.textWidth(shown) * s);
            int th = (int) Math.round(canvas.fontHeight() * s);
            canvas.text(shown, left + (w - tw) / 2, top + (h - th) / 2, Colors.withAlpha(Colors.parse(labelColor, 0xFFFFFFFF), a), labelShadow, s);
        }
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
