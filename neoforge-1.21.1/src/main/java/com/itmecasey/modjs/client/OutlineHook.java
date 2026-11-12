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
        WorldOutlines.draw((x0, y0, z0, x1, y1, z1, line, width, fill, through) -> {
            double[] box = {x0 - cam.x, y0 - cam.y, z0 - cam.z, x1 - cam.x, y1 - cam.y, z1 - cam.z, line, fill};
            (through ? seen : front).add(box);
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
            int fill = (int) b[7];
            if ((fill >>> 24) != 0) {
                LevelRenderer.addChainedFilledBoxVertices(stack, fills, b[0], b[1], b[2], b[3], b[4], b[5],
                    ((fill >> 16) & 0xFF) / 255.0f, ((fill >> 8) & 0xFF) / 255.0f, (fill & 0xFF) / 255.0f, ((fill >>> 24) & 0xFF) / 255.0f);
            }
        }
        buffers.endBatch(RenderType.debugFilledBox());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (double[] b : boxes) {
            int line = (int) b[6];
            if ((line >>> 24) != 0) {
                LevelRenderer.renderLineBox(stack, lines, b[0], b[1], b[2], b[3], b[4], b[5],
                    ((line >> 16) & 0xFF) / 255.0f, ((line >> 8) & 0xFF) / 255.0f, (line & 0xFF) / 255.0f, ((line >>> 24) & 0xFF) / 255.0f);
            }
        }
        buffers.endBatch(RenderType.lines());
    }
}
