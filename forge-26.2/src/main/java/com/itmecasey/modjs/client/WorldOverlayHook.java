package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.WorldCanvas;
import com.itmecasey.modjs.client.overlay.WorldOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/**
 * Draws {@code Overlay.at(pos)} elements in the world. Forge 26.x has no level-wide submit event, so a block entity renderer is
 * registered for the block entities ModJS adds (scripted blocks and storage blocks); the elements at a position are drawn when
 * that block entity renders. Vanilla block entities keep their own renderers, so overlays there are not drawn on Forge 26.x.
 */
@Mod.EventBusSubscriber(modid = "modjs", value = Dist.CLIENT)
public final class WorldOverlayHook {
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("modjs", "textures/misc/white.png");
    private static final int FULL_BRIGHT = 15728880;
    private static final double VIEW_DISTANCE = 64.0;
    private static final float Z_STEP = 0.002f;

    private WorldOverlayHook() {
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (net.minecraft.world.level.block.entity.BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (key != null && key.getNamespace().equals("modjs")) {
                event.registerBlockEntityRenderer((net.minecraft.world.level.block.entity.BlockEntityType<BlockEntity>) type, context -> new OverlayRenderer());
            }
        }
    }

    /** The renderer: one per ModJS block entity type. */
    static final class OverlayRenderer implements BlockEntityRenderer<BlockEntity, BlockEntityRenderState> {
        @Override
        public BlockEntityRenderState createRenderState() {
            return new BlockEntityRenderState();
        }

        @Override
        public void submit(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !WorldOverlay.any()) {
                return;
            }
            Vec3 position = camera.pos;
            WorldOverlay.refreshTemplates(position.x, position.y, position.z, System.currentTimeMillis());
            // the renderer's pose stack is already moved to the block; the canvas moves from the camera itself
            poseStack.pushPose();
            poseStack.translate(camera.pos.x - state.blockPos.getX(), camera.pos.y - state.blockPos.getY(), camera.pos.z - state.blockPos.getZ());
            Canvas canvas = new Canvas(poseStack, collector, position, camera.orientation);
            BlockPos pos = state.blockPos;
            WorldOverlay.drawAt(canvas, pos.getX(), pos.getY(), pos.getZ(), position.x, position.y, position.z,
                key -> mc.level.hasChunkAt(new BlockPos(WorldOverlay.keyX(key), WorldOverlay.keyY(key), WorldOverlay.keyZ(key))));
            poseStack.popPose();
        }

        @Override
        public int getViewDistance() {
            return (int) VIEW_DISTANCE;
        }
    }

    /** One adapter for the 26.x submit-collector API. */
    static final class Canvas implements WorldCanvas {
        private final PoseStack stack;
        private final SubmitNodeCollector collector;
        private final Vec3 camera;
        private final Quaternionf orientation;
        private final Font font = Minecraft.getInstance().font;
        private int layer;

        Canvas(PoseStack stack, SubmitNodeCollector collector, Vec3 camera, Quaternionf orientation) {
            this.stack = stack;
            this.collector = collector;
            this.camera = camera;
            this.orientation = orientation;
        }

        @Override
        public void push(double x, double y, double z, String face) {
            stack.pushPose();
            stack.translate(x - camera.x, y - camera.y, z - camera.z);
            switch (face) {
                case "up" -> stack.mulPose(Axis.XP.rotationDegrees(-90.0f));
                case "down" -> stack.mulPose(Axis.XP.rotationDegrees(90.0f));
                case "north" -> stack.mulPose(Axis.YP.rotationDegrees(180.0f));
                case "east" -> stack.mulPose(Axis.YP.rotationDegrees(90.0f));
                case "west" -> stack.mulPose(Axis.YP.rotationDegrees(-90.0f));
                case "south" -> {
                }
                default -> stack.mulPose(orientation);
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
            collector.submitText(stack, x, -font.lineHeight / 2.0f, Component.literal(text).getVisualOrderText(), shadow, Font.DisplayMode.NORMAL,
                FULL_BRIGHT, argb, 0, 0);
            stack.popPose();
        }

        @Override
        public double textWidth(String text, double lineHeight) {
            return font.width(text) * lineHeight / font.lineHeight;
        }

        @Override
        public void item(String itemId, double size) {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            ItemStackRenderState state = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(state, item, ItemDisplayContext.FIXED, mc.level, null, 0);
            stack.pushPose();
            stack.translate(0.0f, 0.0f, layer++ * Z_STEP);
            stack.mulPose(Axis.YP.rotationDegrees(180.0f));
            stack.scale((float) size, (float) size, (float) size);
            state.submit(stack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            stack.popPose();
        }

        private void rect(Identifier texture, float left, float bottom, float width, float height, int argb) {
            float z = layer++ * Z_STEP;
            float right = left + width;
            float top = bottom + height;
            int a = (argb >>> 24) & 0xFF;
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            collector.submitCustomGeometry(stack, RenderTypes.entityTranslucent(texture), (pose, consumer) -> {
                consumer.addVertex(pose, left, bottom, z).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
                consumer.addVertex(pose, right, bottom, z).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
                consumer.addVertex(pose, right, top, z).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
                consumer.addVertex(pose, left, top, z).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, 0, 0, 1);
            });
        }

        @Override
        public void quad(double left, double bottom, double width, double height, int argb) {
            rect(WHITE, (float) left, (float) bottom, (float) width, (float) height, argb);
        }

        @Override
        public void texture(String texture, double width, double height, int argb) {
            Identifier id = Identifier.tryParse(texture);
            if (id == null) {
                return;
            }
            rect(id, (float) (-width / 2), (float) (-height / 2), (float) width, (float) height, argb);
        }
    }
}
