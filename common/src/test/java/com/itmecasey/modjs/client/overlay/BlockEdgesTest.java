package com.itmecasey.modjs.client.overlay;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEdgesTest {
    private static List<int[]> blocks(int... xyz) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < xyz.length; i += 3) {
            out.add(new int[] {xyz[i], xyz[i + 1], xyz[i + 2]});
        }
        return out;
    }

    @Test
    void oneBlockHasTwelveEdges() {
        assertEquals(12, BlockEdges.silhouette(blocks(5, 5, 5)).size());
    }

    @Test
    void aTwoByTwoByTwoCubeHasTwelveMergedEdgesOfLengthTwo() {
        List<int[]> cube = new ArrayList<>();
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    cube.add(new int[] {x, y, z});
                }
            }
        }
        List<BlockEdges.Edge> edges = BlockEdges.silhouette(cube);
        assertEquals(12, edges.size());
        for (BlockEdges.Edge e : edges) {
            int length = Math.abs(e.x1() - e.x0()) + Math.abs(e.y1() - e.y0()) + Math.abs(e.z1() - e.z0());
            assertEquals(2, length);
        }
    }

    @Test
    void anLShapeDropsTheEdgesBetweenTouchingBlocks() {
        // three blocks in an L, one block high: 18 outer edges (6 on top, 6 below, 6 corners); separate boxes would draw 36
        List<BlockEdges.Edge> edges = BlockEdges.silhouette(blocks(0, 0, 0, 1, 0, 0, 0, 0, 1));
        assertEquals(18, edges.size());
        // the edge where the arms meet (x=1 and z=1, running up the inside corner) is kept, the line between the two arms' tops is not
        assertTrue(edges.stream().anyMatch(e -> e.x0() == 1 && e.z0() == 1 && e.y0() == 0 && e.y1() == 1));
        assertTrue(edges.stream().noneMatch(e -> e.x0() == 1 && e.x1() == 1 && e.z0() == 0 && e.z1() == 1));
    }

    @Test
    void aStraightRowMergesIntoOneBox() {
        List<BlockEdges.Edge> edges = BlockEdges.silhouette(blocks(0, 0, 0, 1, 0, 0, 2, 0, 0, 3, 0, 0));
        assertEquals(12, edges.size());
        assertEquals(4, edges.stream().filter(e -> e.x1() - e.x0() == 4).count());
    }

    @Test
    void blocksThatOnlyTouchAtACornerShareTheirEdge() {
        // 23 unit edges (24 minus the shared one), and four pairs of them line up and join: 19 lines
        assertEquals(19, BlockEdges.silhouette(blocks(0, 0, 0, 1, 0, 1)).size());
    }

    @Test
    void separateGroupsAreOutlinedSeparately() {
        assertEquals(24, BlockEdges.silhouette(blocks(0, 0, 0, 5, 0, 0)).size());
    }
}
