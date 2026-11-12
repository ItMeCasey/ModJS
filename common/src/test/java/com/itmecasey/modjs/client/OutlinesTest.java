package com.itmecasey.modjs.client;

import com.itmecasey.modjs.client.overlay.Overlay;
import com.itmecasey.modjs.client.overlay.WorldOutlines;
import com.itmecasey.modjs.event.ScriptBreak;
import com.itmecasey.modjs.worldcraft.LevelJSTest;
import com.itmecasey.modjs.worldcraft.LevelJS;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.NativeObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutlinesTest {
    private static NativeObject pos(int x, int y, int z) {
        NativeObject object = new NativeObject();
        object.put("x", object, x);
        object.put("y", object, y);
        object.put("z", object, z);
        return object;
    }

    private static NativeObject options(Object... pairs) {
        NativeObject object = new NativeObject();
        for (int i = 0; i < pairs.length; i += 2) {
            object.put((String) pairs[i], object, pairs[i + 1]);
        }
        return object;
    }

    private static void draw(List<Box> boxes, List<double[]> segments, double cx, double cy, double cz) {
        WorldOutlines.draw(new WorldOutlines.Canvas() {
            @Override
            public void box(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, int fill, boolean through) {
                boxes.add(new Box(x0, y0, z0, x1, line, width, fill, through));
            }

            @Override
            public void segment(double x0, double y0, double z0, double x1, double y1, double z1, int line, float width, boolean through) {
                segments.add(new double[] {x0, y0, z0, x1, y1, z1, line});
            }
        }, cx, cy, cz);
    }

    record Box(double x0, double y0, double z0, double x1, int line, float width, int fill, boolean through) {
    }

    @Test
    void anOutlineSetTakesObjectsAndTriplesAndDrawsOneBoxPerBlock() {
        WorldOutlines.clear();
        WorldOutlines.Outline set = new Overlay.Binding().outline("veins",
            List.of(pos(1, 64, 2), List.of(3, 64, 2), pos(1, 64, 2)), options("color", "#ff8800", "width", 3, "fill", "#40ff8800", "throughWalls", true));
        assertEquals(2, set.getCount()); // the repeated position counts once
        List<Box> boxes = new ArrayList<>();
        draw(boxes, new ArrayList<>(), 0, 64, 0);
        assertEquals(2, boxes.size());
        Box first = boxes.get(0);
        assertEquals(1, Math.round(first.x0()));
        assertEquals(0xFFFF8800, first.line());
        assertEquals(0x40FF8800, first.fill());
        assertEquals(3.0f, first.width());
        assertTrue(first.through());
        WorldOutlines.clear();
    }

    @Test
    void sameIdReplacesBlocksAndRemoveStopsDrawing() {
        WorldOutlines.clear();
        Overlay.Binding overlay = new Overlay.Binding();
        overlay.outline("a", List.of(pos(0, 0, 0), pos(1, 0, 0)));
        WorldOutlines.Outline again = overlay.outline("a", List.of(pos(5, 5, 5)), options("color", 0xFF00FF00L));
        assertEquals(1, again.getCount());
        assertEquals(1, WorldOutlines.count());
        assertTrue(overlay.removeOutline("a"));
        assertFalse(overlay.removeOutline("a"));
        assertFalse(WorldOutlines.any());
        assertNull(overlay.getOutline("a"));
    }

    @Test
    void farBlocksAndHiddenSetsAreSkippedAndBadPositionsAreErrors() {
        WorldOutlines.clear();
        WorldOutlines.Outline set = WorldOutlines.put("far", List.of(pos(0, 0, 0), pos(500, 0, 0)), null);
        List<Box> boxes = new ArrayList<>();
        draw(boxes, new ArrayList<>(), 0, 0, 0);
        assertEquals(1, boxes.size());
        set.visible = false;
        boxes.clear();
        draw(boxes, new ArrayList<>(), 0, 0, 0);
        assertEquals(0, boxes.size());
        assertThrows(RuntimeException.class, () -> WorldOutlines.put("bad", List.of(options("x", 1)), null));
        assertThrows(RuntimeException.class, () -> WorldOutlines.put("", List.of(), null));
        WorldOutlines.clear();
    }

    @Test
    void scriptBreaksSuppressEventsUnlessAskedAndStopNestingAtTheLimit() {
        assertFalse(ScriptBreak.active());
        List<String> seen = new ArrayList<>();
        assertTrue(ScriptBreak.run(false, () -> {
            seen.add(ScriptBreak.suppressesEvents() ? "suppressed" : "open");
            ScriptBreak.run(true, () -> {
                seen.add(ScriptBreak.suppressesEvents() ? "suppressed" : "open");
                return true;
            });
            seen.add(ScriptBreak.suppressesEvents() ? "suppressed" : "open");
            return true;
        }));
        assertEquals(List.of("suppressed", "open", "suppressed"), seen);
        assertFalse(ScriptBreak.active());
        boolean[] reached = {false};
        ScriptBreak.run(true, () -> ScriptBreak.run(true, () -> ScriptBreak.run(true, () -> {
            reached[0] = ScriptBreak.run(true, () -> true);
            return true;
        })));
        assertFalse(reached[0]); // the fourth nested break is refused
    }

    @Test
    void theClientWorldReadsButRefusesToChangeBlocks() {
        LevelJSTest.Fake fake = new LevelJSTest.Fake();
        fake.blocks.put("2,2,2", "minecraft:oak_log");
        LevelJS client = new LevelJS(fake, ScriptType.CLIENT, true);
        assertEquals("minecraft:oak_log", client.getBlockId(2, 2, 2));
        assertEquals(1, client.findBlocks(0, 0, 0, 5, 5, 5, "#minecraft:logs").getLength());
        assertThrows(RuntimeException.class, () -> client.destroyBlock(2, 2, 2, true));
        assertThrows(RuntimeException.class, () -> client.setBlock(2, 2, 2, "minecraft:stone"));
        assertThrows(RuntimeException.class, () -> client.bonemeal(2, 2, 2));
        assertEquals("minecraft:oak_log", fake.blocks.get("2,2,2"));
    }

    @Test
    void lookingAtAnswersFromTheInstalledProvider() {
        LevelJSTest.Fake fake = new LevelJSTest.Fake();
        fake.blocks.put("4,5,6", "minecraft:oak_log");
        ClientQueries.install(new ClientQueries.Provider() {
            @Override
            public com.itmecasey.modjs.worldcraft.WorldHandles.LevelHandle level() {
                return fake;
            }

            @Override
            public Map<String, Object> lookingAt() {
                return Map.of("x", 4, "y", 5, "z", 6, "id", "minecraft:oak_log", "face", "up", "distance", 3.5);
            }

            @Override
            public boolean keyHeld(String id) {
                return id.equals("held");
            }

            @Override
            public boolean keyKnown(String id) {
                return id.equals("held") || id.equals("idle");
            }
        });
        NativeObject hit = new ScriptedScreens.Binding().lookingAt();
        assertEquals("minecraft:oak_log", hit.get("id", hit));
        assertEquals("up", hit.get("face", hit));
        assertEquals("minecraft:oak_log", new ScriptedScreens.Binding().getBlock(4, 5, 6).get("id"));
        assertTrue(new ScriptedScreens.Binding().isKeyHeld("held"));
        assertFalse(new ScriptedScreens.Binding().isKeyHeld("idle"));
        assertThrows(RuntimeException.class, () -> new ScriptedScreens.Binding().isKeyHeld("missing"));
        ClientQueries.install(null);
        assertNull(new ScriptedScreens.Binding().lookingAt());
        assertNull(new ScriptedScreens.Binding().getWorld());
    }

    @Test
    void aConnectedSetDrawsOneMergedOutlineAndAPerBlockSetDrawsEveryBox() {
        WorldOutlines.clear();
        List<NativeObject> cube = new ArrayList<>();
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    cube.add(pos(x, y + 64, z));
                }
            }
        }
        new Overlay.Binding().outline(cube, options("connected", true, "color", "#ffd27a", "fill", "#33ffd27a", "width", 2));
        List<Box> boxes = new ArrayList<>();
        List<double[]> segments = new ArrayList<>();
        draw(boxes, segments, 0, 64, 0);
        assertEquals(8, boxes.size()); // the tint stays per block
        assertEquals(true, boxes.stream().allMatch(b -> (b.line() >>> 24) == 0 && b.fill() == 0x33FFD27A));
        assertEquals(12, segments.size()); // one line per edge of the merged cube
        assertEquals(0xFFFFD27A, (int) segments.get(0)[6]);
        WorldOutlines.clear();
        new Overlay.Binding().outline(cube, options("connected", false));
        boxes.clear();
        segments.clear();
        draw(boxes, segments, 0, 64, 0);
        assertEquals(8, boxes.size());
        assertEquals(0, segments.size());
        assertEquals(true, boxes.stream().allMatch(b -> (b.line() >>> 24) != 0)); // every block keeps its own edges
        WorldOutlines.clear();
    }

    @Test
    void outlineWithoutAnIdGetsItsOwnAndCanBeRemoved() {
        WorldOutlines.clear();
        WorldOutlines.Outline first = new Overlay.Binding().outline(List.of(pos(0, 0, 0)), options("color", "#ffffff"));
        WorldOutlines.Outline second = new Overlay.Binding().outline(List.of(pos(1, 0, 0)), null);
        assertEquals(false, first.id.equals(second.id));
        assertEquals(2, WorldOutlines.count());
        first.remove();
        assertEquals(1, WorldOutlines.count());
        WorldOutlines.Outline named = new Overlay.Binding().outline("named", List.of(pos(2, 0, 0)));
        assertEquals("named", named.id);
        WorldOutlines.clear();
    }
}
