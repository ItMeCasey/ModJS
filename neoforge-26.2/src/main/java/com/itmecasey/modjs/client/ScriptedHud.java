package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client-only adapter for ClientEvents.hud; absent handlers cost no script calls. */
@EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class ScriptedHud {
    private ScriptedHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath("modjs", "script_hud"), (graphics, delta) -> draw(graphics));
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
        boolean immediate = EventGroups.CLIENT.hasHandlers("hud");
        if (!immediate && !com.itmecasey.modjs.client.overlay.Overlay.any("hud")) return;
        HudEventJS event = new HudEventJS(graphics.guiWidth(), graphics.guiHeight());
        if (immediate) {
            EventGroups.CLIENT.post("hud", event);
            if (event.isCancelled()) return;
        }
        for (HudEventJS.Rectangle rectangle : event.rectangles()) {
            graphics.fill(rectangle.x(), rectangle.y(), rectangle.x() + rectangle.width(), rectangle.y() + rectangle.height(), rectangle.color());
        }
        for (HudEventJS.Label label : event.labels()) {
            graphics.text(mc.font, label.text(), label.x(), label.y(), label.color(), true);
        }
        com.itmecasey.modjs.client.overlay.OverlayRenderer.draw(new GuiOverlayCanvas(graphics), "hud");
    }
}
