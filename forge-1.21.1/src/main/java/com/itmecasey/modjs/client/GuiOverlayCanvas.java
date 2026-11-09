package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.OverlayCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draws overlay elements with the game's graphics object (one small adapter per Minecraft version). */
final class GuiOverlayCanvas implements OverlayCanvas {
    private final GuiGraphics graphics;
    private final Font font = Minecraft.getInstance().font;

    GuiOverlayCanvas(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public void fill(int x, int y, int width, int height, int argb) {
        graphics.fill(x, y, x + width, y + height, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow, float scale) {
        if (scale == 1.0f) {
            graphics.drawString(font, text, x, y, argb, shadow);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, argb, shadow);
        graphics.pose().popPose();
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int fontHeight() {
        return font.lineHeight;
    }

    @Override
    public void texture(String texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight,
                        int textureWidth, int textureHeight, int argb) {
        ResourceLocation id = ResourceLocation.tryParse(texture);
        if (id == null) {
            return;
        }
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        graphics.setColor(((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f, a);
        graphics.blit(id, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void item(String itemId, int x, int y, float scale) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }
}
