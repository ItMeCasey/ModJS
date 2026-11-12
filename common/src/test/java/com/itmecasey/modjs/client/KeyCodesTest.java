package com.itmecasey.modjs.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeyCodesTest {
    @Test
    void commonKeysMapToTheScancodesTheGameUses() {
        // values from Minecraft 26.3's InputConstants: KEY_A = 4, KEY_G = 10, KEY_0 = 39, KEY_GRAVE = 53, KEY_RIGHT = 79, KEY_RALT = 230
        assertEquals(4, KeyCodes.glfwToScancode(65));
        assertEquals(10, KeyCodes.glfwToScancode(71));
        assertEquals(30, KeyCodes.glfwToScancode(49));
        assertEquals(39, KeyCodes.glfwToScancode(48));
        assertEquals(53, KeyCodes.glfwToScancode(96));
        assertEquals(79, KeyCodes.glfwToScancode(262));
        assertEquals(230, KeyCodes.glfwToScancode(346));
        assertEquals(58, KeyCodes.glfwToScancode(290));
    }

    @Test
    void unboundAndUnknownKeysStayUnbound() {
        assertEquals(-1, KeyCodes.glfwToScancode(-1));
        assertEquals(-1, KeyCodes.glfwToScancode(9999));
    }

    @Test
    void everyMappedKeyRoundTrips() {
        for (int glfw = 0; glfw <= 348; glfw++) {
            int scancode = KeyCodes.glfwToScancode(glfw);
            if (scancode >= 0) {
                assertEquals(glfw, KeyCodes.scancodeToGlfw(scancode), "glfw " + glfw);
                assertEquals(true, scancode < 512, "scancode " + scancode + " must fit the SDL keyboard state");
            }
        }
        assertEquals(500, KeyCodes.scancodeToGlfw(500));
    }
}
