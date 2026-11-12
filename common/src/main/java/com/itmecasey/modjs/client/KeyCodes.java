package com.itmecasey.modjs.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Script key codes are GLFW codes on every version. Minecraft 26.3 switched its input to SDL, where a key mapping holds an SDL
 * scancode (the game reads {@code SDL_GetKeyboardState} by that number), so on 26.3 the codes are translated at the two edges:
 * key bindings registered by scripts ({@link #glfwToScancode}) and the {@code keyPressed} event ({@link #scancodeToGlfw}).
 */
public final class KeyCodes {
    private static final Map<Integer, Integer> TO_SCANCODE = new HashMap<>();
    private static final Map<Integer, Integer> TO_GLFW = new HashMap<>();

    private KeyCodes() {
    }

    private static void map(int glfw, int scancode) {
        TO_SCANCODE.put(glfw, scancode);
        TO_GLFW.put(scancode, glfw);
    }

    static {
        for (int i = 0; i < 26; i++) {
            map(65 + i, 4 + i); // A-Z
        }
        for (int i = 1; i <= 9; i++) {
            map(48 + i, 29 + i); // 1-9
        }
        map(48, 39); // 0
        map(257, 40); // enter
        map(256, 41); // escape
        map(259, 42); // backspace
        map(258, 43); // tab
        map(32, 44); // space
        map(45, 45); // minus
        map(61, 46); // equal
        map(91, 47); // [
        map(93, 48); // ]
        map(92, 49); // backslash
        map(59, 51); // ;
        map(39, 52); // '
        map(96, 53); // grave accent
        map(44, 54); // ,
        map(46, 55); // .
        map(47, 56); // /
        map(280, 57); // caps lock
        for (int i = 0; i < 12; i++) {
            map(290 + i, 58 + i); // F1-F12
        }
        map(283, 70); // print screen
        map(281, 71); // scroll lock
        map(284, 72); // pause
        map(260, 73); // insert
        map(268, 74); // home
        map(266, 75); // page up
        map(261, 76); // delete
        map(269, 77); // end
        map(267, 78); // page down
        map(262, 79); // right
        map(263, 80); // left
        map(264, 81); // down
        map(265, 82); // up
        map(282, 83); // num lock
        map(331, 84); // keypad /
        map(332, 85); // keypad *
        map(333, 86); // keypad -
        map(334, 87); // keypad +
        map(335, 88); // keypad enter
        for (int i = 1; i <= 9; i++) {
            map(320 + i, 88 + i); // keypad 1-9
        }
        map(320, 98); // keypad 0
        map(330, 99); // keypad .
        map(348, 101); // menu
        map(341, 224); // left control
        map(340, 225); // left shift
        map(342, 226); // left alt
        map(343, 227); // left super
        map(345, 228); // right control
        map(344, 229); // right shift
        map(346, 230); // right alt
        map(347, 231); // right super
    }

    /** The SDL scancode for a GLFW key code; -1 (unbound) for -1 and for keys without a scancode. */
    public static int glfwToScancode(int glfw) {
        return TO_SCANCODE.getOrDefault(glfw, -1);
    }

    /** The GLFW key code for an SDL scancode, or the scancode itself when there is no GLFW equivalent. */
    public static int scancodeToGlfw(int scancode) {
        return TO_GLFW.getOrDefault(scancode, scancode);
    }
}
