package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** The screens scripts defined with {@code ClientEvents.screens}, and the hooks the platform uses to open them. */
public final class ScriptedScreens {
    private static final Map<String, TitleScreenConfig> SCREENS = new LinkedHashMap<>();
    private static volatile Consumer<String> opener = name -> {
    };
    private static volatile Runnable closer = () -> {
    };

    private ScriptedScreens() {
    }

    /** What a script gets inside a button callback, to read text boxes and move between screens. */
    public interface Context {
        /** The text in the text box with this id, or an empty string. */
        String getValue(String id);

        void setValue(String id, String value);

        /** Closes this screen. */
        void close();

        /** Opens another scripted screen by name. */
        void open(String name);
    }

    public static synchronized TitleScreenConfig get(String name) {
        return SCREENS.get(name);
    }

    public static synchronized void replaceAll(Map<String, TitleScreenConfig> screens) {
        SCREENS.clear();
        SCREENS.putAll(screens);
    }

    public static void setHooks(Consumer<String> open, Runnable close) {
        opener = open;
        closer = close;
    }

    private static Consumer<String> screenshotter = name -> {
    };

    /** Set by the loader module: saves the current frame, with the screen overlays, as {@code screenshots/<name>} in the game folder. */
    public static void setScreenshotHook(Consumer<String> hook) {
        screenshotter = hook;
    }

    /** The event passed to {@code ClientEvents.screens(event => event.create('name', screen => {...}))}. */
    public static final class ScreensEventJS extends EventJS {
        private final Map<String, TitleScreenConfig> created = new LinkedHashMap<>();

        /** Defines a screen. The callback receives a config with the same methods as {@code ClientEvents.titleScreen}. */
        public void create(String name, Consumer<TitleScreenConfig> define) {
            TitleScreenConfig config = TitleScreenConfig.forScreen();
            define.accept(config);
            created.put(name, config);
        }

        Map<String, TitleScreenConfig> created() {
            return created;
        }
    }

    public static void rebuild(com.itmecasey.modjs.event.EventGroup group) {
        ScreensEventJS event = new ScreensEventJS();
        group.post("screens", event);
        replaceAll(event.created());
    }

    /** The {@code Client} global in scripts. */
    public static final class Binding {
        /**
         * The block under the crosshair as {@code {x, y, z, id, face, distance}} ({@code face} is the side you look at: up, down, north, south,
         * east or west), or null when you look at no block or are not in a world.
         */
        public org.mozilla.javascript.NativeObject lookingAt() {
            return ClientQueries.lookingAt();
        }

        /** The client's world for reading: {@code getBlock}, {@code getBlockId}, {@code matches}, {@code findBlocks}, {@code connected}. Null outside a world. */
        public com.itmecasey.modjs.worldcraft.LevelJS getWorld() {
            return ClientQueries.world();
        }

        /** The block at a position as {@code {id, properties, x, y, z}}, or null when that part of the world is not loaded. */
        public org.mozilla.javascript.NativeObject getBlock(int x, int y, int z) {
            com.itmecasey.modjs.worldcraft.LevelJS world = ClientQueries.world();
            return world == null ? null : world.getBlock(x, y, z);
        }

        /** Whether the key registered with {@code ClientEvents.keyBindings} under this id is held down right now. */
        public boolean isKeyHeld(String id) {
            return ClientQueries.keyHeld(id);
        }

        /** Opens a screen defined with {@code ClientEvents.screens}. */
        public void openScreen(String name) {
            if (get(name) == null) {
                throw org.mozilla.javascript.Context.reportRuntimeError("No scripted screen named '" + name + "'");
            }
            opener.accept(name);
        }

        public void closeScreen() {
            closer.run();
        }

        /**
         * Stops these kinds of toast from showing: {@code system}, {@code tutorial}, {@code recipe}, {@code advancement}, the id of a
         * system toast such as {@code unsecure_server_warning}, or {@code all}. Use {@code ClientEvents.toast} to decide per toast.
         */
        public void hideToasts(String... kinds) {
            ClientTweaks.hideToasts(kinds);
        }

        /** Shows a toast with a title and an optional message. */
        public void showToast(String title, String message) {
            ClientTweaks.showToast(title, message);
        }

        public void showToast(String title) {
            ClientTweaks.showToast(title, null);
        }

        /**
         * Saves the current frame, with the in-world overlays drawn on it, to {@code screenshots/<name>.png} in the game folder.
         * The name may use letters, digits, {@code _}, {@code -} and {@code .}. Used by the screenshot tours that document the mod.
         */
        public void screenshot(String name) {
            String clean = name == null ? "" : name.replaceAll("[^A-Za-z0-9_.-]", "_");
            if (clean.isEmpty()) {
                throw org.mozilla.javascript.Context.reportRuntimeError("Client.screenshot needs a name");
            }
            screenshotter.accept(clean.endsWith(".png") ? clean : clean + ".png");
        }

        /** Removes every toast on screen or waiting. */
        public void clearToasts() {
            ClientTweaks.clearToasts();
        }

        /** Turns off the tutorial hints, and with them their toasts. This changes the game's own tutorial setting. */
        public void disableTutorial() {
            ClientTweaks.disableTutorial();
        }

        /** Skips the "this world uses experimental settings" warning by answering it with "proceed". */
        public void skipExperimentalWarning(boolean skip) {
            ClientTweaks.setSkipExperimentalWarning(skip);
        }

        public void skipExperimentalWarning() {
            ClientTweaks.setSkipExperimentalWarning(true);
        }
    }
}
