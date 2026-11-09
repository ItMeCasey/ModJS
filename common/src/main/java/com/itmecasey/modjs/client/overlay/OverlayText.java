package com.itmecasey.modjs.client.overlay;

/**
 * Text: {@code Overlay.text('score', { x: 10, y: 20, text: 'Score: 0', color: '#ffcc44', scale: 1.5, shadow: true })}.
 * Change {@code text} whenever you like. {@code outline} draws a one pixel border around the letters.
 */
public final class OverlayText extends OverlayElement {
    public String text = "";
    public Object color = 0xFFFFFFFF;
    public boolean shadow = true;
    /** Border color around the letters, or null for none. */
    public Object outline = null;
    /** Box drawn behind the text, or null for none. */
    public Object background = null;
    public double padding = 2;
    /** How the lines sit on x: left, center or right. Only matters when {@code width} is set or the text has several lines. */
    public String align = "left";
    /** Width of the text box; 0 uses the width of the widest line. */
    public double width = 0;

    public OverlayText(String id) {
        super(id);
    }

    private String[] lines() {
        return (text == null ? "" : text).split("\n", -1);
    }

    private double textWidth(OverlayCanvas canvas) {
        double widest = 0;
        for (String line : lines()) {
            widest = Math.max(widest, canvas.textWidth(line));
        }
        return widest;
    }

    @Override
    public double baseWidth(OverlayCanvas canvas) {
        return (width > 0 ? width : textWidth(canvas)) + (background != null ? padding * 2 : 0);
    }

    @Override
    public double baseHeight(OverlayCanvas canvas) {
        return lines().length * (canvas.fontHeight() + 1) - 1 + (background != null ? padding * 2 : 0);
    }

    @Override
    public void draw(OverlayCanvas canvas, int left, int top, int w, int h, double a) {
        float s = (float) Math.max(0.1, scale);
        int pad = background != null ? (int) Math.round(padding * s) : 0;
        if (background != null) {
            int back = Colors.withAlpha(Colors.parse(background, 0), a);
            if ((back >>> 24) != 0) {
                canvas.fill(left, top, w, h, back);
            }
        }
        int main = Colors.withAlpha(Colors.parse(color, 0xFFFFFFFF), a);
        int edge = outline == null ? 0 : Colors.withAlpha(Colors.parse(outline, 0xFF000000), a);
        double inner = (w - 2.0 * pad) / s;
        int y = top + pad;
        for (String line : lines()) {
            double lineWidth = canvas.textWidth(line);
            double dx = align.equals("center") ? (inner - lineWidth) / 2.0 : align.equals("right") ? inner - lineWidth : 0.0;
            int x = left + pad + (int) Math.round(dx * s);
            if (outline != null && (edge >>> 24) != 0) {
                int o = Math.max(1, Math.round(s));
                canvas.text(line, x - o, y, edge, false, s);
                canvas.text(line, x + o, y, edge, false, s);
                canvas.text(line, x, y - o, edge, false, s);
                canvas.text(line, x, y + o, edge, false, s);
            }
            canvas.text(line, x, y, main, shadow, s);
            y += (int) Math.round((canvas.fontHeight() + 1) * s);
        }
    }
}
