package com.itmecasey.modjs;

import com.itmecasey.modjs.client.SplashEventJS;
import com.itmecasey.modjs.client.TitleScreenConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleScreenTest {
    @Test
    void splashEditing() {
        SplashEventJS splashes = new SplashEventJS(List.of("Awesome!", "Also cool", "100% pure!"));
        splashes.add("Hello there", "  ");
        splashes.remove("cool");
        assertEquals(List.of("Awesome!", "100% pure!", "Hello there"), splashes.getLines());
        splashes.set("Only this");
        assertEquals("Only this\n", splashes.toFile());
        splashes.clear();
        assertEquals("\n", splashes.toFile());
    }

    @Test
    void scriptedScreensHaveScreenDefaultsAndInputs() {
        TitleScreenConfig screen = TitleScreenConfig.forScreen();
        assertEquals("default", screen.backgroundKind());
        assertEquals("none", screen.logoKind());
        assertTrue(screen.closesOnEsc());
        assertFalse(screen.splashShown());
        screen.input("name", null);
        screen.button("OK", "close");
        assertEquals(1, screen.inputs().size());
        assertEquals(200, screen.inputs().get(0).width());
        assertEquals(64, screen.inputs().get(0).maxLength());
        assertEquals("close", screen.buttons().get(0).action());
        assertFalse(new TitleScreenConfig().closesOnEsc());
        assertNull(com.itmecasey.modjs.client.ScriptedScreens.get("missing"));
    }

    @Test
    void inputStartingTextCanBeCalledInitialOrValue() {
        TitleScreenConfig screen = TitleScreenConfig.forScreen();
        org.mozilla.javascript.NativeObject initial = new org.mozilla.javascript.NativeObject();
        initial.put("initial", initial, "ModJS");
        org.mozilla.javascript.NativeObject value = new org.mozilla.javascript.NativeObject();
        value.put("value", value, "Older");
        screen.input("a", initial);
        screen.input("b", value);
        assertEquals("ModJS", screen.inputs().get(0).initial());
        assertEquals("Older", screen.inputs().get(1).initial());
    }

    @Test
    void titleScreenDescription() {
        TitleScreenConfig config = new TitleScreenConfig();
        config.background(0x112233);
        config.button("Play", "multiplayer");
        config.button("Hub", "connect:play.example.com", null);
        config.text("Welcome");
        assertEquals("color", config.backgroundKind());
        assertEquals(0xFF112233, config.backgroundColor());
        assertEquals(2, config.buttons().size());
        assertEquals("connect:play.example.com", config.buttons().get(1).action());
        assertEquals(200, config.buttons().get(0).width());
        assertEquals(1, config.texts().size());
        assertTrue(config.texts().get(0).centered());
        TitleScreenConfig.set(null);
        assertNull(TitleScreenConfig.current());
    }

    private static org.mozilla.javascript.NativeObject options(Object... pairs) {
        org.mozilla.javascript.NativeObject object = new org.mozilla.javascript.NativeObject();
        for (int i = 0; i < pairs.length; i += 2) {
            object.put((String) pairs[i], object, pairs[i + 1]);
        }
        return object;
    }

    @Test
    void buttonsWithoutAnAnchorKeepTheOldPlacement() {
        TitleScreenConfig config = new TitleScreenConfig();
        config.button("A", "quit", options("x", 10, "y", 50));
        config.button("B", "quit");
        // centered column plus x offset, y from the top; a button without y asks to be stacked
        assertEquals(960 / 2 - 100 + 10, config.buttons().get(0).place(960, 540)[0]);
        assertEquals(50, config.buttons().get(0).place(960, 540)[1]);
        assertEquals(TitleScreenConfig.Spot.STACK, config.buttons().get(1).place(960, 540)[1]);
    }

    @Test
    void anchoredWidgetsFollowTheScreenSize() {
        TitleScreenConfig config = new TitleScreenConfig();
        config.button("Singleplayer", "singleplayer", options("anchor", "left", "x", 12, "y", -36, "width", 130));
        config.button("Exit", "quit", options("anchor", "bottomright", "x", -8, "y", -8, "width", 50, "height", 20));
        config.button("Middle", "quit", options("anchor", "center", "width", 100, "height", 20));
        // left edge 12 from the screen's left edge, vertically centered, then shifted by y
        assertEquals(12, config.buttons().get(0).place(960, 540)[0]);
        assertEquals((540 - 20) / 2 - 36, config.buttons().get(0).place(960, 540)[1]);
        // a bottom right button hugs the corner by its own size, so a resize keeps it there
        assertEquals(960 - 50 - 8, config.buttons().get(1).place(960, 540)[0]);
        assertEquals(540 - 20 - 8, config.buttons().get(1).place(960, 540)[1]);
        assertEquals(1920 - 50 - 8, config.buttons().get(1).place(1920, 1080)[0]);
        assertEquals((960 - 100) / 2, config.buttons().get(2).place(960, 540)[0]);
        assertEquals((540 - 20) / 2, config.buttons().get(2).place(960, 540)[1]);
    }

    @Test
    void percentOffsetsAndTextAndInputAnchors() {
        TitleScreenConfig config = new TitleScreenConfig();
        config.button("Quarter", "quit", options("anchor", "topleft", "x", "25%", "y", "10%", "width", 50));
        config.text("Hello", options("anchor", "top", "y", 6));
        config.text("Left", options("x", -20, "left", true));
        config.text("Centered", options("x", 5));
        config.input("name", options("anchor", "bottom", "y", -30, "width", 100));
        assertEquals(200, config.buttons().get(0).place(800, 600)[0]);
        assertEquals(60, config.buttons().get(0).place(800, 600)[1]);
        // anchored text aligns by its own width; unanchored text keeps centered or left start behavior
        assertEquals((800 - 30) / 2, config.texts().get(0).place(800, 600, 30, 9)[0]);
        assertEquals(6, config.texts().get(0).place(800, 600, 30, 9)[1]);
        assertEquals(800 / 2 - 20, config.texts().get(1).place(800, 600, 30, 9)[0]);
        assertEquals(800 / 2 + 5 - 15, config.texts().get(2).place(800, 600, 30, 9)[0]);
        assertEquals(20, config.texts().get(2).place(800, 600, 30, 9)[1]);
        assertEquals((800 - 100) / 2, config.inputs().get(0).place(800, 600)[0]);
        assertEquals(600 - 20 - 30, config.inputs().get(0).place(800, 600)[1]);
    }

    @Test
    void anAnchorMustBeOneOfTheOverlayNames() {
        TitleScreenConfig config = new TitleScreenConfig();
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> config.button("A", "quit", options("anchor", "middle")));
        config.button("B", "quit", options("anchor", "Top-Left"));
        assertEquals("topleft", config.buttons().get(0).spot().anchor());
    }
}
