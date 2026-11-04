package com.itmecasey.modjs;

import com.itmecasey.modjs.client.SplashEventJS;
import com.itmecasey.modjs.client.TitleScreenConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
