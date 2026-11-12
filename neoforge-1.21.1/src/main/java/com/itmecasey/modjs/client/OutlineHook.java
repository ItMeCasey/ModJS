package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.WorldOutlines;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws {@code Overlay.outline(...)} boxes around any block, from the level renderer's particle stage. Boxes that should show through
 * walls are drawn first with the depth test off; everything else keeps the depth test.
 */
@EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class OutlineHook {
    private OutlineHook() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !WorldOutlines.any()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        List<double[]> seen = new ArrayList<>();
        List<double[]> front = new ArrayList<>();
        WorldOutlines.draw(new WorldOutlines.Canvas() {
            @Override
            public void box(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, int fill, boolean through) {
                (through ? seen : front).add(new double[] {0, x0 - cam.x, y0 - cam.y, z0 - cam.z, x1 - cam.x, y1 - cam.y, z1 - cam.z, line, fill});
            }

            @Override
            public void segment(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, boolean through) {
                (through ? seen : front).add(new double[] {1, x0 - cam.x, y0 - cam.y, z0 - cam.z, x1 - cam.x, y1 - cam.y, z1 - cam.z, line, 0});
            }
        }, cam.x, cam.y, cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack stack = event.getPoseStack();
        if (!seen.isEmpty()) {
            RenderSystem.disableDepthTest();
            submit(stack, buffers, seen);
            RenderSystem.enableDepthTest();
        }
        submit(stack, buffers, front);
    }

    private static void submit(PoseStack stack, MultiBufferSource.BufferSource buffers, List<double[]> boxes) {
        if (boxes.isEmpty()) {
            return;
        }
        VertexConsumer fills = buffers.getBuffer(RenderType.debugFilledBox());
        for (double[] b : boxes) {
            int fill = (int) b[8];
            if (b[0] == 0 && (fill >>> 24) != 0) {
                LevelRenderer.addChainedFilledBoxVertices(stack, fills, b[1], b[2], b[3], b[4], b[5], b[6],
                    ((fill >> 16) & 0xFF) / 255.0f, ((fill >> 8) & 0xFF) / 255.0f, (fill & 0xFF) / 255.0f, ((fill >>> 24) & 0xFF) / 255.0f);
            }
        }
        buffers.endBatch(RenderType.debugFilledBox());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (double[] b : boxes) {
            int line = (int) b[7];
            if ((line >>> 24) != 0) {
                // a merged-outline edge is a box with no thickness: the game draws its twelve (overlapping) edges
                LevelRenderer.renderLineBox(stack, lines, b[1], b[2], b[3], b[4], b[5], b[6],
                    ((line >> 16) & 0xFF) / 255.0f, ((line >> 8) & 0xFF) / 255.0f, (line & 0xFF) / 255.0f, ((line >>> 24) & 0xFF) / 255.0f);
            }
        }
        buffers.endBatch(RenderType.lines());
    }
}
