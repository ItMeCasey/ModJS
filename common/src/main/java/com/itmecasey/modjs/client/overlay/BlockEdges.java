package com.itmecasey.modjs.client.overlay;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The outer silhouette of a group of blocks: every edge of the merged shape and none between blocks that touch. An edge of the
 * block lattice belongs to the silhouette when the four blocks around it are not all filled, not all empty, and not two filled
 * blocks side by side (that edge lies flat inside a face). Collinear pieces are joined, so a 2x2x2 cube has 12 edges.
 */
public final class BlockEdges {
    private BlockEdges() {
    }

    /** One straight edge from {@code (x0, y0, z0)} to {@code (x1, y1, z1)} in lattice coordinates (block corners). */
    public record Edge(int x0, int y0, int z0, int x1, int y1, int z1) {
    }

    private static long key(int x, int y, int z) {
        return WorldOverlay.key(x, y, z);
    }

    /** The silhouette edges of the blocks (at most one edge per run of unit edges along a line). */
    public static List<Edge> silhouette(List<int[]> blocks) {
        Set<Long> filled = new HashSet<>();
        for (int[] b : blocks) {
            filled.add(key(b[0], b[1], b[2]));
        }
        // unit edges as (axis, x, y, z) of the lower end; collected into a set so shared edges count once
        Set<String> seen = new HashSet<>();
        List<int[]> units = new ArrayList<>();
        for (int[] b : blocks) {
            for (int axis = 0; axis < 3; axis++) {
                for (int da = 0; da <= 1; da++) {
                    for (int db = 0; db <= 1; db++) {
                        int[] p = {b[0], b[1], b[2]};
                        p[(axis + 1) % 3] += da;
                        p[(axis + 2) % 3] += db;
                        String id = axis + ":" + p[0] + "," + p[1] + "," + p[2];
                        if (seen.add(id) && silhouetteUnit(filled, axis, p)) {
                            units.add(new int[] {axis, p[0], p[1], p[2]});
                        }
                    }
                }
            }
        }
        return merge(units);
    }

    /** Whether the unit edge starting at {@code p} along {@code axis} is on the silhouette. */
    private static boolean silhouetteUnit(Set<Long> filled, int axis, int[] p) {
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        // the four cells around the edge: offsets (-1 or 0) along the two other axes
        boolean[][] cell = new boolean[2][2];
        int count = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                int[] c = {p[0], p[1], p[2]};
                c[a] -= 1 - i;
                c[b] -= 1 - j;
                cell[i][j] = filled.contains(key(c[0], c[1], c[2]));
                if (cell[i][j]) {
                    count++;
                }
            }
        }
        if (count == 1 || count == 3) {
            return true;
        }
        // two filled cells: a crease only when they touch at a corner (diagonal), not when they share a side
        return count == 2 && cell[0][0] == cell[1][1];
    }

    private static List<Edge> merge(List<int[]> units) {
        Set<String> present = new HashSet<>();
        for (int[] u : units) {
            present.add(u[0] + ":" + u[1] + "," + u[2] + "," + u[3]);
        }
        List<Edge> edges = new ArrayList<>();
        for (int[] u : units) {
            int[] before = {u[1], u[2], u[3]};
            before[u[0]] -= 1;
            if (present.contains(u[0] + ":" + before[0] + "," + before[1] + "," + before[2])) {
                continue; // not the start of a run
            }
            int[] end = {u[1], u[2], u[3]};
            end[u[0]] += 1;
            while (present.contains(u[0] + ":" + end[0] + "," + end[1] + "," + end[2])) {
                end[u[0]] += 1;
            }
            edges.add(new Edge(u[1], u[2], u[3], end[0], end[1], end[2]));
        }
        return edges;
    }
}
