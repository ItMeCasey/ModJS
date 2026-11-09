package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

/** Client-only adapter for ClientEvents.hud; absent handlers cost no script calls. */
@Mod.EventBusSubscriber(modid = "modjs", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ScriptedHud {
    private ScriptedHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("script_hud", (gui, graphics, partialTick, width, height) -> draw(graphics));
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
