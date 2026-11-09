package com.itmecasey.modjs.client.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

class OverlayTest {
    /** Records every drawing call as text so tests can read what was drawn. */
    static final class Recorder implements OverlayCanvas {
        final List<String> calls = new ArrayList<>();
        final int width;
        final int height;

        Recorder(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() { return width; }
        public int height() { return height; }
        public void fill(int x, int y, int w, int h, int argb) { calls.add("fill " + x + "," + y + " " + w + "x" + h + " " + Integer.toHexString(argb)); }
        public void text(String text, int x, int y, int argb, boolean shadow, float scale) { calls.add("text '" + text + "' " + x + "," + y + " " + Integer.toHexString(argb) + (shadow ? " shadow" : "") + " x" + scale); }
        public int textWidth(String text) { return text.length() * 6; }
        public int fontHeight() { return 9; }
        public void texture(String t, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th, int argb) { calls.add("texture " + t + " " + x + "," + y + " " + w + "x" + h + " uv " + u + "," + v + " " + rw + "x" + rh + " of " + tw + "x" + th + " " + Integer.toHexString(argb)); }
        public void item(String id, int x, int y, float scale) { calls.add("item " + id + " " + x + "," + y + " x" + scale); }
    }

    private final AtomicLong time = new AtomicLong(1000);
    private Context cx;
    private Scriptable scope;

    @BeforeEach
    void setUp() {
        Overlay.clear();
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
    }

    private Object run(String script) {
        return cx.evaluateString(scope, script, "test", 1, null);
    }

    @Test
    void easingHitsItsEndpoints() {
        for (String name : List.of("linear", "easeIn", "easeOut", "easeInOut", "easeOutBack", "bounce")) {
            assertEquals(0.0, Easing.apply(name, 0.0), 1e-9, name);
            assertEquals(1.0, Easing.apply(name, 1.0), 1e-9, name);
        }
        assertEquals(0.25, Easing.apply("easeIn", 0.5), 1e-9);
        assertTrue(Easing.apply("easeOutBack", 0.8) > 1.0, "back overshoots");
        assertFalse(Easing.known("wobble"));
    }

    @Test
    void colorsAcceptNumbersAndStrings() {
        assertEquals(0xFFFF0000, Colors.parse(0xFF0000, 0));
        assertEquals(0x80FF0000, Colors.parse(0x80FF0000L, 0));
        assertEquals(0xFF00FF00, Colors.parse("#00ff00", 0));
        assertEquals(0x8000FF00, Colors.parse("#8000ff00", 0));
        assertEquals(7, Colors.parse("nonsense", 7));
        assertEquals(0x80000000, Colors.withAlpha(0xFF000000, 0.5) & 0xFF000000);
        assertEquals(0xFF808080, Colors.mix(0xFF000000, 0xFFFFFFFF, 0.5), "midpoint is grey");
    }

    @Test
    void barFillsByValueAndMax() {
        run("Overlay.bar('hp', { x: 10, y: 20, width: 100, height: 8, value: 25, max: 100, color: '#ff0000', background: 0, border: 0 })");
        Recorder canvas = new Recorder(400, 300);
        OverlayRenderer.draw(canvas, "hud");
        long fills = canvas.calls.stream().filter(c -> c.startsWith("fill ") && c.contains(" 1x8 ")).count();
        assertEquals(25, fills, "25 percent of a 100 pixel bar is 25 one pixel columns: " + canvas.calls);
        assertEquals("fill 10,20 1x8 ffff0000", canvas.calls.get(0));
    }

    @Test
    void scriptsChangeValuesAfterCreation() {
        run("var bar = Overlay.bar('hp', { width: 50, height: 4, value: 10, max: 50, background: 0, border: 0 }); bar.value = 50;");
        Recorder canvas = new Recorder(100, 100);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(50, canvas.calls.stream().filter(c -> c.startsWith("fill ") && c.contains(" 1x4 ")).count());
    }

    @Test
    void barLabelReplacesTokens() {
        run("Overlay.bar('xp', { width: 60, height: 10, value: 30, max: 60, label: '{value}/{max} {percent}', background: 0, border: 0 })");
        Recorder canvas = new Recorder(200, 100);
        OverlayRenderer.draw(canvas, "hud");
        assertTrue(canvas.calls.stream().anyMatch(c -> c.startsWith("text '30/60 50%'")), canvas.calls.toString());
    }

    @Test
    void smoothBarEasesTowardTheNewValue() {
        run("var b = Overlay.bar('s', { width: 100, height: 2, value: 0, max: 100, smooth: 300, background: 0, border: 0 });");
        OverlayBar bar = (OverlayBar) Overlay.get("s");
        Recorder canvas = new Recorder(200, 100);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(0.0, bar.displayValue, 1e-9);
        bar.value = 100;
        time.addAndGet(100);
        OverlayRenderer.draw(canvas, "hud");
        assertTrue(bar.displayValue > 10 && bar.displayValue < 100, "part way: " + bar.displayValue);
        time.addAndGet(5000);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(100.0, bar.displayValue, 1e-9);
    }

    @Test
    void animateMovesFieldsOverTime() {
        run("Overlay.rect('r', { width: 10, height: 10 }).animate({ x: 100, alpha: 0.5 }, 1000, 'linear');");
        OverlayElement rect = Overlay.get("r");
        Recorder canvas = new Recorder(400, 300);
        OverlayRenderer.draw(canvas, "hud");
        time.addAndGet(500);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(50.0, rect.x, 1e-6);
        assertEquals(0.75, rect.alpha, 1e-6);
        time.addAndGet(600);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(100.0, rect.x, 1e-9);
        assertFalse(rect.isAnimating());
    }

    @Test
    void fadeOutEndsInvisibleAndFadeInStartsAtZero() {
        run("Overlay.text('t', { text: 'hi' }).fadeOut(200);");
        OverlayElement text = Overlay.get("t");
        Recorder canvas = new Recorder(100, 100);
        OverlayRenderer.draw(canvas, "hud");
        time.addAndGet(300);
        canvas.calls.clear();
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(0.0, text.alpha, 1e-9);
        assertTrue(canvas.calls.isEmpty(), "fully faded elements draw nothing");
        text.fadeIn(100);
        assertEquals(0.0, text.alpha, 1e-9);
    }

    @Test
    void pulseKeepsGoingBetweenTwoAlphas() {
        run("Overlay.text('p', { text: 'x' }).pulse(1000, 0.2, 1.0);");
        OverlayElement text = Overlay.get("p");
        Recorder canvas = new Recorder(100, 100);
        OverlayRenderer.draw(canvas, "hud");
        time.addAndGet(500);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(0.2, text.alpha, 1e-6, "half a period later it is at the low point");
        time.addAndGet(500);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(1.0, text.alpha, 1e-6);
        assertTrue(text.isAnimating());
        text.stop();
        assertFalse(text.isAnimating());
    }

    @Test
    void textSupportsScaleShadowOutlineAndAlignment() {
        run("Overlay.text('t', { x: 10, y: 10, text: 'AB', color: '#ffffff', scale: 2, shadow: false, outline: '#000000', anchor: 'topright' })");
        Recorder canvas = new Recorder(200, 100);
        OverlayRenderer.draw(canvas, "hud");
        // 'AB' is 12 wide at scale 1, so 24 wide at scale 2, right anchored: 200 - 24 + 10
        assertTrue(canvas.calls.contains("text 'AB' 186,10 ffffffff x2.0"), canvas.calls.toString());
        assertEquals(4, canvas.calls.stream().filter(c -> c.contains("ff000000")).count(), "four outline copies");
    }

    @Test
    void anchorsPlaceElementsRelativeToTheScreen() {
        assertEquals(0.0, OverlayRenderer.anchorX("topleft", 400, 20));
        assertEquals(190.0, OverlayRenderer.anchorX("top", 400, 20));
        assertEquals(380.0, OverlayRenderer.anchorX("bottomright", 400, 20));
        assertEquals(140.0, OverlayRenderer.anchorY("center", 300, 20));
        assertEquals(280.0, OverlayRenderer.anchorY("bottom", 300, 20));
        assertEquals(0.0, OverlayRenderer.anchorY("topright", 300, 20));
    }

    @Test
    void layersAndSurfacesDecideWhatIsDrawnAndInWhichOrder() {
        run("Overlay.rect('top', { layer: 5, color: '#ff0000' }); Overlay.rect('bottom', { layer: 1, color: '#00ff00' }); Overlay.rect('menu', { surface: 'screen', color: '#0000ff' });");
        Recorder canvas = new Recorder(100, 100);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(2, canvas.calls.size());
        assertTrue(canvas.calls.get(0).endsWith("ff00ff00") && canvas.calls.get(1).endsWith("ffff0000"));
        assertTrue(Overlay.any("screen"));
        Recorder screen = new Recorder(100, 100);
        OverlayRenderer.draw(screen, "screen");
        assertEquals(1, screen.calls.size());
    }

    @Test
    void rectGradientBlendsAndImageAndItemUseTheirOwnCalls() {
        run("Overlay.rect('g', { width: 3, height: 1, color: '#000000', color2: '#ffffff', horizontal: true });"
            + "Overlay.image('i', { texture: 'pack:textures/gui/a.png', x: 5, y: 6, width: 32, height: 16, texWidth: 64, texHeight: 64, u: 8, v: 4, regionWidth: 16, regionHeight: 8 });"
            + "Overlay.item('it', { item: 'diamond', x: 1, y: 2, scale: 2 });");
        Recorder canvas = new Recorder(100, 100);
        OverlayRenderer.draw(canvas, "hud");
        assertEquals(3, canvas.calls.stream().filter(c -> c.startsWith("fill ")).count());
        assertTrue(canvas.calls.contains("texture pack:textures/gui/a.png 5,6 32x16 uv 8.0,4.0 16x8 of 64x64 ffffffff"), canvas.calls.toString());
        assertTrue(canvas.calls.contains("item minecraft:diamond 1,2 x2.0"), canvas.calls.toString());
        run("Overlay.image('whole', { texture: 'pack:textures/gui/b.png', x: 1, y: 1, width: 8, height: 8, tint: '#80ffffff' })");
        Recorder whole = new Recorder(100, 100);
        OverlayRenderer.draw(whole, "hud");
        assertTrue(whole.calls.contains("texture pack:textures/gui/b.png 1,1 8x8 uv 0.0,0.0 1x1 of 1x1 80ffffff"), whole.calls.toString());
    }

    @Test
    void typosAndBadValuesAreErrorsInsteadOfSilence() {
        RhinoException e = assertThrows(RhinoException.class, () -> run("Overlay.bar('x', { widht: 10 })"));
        assertTrue(e.getMessage().contains("unknown option 'widht'"), e.getMessage());
        e = assertThrows(RhinoException.class, () -> run("Overlay.rect('x', {}).animate({ color: 3 }, 100)"));
        assertTrue(e.getMessage().contains("not a number field"), e.getMessage());
        e = assertThrows(RhinoException.class, () -> run("Overlay.rect('x', {}).animate({ x: 3 }, 100, 'wobble')"));
        assertTrue(e.getMessage().contains("unknown easing"), e.getMessage());
        assertThrows(RhinoException.class, () -> run("Overlay.bar('', {})"));
    }

    @Test
    void sameIdReplacesAndRemoveAndClearWork() {
        run("Overlay.text('a', { text: 'one' }); Overlay.text('a', { text: 'two' });");
        assertEquals("two", ((OverlayText) Overlay.get("a")).text);
        assertEquals(Boolean.TRUE, run("Overlay.has('a')"));
        run("Overlay.remove('a')");
        assertFalse(Overlay.any("hud"));
        run("Overlay.text('b', {}); Overlay.clear();");
        assertFalse(Overlay.any("hud"));
    }
}
