package com.itmecasey.modjs.client.overlay;

/**
 * A picture from a resource pack texture: {@code Overlay.image('logo', { texture: 'mypack:textures/gui/logo.png', width: 32, height: 32 })}.
 * By default the whole texture is stretched to {@code width} by {@code height}. To draw only a part of a bigger sheet, give the real
 * size of the file ({@code texWidth}, {@code texHeight}) and the part ({@code u}, {@code v}, {@code regionWidth}, {@code regionHeight}).
 */
public final class OverlayImage extends OverlayElement {
    public String texture = "";
    public double width = 16;
    public double height = 16;
    public double u = 0;
    public double v = 0;
    /** Size of the part of the texture to draw; 0 means the rest of the file. Only used with texWidth and texHeight. */
    public double regionWidth = 0;
    public double regionHeight = 0;
    /** Real size of the texture file in pixels. Leave at 0 to stretch the whole texture. */
    public double texWidth = 0;
    public double texHeight = 0;
    public Object tint = 0xFFFFFFFF;

    public OverlayImage(String id) {
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
        if (texture == null || texture.isEmpty()) {
            return;
        }
        if (texWidth <= 0 || texHeight <= 0) {
            canvas.texture(texture, left, top, w, h, 0.0f, 0.0f, 1, 1, 1, 1, Colors.withAlpha(Colors.parse(tint, 0xFFFFFFFF), a));
            return;
        }
        int tw = (int) Math.round(texWidth);
        int th = (int) Math.round(texHeight);
        int rw = (int) Math.round(regionWidth > 0 ? regionWidth : texWidth - u);
        int rh = (int) Math.round(regionHeight > 0 ? regionHeight : texHeight - v);
        canvas.texture(texture, left, top, w, h, (float) u, (float) v, rw, rh, tw, th, Colors.withAlpha(Colors.parse(tint, 0xFFFFFFFF), a));
    }
}
