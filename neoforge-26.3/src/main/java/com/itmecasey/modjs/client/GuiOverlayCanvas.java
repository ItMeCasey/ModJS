package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.OverlayCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Draws overlay elements with the game's graphics object (one small adapter per Minecraft version). */
final class GuiOverlayCanvas implements OverlayCanvas {
    private static final java.util.Map<Identifier, ItemStack> STACKS = new java.util.HashMap<>();
    private static final Identifier NO_FLAT = Identifier.fromNamespaceAndPath("modjs", "none");
    private static final java.util.Map<Identifier, Identifier> FLAT = new java.util.HashMap<>();
    private final GuiGraphicsExtractor graphics;
    private final Font font = Minecraft.getInstance().font;

    GuiOverlayCanvas(GuiGraphicsExtractor graphics) {
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
            graphics.text(font, text, x, y, argb, shadow);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, argb, shadow);
        graphics.pose().popMatrix();
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
        Identifier id = Identifier.tryParse(texture);
        if (id == null) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, argb);
    }

    @Override
    public void item(String itemId, int x, int y, float scale) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) {
            return;
        }
        ItemStack stack = STACKS.get(id);
        if (stack == null) {
            // The title screen can draw before the item holders have their components bound (26.3 throws "Components not bound yet").
            // Skip the icon for that frame and try again on the next one; cache the stack once it builds.
            try {
                stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
                stack.getItem();
                if (stack.isEmpty()) {
                    return;
                }
            } catch (RuntimeException | LinkageError e) {
                drawFlat(id, x, y, scale);
                return;
            }
            STACKS.put(id, stack);
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);
        try {
            graphics.item(stack, 0, 0);
        } catch (RuntimeException e) {
            STACKS.remove(id);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * Draws the item's own texture file when no item stack can be built (the title screen has no bound item components, so 26.x cannot
     * render the item model there). Looks for textures/item/NAME.png, then textures/block/NAME.png; items without a flat texture draw nothing.
     */
    private void drawFlat(Identifier id, int x, int y, float scale) {
        Identifier flat = FLAT.computeIfAbsent(id, key -> {
            for (String folder : new String[] {"item", "block"}) {
                Identifier candidate = Identifier.fromNamespaceAndPath(key.getNamespace(), "textures/" + folder + "/" + key.getPath() + ".png");
                if (Minecraft.getInstance().getResourceManager().getResource(candidate).isPresent()) {
                    return candidate;
                }
            }
            return NO_FLAT;
        });
        if (flat != NO_FLAT) {
            int size = Math.round(16 * scale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, flat, x, y, 0.0f, 0.0f, size, size, 16, 16, 16, 16, 0xFFFFFFFF);
        }
    }
}
