package com.itmecasey.modjs.client.overlay;

/**
 * Something drawn in the world by {@code Overlay.at(pos)}. Sizes and offsets are in blocks. The fields can be set as options
 * ({@code at.text('t', { text: 'Hi', y: 0.4 })}), changed later ({@code el.text = 'Bye'}) or animated like the HUD elements.
 */
public abstract class WorldElement extends OverlayElement {
    /** Offsets in blocks. For a billboard they count from the top center of the block; on a face, x is right and y is up on that face. */
    public double z;
    /** billboard (default), up, down, north, south, east or west. */
    public String face = "billboard";
    /** Culling distance in blocks, or 0 for the global maximum ({@code Overlay.world.maxDistance}). */
    public double distance;

    protected WorldElement(String id) {
        super(id);
        this.surface = "world";
    }

    /** Draws the element at the origin of the canvas frame. */
    public abstract void drawWorld(WorldCanvas canvas, double effectiveAlpha);

    @Override
    public double baseWidth(OverlayCanvas canvas) {
        return 0;
    }

    @Override
    public double baseHeight(OverlayCanvas canvas) {
        return 0;
    }

    @Override
    public void draw(OverlayCanvas canvas, int left, int top, int width, int height, double effectiveAlpha) {
        // world elements are drawn through WorldCanvas
    }

    /** Text: {@code at.text('t', { text: 'Generator', color: '#ffffff', scale: 1 })}. One line is 0.2 blocks tall at scale 1. */
    public static final class Text extends WorldElement {
        public String text = "";
        public Object color = 0xFFFFFFFF;
        public boolean shadow;
        /** Color behind the text, or null for none. */
        public Object background;

        public Text(String id) {
            super(id);
        }

        @Override
        public void drawWorld(WorldCanvas canvas, double alpha) {
            if (text == null || text.isEmpty()) {
                return;
            }
            int main = Colors.withAlpha(Colors.parse(color, 0xFFFFFFFF), alpha);
            int back = background == null ? 0 : Colors.withAlpha(Colors.parse(background, 0), alpha);
            canvas.text(text, 0.2 * scale, main, shadow, back);
        }
    }

    /** An item icon: {@code at.item('i', { item: 'minecraft:diamond', scale: 1 })}. About 0.5 blocks tall at scale 1. */
    public static final class Item extends WorldElement {
        public String item = "minecraft:air";

        public Item(String id) {
            super(id);
        }

        @Override
        public void drawWorld(WorldCanvas canvas, double alpha) {
            if (item == null || item.isEmpty() || item.equals("minecraft:air")) {
                return;
            }
            canvas.item(item.contains(":") ? item : "minecraft:" + item, 0.5 * scale);
        }
    }

    /** A progress bar: {@code at.bar('b', { value: 40, max: 100, width: 1, height: 0.12, color: '#3fdc5a' })}. */
    public static final class Bar extends WorldElement {
        public double value;
        public double max = 100;
        public double width = 1.0;
        public double height = 0.12;
        public Object color = 0xFF3FDC5A;
        public Object backgroundColor = 0xC0000000;

        public Bar(String id) {
            super(id);
        }

        /** The filled share of the bar, 0 to 1. */
        public double fraction() {
            return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, value / max));
        }

        @Override
        public void drawWorld(WorldCanvas canvas, double alpha) {
            double w = width * scale;
            double h = height * scale;
            int back = Colors.withAlpha(Colors.parse(backgroundColor, 0xC0000000), alpha);
            int fill = Colors.withAlpha(Colors.parse(color, 0xFF3FDC5A), alpha);
            canvas.quad(-w / 2, -h / 2, w, h, back);
            double filled = w * fraction();
            if (filled > 0) {
                canvas.quad(-w / 2, -h / 2, filled, h, fill);
            }
        }
    }

    /** A texture: {@code at.texture('p', { texture: 'modjs:textures/gui/pic.png', width: 0.5, height: 0.5 })}. */
    public static final class Texture extends WorldElement {
        public String texture = "";
        public double width = 0.5;
        public double height = 0.5;
        public Object color = 0xFFFFFFFF;

        public Texture(String id) {
            super(id);
        }

        @Override
        public void drawWorld(WorldCanvas canvas, double alpha) {
            if (texture == null || texture.isEmpty()) {
                return;
            }
            canvas.texture(texture, width * scale, height * scale, Colors.withAlpha(Colors.parse(color, 0xFFFFFFFF), alpha));
        }
    }
}
