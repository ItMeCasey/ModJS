package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.WorldOutlines;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Draws {@code Overlay.outline(...)} boxes around any block from the level renderer's submit collector. */
@EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class OutlineHook {
    private OutlineHook() {
    }

    @SubscribeEvent
    public static void render(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !WorldOutlines.any()) {
            return;
        }
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        WorldOutlines.draw((x0, y0, z0, x1, y1, z1, line, width, fill, through) ->
            OutlineBoxes.box(event.getPoseStack(), event.getSubmitNodeCollector(), cam, x0, y0, z0, x1, y1, z1, line, width, fill), cam.x, cam.y, cam.z);
    }
}
