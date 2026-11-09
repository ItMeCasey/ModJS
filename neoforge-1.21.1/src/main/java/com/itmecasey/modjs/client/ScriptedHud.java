package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client-only adapter for ClientEvents.hud; absent handlers cost no script calls. */
@EventBusSubscriber(modid = "modjs", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ScriptedHud {
    private ScriptedHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("modjs", "script_hud"), (graphics, delta) -> draw(graphics));
    }

    private static void draw(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !EventGroups.CLIENT.hasHandlers("hud")) return;
        HudEventJS event = new HudEventJS(graphics.guiWidth(), graphics.guiHeight());
        EventGroups.CLIENT.post("hud", event);
        if (event.isCancelled()) return;
        for (HudEventJS.Rectangle rectangle : event.rectangles()) {
            graphics.fill(rectangle.x(), rectangle.y(), rectangle.x() + rectangle.width(), rectangle.y() + rectangle.height(), rectangle.color());
        }
        for (HudEventJS.Label label : event.labels()) {
            graphics.drawString(mc.font, label.text(), label.x(), label.y(), label.color(), true);
        }
    }
}
