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
    }
}
