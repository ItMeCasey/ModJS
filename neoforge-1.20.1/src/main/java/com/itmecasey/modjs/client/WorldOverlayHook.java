package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.WorldCanvas;
import com.itmecasey.modjs.client.overlay.WorldOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws {@code Overlay.at(pos)} elements in the world, after the block entities, with the level's buffer source. */
@Mod.EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class WorldOverlayHook {
    private static final ResourceLocation WHITE = new ResourceLocation("modjs", "textures/misc/white.png");
    private static final int FULL_BRIGHT = 15728880;
    /** Distance between layered quads of one element; smaller steps z-fight at a distance. */
    private static final float Z_STEP = 0.002f;

    private WorldOverlayHook() {
    }

    // Forge 1.21.1 hands the event the view matrix (deprecated), not a pose stack; the level renderer uses an identity stack and
    // applies the view matrix in the shader, so an empty stack is the same frame NeoForge gives.
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || !WorldOverlay.any()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 position = camera.getPosition();
        WorldOverlay.refreshTemplates(position.x, position.y, position.z, System.currentTimeMillis());
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Canvas canvas = new Canvas(event.getPoseStack(), buffers, camera);
        WorldOverlay.draw(canvas, position.x, position.y, position.z,
            key -> mc.level.hasChunkAt(new BlockPos(WorldOverlay.keyX(key), WorldOverlay.keyY(key), WorldOverlay.keyZ(key))));
        buffers.endBatch();
    }

    /** One adapter for the 1.21.1 buffer-source API. */
    static final class Canvas implements WorldCanvas {
        private final PoseStack stack;
        private final MultiBufferSource buffers;
        private final Camera camera;
        private final Font font = Minecraft.getInstance().font;
        private int layer;

        Canvas(PoseStack stack, MultiBufferSource buffers, Camera camera) {
            this.stack = stack;
            this.buffers = buffers;
            this.camera = camera;
        }

        @Override
        public void push(double x, double y, double z, String face) {
            Vec3 cam = camera.getPosition();
            stack.pushPose();
            stack.translate(x - cam.x, y - cam.y, z - cam.z);
            switch (face) {
                case "up" -> stack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                case "down" -> stack.mulPose(Axis.XP.rotationDegrees(90.0f));
                case "north" -> stack.mulPose(Axis.YP.rotationDegrees(180.0f));
                case "east" -> stack.mulPose(Axis.YP.rotationDegrees(90.0f));
                case "west" -> stack.mulPose(Axis.YP.rotationDegrees(-90.0f));
                case "south" -> {
                }
                default -> {
                    stack.mulPose(camera.rotation());
                    stack.mulPose(Axis.YP.rotationDegrees(180.0f));
                }
            }
            layer = 0;
        }

        @Override
        public void pop() {
            stack.popPose();
        }

        @Override
        public void text(String text, double lineHeight, int argb, boolean shadow, int backgroundArgb) {
            if ((backgroundArgb >>> 24) != 0) {
                // the font's own background sits a hair behind the glyphs and z-fights with them, so it is a separate quad
                double width = textWidth(text, lineHeight);
                double pad = lineHeight * 0.25;
                rect(WHITE, (float) (-width / 2 - pad), (float) (-lineHeight / 2 - pad * 0.6), (float) (width + pad * 2), (float) (lineHeight + pad * 1.2), backgroundArgb);
            }
            float s = (float) (lineHeight / font.lineHeight);
            stack.pushPose();
            stack.translate(0.0f, 0.0f, layer++ * Z_STEP);
            stack.scale(s, -s, s);
            float x = -font.width(text) / 2.0f;
            font.drawInBatch(text, x, -font.lineHeight / 2.0f, argb, shadow, stack.last().pose(), buffers, Font.DisplayMode.NORMAL,
                0, FULL_BRIGHT);
            stack.popPose();
        }

        @Override
        public double textWidth(String text, double lineHeight) {
            return font.width(text) * lineHeight / font.lineHeight;
        }

        @Override
        public void item(String itemId, double size) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) {
                return;
            }
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(id));
            Minecraft mc = Minecraft.getInstance();
            stack.pushPose();
            stack.translate(0.0f, 0.0f, layer++ * Z_STEP);
            stack.mulPose(Axis.YP.rotationDegrees(180.0f));
            stack.scale((float) size, (float) size, (float) size);
            mc.getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, stack, buffers, mc.level, 0);
            stack.popPose();
        }

        private void rect(ResourceLocation texture, float left, float bottom, float width, float height, int argb) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
            PoseStack.Pose pose = stack.last();
            float z = layer++ * Z_STEP;
            float right = left + width;
            float top = bottom + height;
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            consumer.vertex(pose.pose(), left, bottom, z).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(pose.normal(), 0, 0, 1).endVertex();
            consumer.vertex(pose.pose(), right, bottom, z).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(pose.normal(), 0, 0, 1).endVertex();
            consumer.vertex(pose.pose(), right, top, z).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(pose.normal(), 0, 0, 1).endVertex();
            consumer.vertex(pose.pose(), left, top, z).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(pose.normal(), 0, 0, 1).endVertex();
        }

        @Override
        public void quad(double left, double bottom, double width, double height, int argb) {
            rect(WHITE, (float) left, (float) bottom, (float) width, (float) height, argb);
        }

        @Override
        public void texture(String texture, double width, double height, int argb) {
            ResourceLocation id = ResourceLocation.tryParse(texture);
            if (id == null) {
                return;
            }
            rect(id, (float) (-width / 2), (float) (-height / 2), (float) width, (float) height, argb);
        }
    }
}
