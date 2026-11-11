package com.itmecasey.modjs.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

class WorldOverlayTest {
    /** Records every drawing call as text. */
    static final class Recorder implements WorldCanvas {
        final List<String> calls = new ArrayList<>();

        public void push(double x, double y, double z, String face) { calls.add(String.format("push %.2f,%.2f,%.2f %s", x, y, z, face)); }
        public void pop() { calls.add("pop"); }
        public void text(String text, double h, int argb, boolean shadow, int back) { calls.add(String.format("text '%s' %.2f %x", text, h, argb)); }
        public double textWidth(String text, double h) { return text.length() * h * 0.6; }
        public void item(String id, double size) { calls.add(String.format("item %s %.2f", id, size)); }
        public void quad(double l, double b, double w, double h, int argb) { calls.add(String.format("quad %.2f,%.2f %.2fx%.2f %x", l, b, w, h, argb)); }
        public void texture(String t, double w, double h, int argb) { calls.add(String.format("texture %s %.2fx%.2f %x", t, w, h, argb)); }
    }

    private final AtomicLong time = new AtomicLong(1000);
    private Context cx;
    private Scriptable scope;

    @BeforeEach
    void setUp() {
        Overlay.clear();
        WorldOverlay.forgetBlocks();
        WorldOverlay.maxDistance = 24.0;
        WorldOverlay.maxElements = 128;
        Overlay.setClock(time::get);
        cx = Context.enter();
        scope = cx.initStandardObjects();
        ScriptableObject.putProperty(scope, "Overlay", Context.javaToJS(new Overlay.Binding(), scope));
    }

    @AfterEach
    void tearDown() {
        Context.exit();
        Overlay.setClock(null);
        Overlay.clear();
        WorldOverlay.forgetBlocks();
        WorldOverlay.maxDistance = 24.0;
        WorldOverlay.maxElements = 128;
    }

    private Object run(String source) {
        return cx.evaluateString(scope, source, "test", 1, null);
    }

    private List<String> draw(double cx, double cy, double cz) {
        Recorder recorder = new Recorder();
        WorldOverlay.draw(recorder, cx, cy, cz, key -> true);
        return recorder.calls;
    }

    @Test
    void allFourElementKindsDrawAtTheBlock() {
        run("""
            const o = Overlay.at(10, 64, 20);
            o.text('t', { text: 'Generator', y: 0.4, color: '#ffffff' });
            o.item('i', { item: 'minecraft:diamond', y: 1.0 });
            o.bar('b', { value: 25, max: 100, width: 1, height: 0.1, y: 0.8 });
            o.texture('p', { texture: 'modjs:textures/gui/pic.png', width: 0.5, height: 0.25, y: 1.4 });
            """);
        List<String> calls = draw(10.5, 65.5, 16);
        assertEquals("push 10.50,65.40,20.50 billboard", calls.get(0));
        assertEquals("text 'Generator' 0.20 ffffffff", calls.get(1));
        assertTrue(calls.contains("item minecraft:diamond 0.50"));
        // background bar and a quarter of the width filled
        assertTrue(calls.contains("quad -0.50,-0.05 1.00x0.10 c0000000"), calls.toString());
        assertTrue(calls.contains("quad -0.50,-0.05 0.25x0.10 ff3fdc5a"), calls.toString());
        assertTrue(calls.contains("texture modjs:textures/gui/pic.png 0.50x0.25 ffffffff"));
        assertEquals(calls.stream().filter(c -> c.startsWith("push")).count(), calls.stream().filter(c -> c.equals("pop")).count());
    }

    @Test
    void positionObjectAndSameHandle() {
        run("Overlay.at({ x: 1, y: 2, z: 3 }).text('a', { text: 'A' }); Overlay.at(1, 2, 3).text('b', { text: 'B' });");
        assertEquals(2, ((Number) run("Overlay.at(1, 2, 3).size")).intValue());
        assertEquals(1, WorldOverlay.positionCount());
        run("Overlay.at(1, 2, 3).remove('a')");
        assertEquals(1, ((Number) run("Overlay.at(1, 2, 3).size")).intValue());
        run("Overlay.at(1, 2, 3).clear()");
        assertEquals(0, draw(1, 2, 3).size());
    }

    @Test
    void faceElementsLieOnTheBlockFaceAndCullWhenSeenFromBehind() {
        run("Overlay.at(0, 0, 0).text('n', { text: 'N', face: 'north', x: 0.1, y: 0.2 }); Overlay.at(0, 0, 0).text('u', { text: 'U', face: 'up' });");
        // camera north of the block sees the north face and the top face from above
        List<String> fromNorth = draw(0.5, 3, -5);
        assertTrue(fromNorth.stream().anyMatch(c -> c.startsWith("push") && c.endsWith("north")), fromNorth.toString());
        assertTrue(fromNorth.stream().anyMatch(c -> c.endsWith(" up")), fromNorth.toString());
        // camera south of the block, below it: the north face and the top face both face away
        List<String> fromSouth = draw(0.5, -3, 5);
        assertTrue(fromSouth.isEmpty(), fromSouth.toString());
        // north face origin: 0.505 out, x slides to the left of the viewer (west to east is right-to-left on the north side)
        assertTrue(fromNorth.get(0).startsWith("push 0.40,0.70,"), fromNorth.get(0));
        assertTrue(fromNorth.get(0).endsWith(" north"), fromNorth.get(0));
    }

    @Test
    void distanceCullingUsesTheElementOrTheGlobalLimit() {
        run("Overlay.at(0, 0, 0).text('near', { text: 'near', distance: 5 }); Overlay.at(0, 0, 0).text('far', { text: 'far' });");
        assertEquals(2, draw(0.5, 1, 3).stream().filter(c -> c.startsWith("text")).count());
        assertEquals(1, draw(0.5, 1, 12).stream().filter(c -> c.startsWith("text")).count());
        run("Overlay.world.maxDistance = 8");
        assertEquals(0, draw(0.5, 1, 12).stream().filter(c -> c.startsWith("text")).count());
    }

    @Test
    void notLoadedPositionsAreSkipped() {
        run("Overlay.at(0, 0, 0).text('t', { text: 'x' }); Overlay.at(100, 0, 0).text('u', { text: 'y' });");
        Recorder recorder = new Recorder();
        WorldOverlay.draw(recorder, 50, 1, 0, key -> key != WorldOverlay.key(100, 0, 0));
        WorldOverlay.maxDistance = 200;
        recorder.calls.clear();
        WorldOverlay.draw(recorder, 50, 1, 0, key -> key != WorldOverlay.key(100, 0, 0));
        assertEquals(1, recorder.calls.stream().filter(c -> c.startsWith("text")).count());
        assertTrue(recorder.calls.contains("text 'x' 0.20 ffffffff"));
    }

    @Test
    void elementCapKeepsTheNearestPositions() {
        run("for (var i = 0; i < 10; i++) Overlay.at(i * 2, 0, 0).text('t', { text: 'p' + i });");
        run("Overlay.world.maxElements = 3");
        List<String> texts = draw(0, 1, 0).stream().filter(c -> c.startsWith("text")).toList();
        assertEquals(List.of("text 'p0' 0.20 ffffffff", "text 'p1' 0.20 ffffffff", "text 'p2' 0.20 ffffffff"), texts);
        assertEquals(3, ((Number) run("Overlay.world.maxElements")).intValue());
        assertEquals(10, ((Number) run("Overlay.world.positions")).intValue());
    }

    @Test
    void hiddenAndInvisibleElementsAreNotDrawn() {
        run("const o = Overlay.at(0, 0, 0); o.text('a', { text: 'a', visible: false }); o.text('b', { text: 'b', alpha: 0 }); o.text('c', { text: 'c' });");
        List<String> texts = draw(0, 1, 3).stream().filter(c -> c.startsWith("text")).toList();
        assertEquals(1, texts.size());
        assertTrue(texts.get(0).contains("'c'"));
    }

    @Test
    void layersOrderTheDrawing() {
        run("const o = Overlay.at(0, 0, 0); o.text('top', { text: 'top', layer: 5 }); o.text('bottom', { text: 'bottom', layer: 1 });");
        List<String> texts = draw(0, 1, 3).stream().filter(c -> c.startsWith("text")).toList();
        assertTrue(texts.get(0).contains("bottom"));
        assertTrue(texts.get(1).contains("top"));
    }

    @Test
    void elementsAnimateLikeHudElements() {
        run("globalThis.el = Overlay.at(0, 0, 0).bar('b', { value: 0, max: 100 }); el.animate({ value: 100 }, 1000);");
        draw(0, 1, 3);
        time.addAndGet(500);
        draw(0, 1, 3);
        assertEquals(50.0, ((Number) run("el.value")).doubleValue(), 1.0);
    }

    @Test
    void unknownOptionsAreAnError() {
        try {
            run("Overlay.at(0, 0, 0).text('t', { txet: 'typo' })");
            assertTrue(false, "expected an error");
        } catch (org.mozilla.javascript.RhinoException e) {
            assertTrue(e.getMessage().contains("unknown option 'txet'"), e.getMessage());
        }
    }

    @Test
    void templatesRunOnFirstSightAndWhenSyncedDataChanges() {
        run("globalThis.runs = []; Overlay.forBlock('modjs:smelter', (at, data, pos) => { runs.push(data.progress + '@' + pos.x); at.text('p', { text: 'P' + data.progress }); });");
        WorldOverlay.blockUpdated("modjs:smelter", 4, 70, 4, "{\"progress\":10}");
        WorldOverlay.blockUpdated("modjs:other", 5, 70, 4, "{}");
        WorldOverlay.refreshTemplates(4, 70, 4, 10_000);
        assertEquals("10@4", String.valueOf(run("runs.join(',')")));
        assertTrue(draw(4.5, 71, 4).stream().anyMatch(c -> c.contains("'P10'")));

        // unchanged data does not run again
        WorldOverlay.refreshTemplates(4, 70, 4, 10_200);
        assertEquals("10@4", String.valueOf(run("runs.join(',')")));

        // changed data runs again, but not more than once a second
        WorldOverlay.blockUpdated("modjs:smelter", 4, 70, 4, "{\"progress\":20}");
        WorldOverlay.refreshTemplates(4, 70, 4, 10_500);
        assertEquals("10@4", String.valueOf(run("runs.join(',')")));
        WorldOverlay.refreshTemplates(4, 70, 4, 11_100);
        assertEquals("10@4,20@4", String.valueOf(run("runs.join(',')")));
        assertTrue(draw(4.5, 71, 4).stream().anyMatch(c -> c.contains("'P20'")));
        assertFalse(draw(4.5, 71, 4).stream().anyMatch(c -> c.contains("'P10'")));

        // data is readable from scripts, and a removed block drops its overlay
        assertEquals(20.0, ((Number) run("Overlay.data(4, 70, 4).progress")).doubleValue());
        WorldOverlay.blockRemoved(4, 70, 4);
        assertEquals(0, draw(4.5, 71, 4).size());
    }

    @Test
    void templatesStayQuietOutOfRange() {
        run("globalThis.n = 0; Overlay.forBlock('modjs:smelter', (at) => { n++; });");
        WorldOverlay.blockUpdated("modjs:smelter", 500, 70, 500, "{}");
        WorldOverlay.refreshTemplates(0, 70, 0, 10_000);
        assertEquals(0, ((Number) run("n")).intValue());
        WorldOverlay.refreshTemplates(499, 70, 499, 11_000);
        assertEquals(1, ((Number) run("n")).intValue());
    }

    @Test
    void positionKeysRoundTripNegativeCoordinates() {
        long key = WorldOverlay.key(-30_000_000, -64, 29_999_999);
        assertEquals(-30_000_000, WorldOverlay.keyX(key));
        assertEquals(-64, WorldOverlay.keyY(key));
        assertEquals(29_999_999, WorldOverlay.keyZ(key));
        long other = WorldOverlay.key(12, 319, -7);
        assertEquals(12, WorldOverlay.keyX(other));
        assertEquals(319, WorldOverlay.keyY(other));
        assertEquals(-7, WorldOverlay.keyZ(other));
    }

    @Test
    void drawAtDrawsOnlyOnePositionButSharesTheCap() {
        run("Overlay.at(0, 0, 0).text('a', { text: 'A' }); Overlay.at(2, 0, 0).text('b', { text: 'B' });");
        Recorder recorder = new Recorder();
        WorldOverlay.drawAt(recorder, 2, 0, 0, 0, 1, 0, key -> true);
        assertEquals(1, recorder.calls.stream().filter(c -> c.startsWith("text")).count());
        assertTrue(recorder.calls.stream().anyMatch(c -> c.contains("'B'")));
        recorder.calls.clear();
        WorldOverlay.drawAt(recorder, 0, 0, 0, 0, 1, 0, key -> true);
        assertTrue(recorder.calls.stream().anyMatch(c -> c.contains("'A'")));
    }

    @Test
    void clearDropsTemplatesAndElements() {
        run("Overlay.at(0, 0, 0).text('t', { text: 'x' }); Overlay.forBlock('a:b', () => {});");
        assertTrue(WorldOverlay.any());
        run("Overlay.clear()");
        assertFalse(WorldOverlay.any());
    }
}
