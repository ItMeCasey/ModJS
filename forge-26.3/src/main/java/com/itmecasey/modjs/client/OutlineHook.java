package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.WorldOutlines;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderHighlightEvent;

/**
 * Draws {@code Overlay.outline(...)} boxes around any block. Forge 26.x has no level-wide render event, so the boxes are drawn from the
 * block highlight callback, which runs every frame while the crosshair is on a block. The callback replaces the vanilla highlight, so
 * the highlighted block's own outline is drawn here as well.
 */
public final class OutlineHook {
    private OutlineHook() {
    }

    static void register() {
        RenderHighlightEvent.Block.BUS.addListener((RenderHighlightEvent.Block event) -> {
            if (!WorldOutlines.any() || Minecraft.getInstance().level == null) {
                return false;
            }
            Vec3 cam = event.getCamera().position();
            net.minecraft.core.BlockPos target = event.getTarget().getBlockPos();
            event.setCustomRenderer((collector, stack, state) -> {
                Vec3 camera = state.cameraRenderState.pos;
                WorldOutlines.draw(new WorldOutlines.Canvas() {
                    @Override
                    public void box(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, int fill, boolean through) {
                        OutlineBoxes.box(stack, collector, camera, x0, y0, z0, x1, y1, z1, line, width, fill);
                    }

                    @Override
                    public void segment(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, boolean through) {
                        OutlineBoxes.segment(stack, collector, camera, x0, y0, z0, x1, y1, z1, line, width);
                    }
                }, camera.x, camera.y, camera.z);
                OutlineBoxes.box(stack, collector, camera, target.getX(), target.getY(), target.getZ(), target.getX() + 1.0, target.getY() + 1.0, target.getZ() + 1.0,
                    0x66000000, 2.0f, 0);
            });
            return false;
        });
    }
}
