package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;

/** Client-only adapter for ClientEvents.hud; absent handlers cost no script calls. */
@Mod.EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class ScriptedHud {
    private ScriptedHud() {}

    @SubscribeEvent
    public static void register(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath("modjs", "script_hud"), (graphics, delta) -> draw(graphics));
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden() || !EventGroups.CLIENT.hasHandlers("hud")) return;
        HudEventJS event = new HudEventJS(graphics.guiWidth(), graphics.guiHeight());
        EventGroups.CLIENT.post("hud", event);
        if (event.isCancelled()) return;
        for (HudEventJS.Rectangle rectangle : event.rectangles()) {
            graphics.fill(rectangle.x(), rectangle.y(), rectangle.x() + rectangle.width(), rectangle.y() + rectangle.height(), rectangle.color());
        }
        for (HudEventJS.Label label : event.labels()) {
            graphics.text(mc.font, label.text(), label.x(), label.y(), label.color(), true);
        }
    }
}
