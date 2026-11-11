package com.itmecasey.modjs.client;

import com.itmecasey.modjs.registry.ScriptBlocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The screen of a scripted block: the panel, slots, bars, text and buttons its {@code screen(...)} definition describes. */
public final class ScriptContainerScreen extends AbstractContainerScreen<ScriptBlocks.ScriptMenu> {
    private final TitleScreenConfig config;

    public ScriptContainerScreen(ScriptBlocks.ScriptMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.definition().screenConfig().panelWidth(), menu.definition().screenConfig().panelHeight());
        this.config = menu.definition().screenConfig();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = (config.panelWidth() - 176) / 2 + 8;
        this.inventoryLabelY = config.panelHeight() - 94;
    }

    @Override
    protected void init() {
        super.init();
        int stackY = 20;
        for (int i = 0; i < config.buttons().size(); i++) {
            TitleScreenConfig.Button spec = config.buttons().get(i);
            int index = i;
            int x = leftPos + (spec.x() != null ? spec.x() : imageWidth / 2 - spec.width() / 2);
            int y;
            if (spec.y() != null) {
                y = topPos + spec.y();
            } else {
                y = topPos + stackY;
                stackY += spec.height() + 4;
            }
            addRenderableWidget(Button.builder(Component.literal(spec.label()), button -> {
                if (spec.id() != null && minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                }
            }).bounds(x, y, spec.width(), spec.height()).build());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        String kind = config.backgroundKind();
        if ("color".equals(kind)) {
            graphics.fill(x, y, x + imageWidth, y + imageHeight, config.backgroundColor());
        } else if (kind != null && kind.contains(":") && kind.endsWith(".png")) {
            Identifier texture = Identifier.tryParse(kind);
            if (texture != null) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
            }
        } else {
            // the vanilla grey panel with a light top left edge and a dark bottom right edge
            graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF000000);
            graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFFFFFFFF);
            graphics.fill(x + 3, y + 3, x + imageWidth - 1, y + imageHeight - 1, 0xFF555555);
            graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFC6C6C6);
        }
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
            graphics.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        }
        for (TitleScreenConfig.Bar bar : config.bars()) {
            double max = number(bar.max());
            double fraction = max <= 0 ? 0 : Math.max(0.0, Math.min(1.0, number(bar.value()) / max));
            int bx = x + bar.x();
            int by = y + bar.y();
            graphics.fill(bx, by, bx + bar.width(), by + bar.height(), bar.background());
            graphics.fill(bx, by, bx + (int) Math.round(bar.width() * fraction), by + bar.height(), bar.color());
        }
        for (TitleScreenConfig.Text text : config.texts()) {
            int tx = text.centered() ? x + imageWidth / 2 + text.x() - font.width(text.text()) / 2 : x + text.x();
            graphics.text(font, text.text(), tx, y + text.y(), text.color(), false);
        }
    }

    /** A bar value is a number written as text, or the name of one of the block's synced values. */
    private double number(String valueOrName) {
        try {
            return Double.parseDouble(valueOrName);
        } catch (NumberFormatException e) {
            return menu.value(valueOrName);
        }
    }
}
