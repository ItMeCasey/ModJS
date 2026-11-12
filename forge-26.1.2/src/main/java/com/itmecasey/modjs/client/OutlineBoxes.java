package com.itmecasey.modjs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** Draws one block box (edges and an optional tint) into a buffer source. Coordinates are absolute; the camera is subtracted here. */
final class OutlineBoxes {
    private OutlineBoxes() {
    }

    static void box(PoseStack stack, MultiBufferSource buffers, Vec3 cam, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        int line, float width, int fill) {
        float x0 = (float) (minX - cam.x);
        float y0 = (float) (minY - cam.y);
        float z0 = (float) (minZ - cam.z);
        float x1 = (float) (maxX - cam.x);
        float y1 = (float) (maxY - cam.y);
        float z1 = (float) (maxZ - cam.z);
        float[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        PoseStack.Pose pose = stack.last();
        if ((fill >>> 24) != 0) {
            int a = (fill >>> 24) & 0xFF;
            int r = (fill >> 16) & 0xFF;
            int g = (fill >> 8) & 0xFF;
            int b = fill & 0xFF;
            VertexConsumer consumer = buffers.getBuffer(RenderTypes.debugQuads());
            int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {0, 1, 5, 4}, {3, 7, 6, 2}};
            for (int[] face : faces) {
                for (int index : face) {
                    float[] c = corners[index];
                    consumer.addVertex(pose, c[0], c[1], c[2]).setColor(r, g, b, a);
                }
            }
        }
        if ((line >>> 24) != 0) {
            int a = (line >>> 24) & 0xFF;
            int r = (line >> 16) & 0xFF;
            int g = (line >> 8) & 0xFF;
            int b = line & 0xFF;
            VertexConsumer consumer = buffers.getBuffer(RenderTypes.lines());
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] edge : edges) {
                float[] p = corners[edge[0]];
                float[] q = corners[edge[1]];
                float nx = q[0] - p[0];
                float ny = q[1] - p[1];
                float nz = q[2] - p[2];
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= length;
                ny /= length;
                nz /= length;
                consumer.addVertex(pose, p[0], p[1], p[2]).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(width);
                consumer.addVertex(pose, q[0], q[1], q[2]).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(width);
            }
        }
    }
}
