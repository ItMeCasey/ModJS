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
        var stack = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();
        WorldOutlines.draw(new WorldOutlines.Canvas() {
            @Override
            public void box(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, int fill, boolean through) {
                OutlineBoxes.box(stack, collector, cam, x0, y0, z0, x1, y1, z1, line, width, fill);
            }

            @Override
            public void segment(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, boolean through) {
                OutlineBoxes.segment(stack, collector, cam, x0, y0, z0, x1, y1, z1, line, width);
            }
        }, cam.x, cam.y, cam.z);
    }
}
