package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * What {@code ClientEvents.titleScreen(event => ...)} describes: a replacement for the main menu. The platform module
 * turns it into a screen. {@code null} from {@link #current()} means the vanilla main menu stays.
 */
public final class TitleScreenConfig extends EventJS {
    private static volatile TitleScreenConfig current;

    /** A button. {@code action} is a keyword or a {@code connect:host} / {@code url:https://...} string, or {@code callback} is set. */
    public record Button(String label, String action, Function callback, Integer x, Integer y, int width, int height) {
    }

    public record Text(String text, int x, int y, int color, boolean centered) {
    }

    /** A text box. {@code id} is how a button callback reads it back with {@code screen.getValue(id)}. */
    public record Input(String id, String placeholder, String initial, int x, int y, int width, int height, int maxLength) {
    }

    private final List<Button> buttons = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<Input> inputs = new ArrayList<>();
    private boolean closeOnEsc;
    private String background = "panorama";
    private int backgroundColor = 0xFF101018;
    private String logo = "vanilla";
    private boolean showSplash = true;
    private boolean showVersion = true;

    /** A config with the defaults for a normal screen: dimmed game behind it, no logo, splash or version text, closes with Escape. */
    public static TitleScreenConfig forScreen() {
        TitleScreenConfig config = new TitleScreenConfig();
        config.background = "default";
        config.logo = "none";
        config.showSplash = false;
        config.showVersion = false;
        config.closeOnEsc = true;
        return config;
    }

    public static TitleScreenConfig current() {
        return current;
    }

    public static void set(TitleScreenConfig config) {
        current = config;
    }

    // -- script API ----------------------------------------------------------------------------------------------

    /**
     * Background: "panorama" (default), "dirt", a texture id such as "mymod:textures/gui/bg.png", or a color number
     * such as 0x101018.
     */
    public void background(Object value) {
        if (value instanceof Number number) {
            this.background = "color";
            this.backgroundColor = 0xFF000000 | number.intValue();
        } else {
            this.background = String.valueOf(value);
        }
    }

    /** Logo: "vanilla" (default), "none", or a texture id drawn centered near the top. */
    public void logo(String value) {
        this.logo = value;
    }

    public void showSplash(boolean show) {
        this.showSplash = show;
    }

    /** Whether Escape closes this screen (default true for screens, false for the title screen). */
    public void closeOnEsc(boolean close) {
        this.closeOnEsc = close;
    }

    /**
     * Adds a text box. Options: {@code placeholder}, {@code value}, {@code x} (offset from center), {@code y} (from top),
     * {@code width}, {@code height}, {@code maxLength}. A button callback receives the screen and reads it with {@code screen.getValue(id)}.
     */
    public void input(String id, Object options) {
        Integer x = number(options, "x");
        Integer y = number(options, "y");
        Integer w = number(options, "width");
        Integer h = number(options, "height");
        Integer max = number(options, "maxLength");
        String placeholder = JsonUtil.asString(options == null ? null : JsonUtil.property(options, "placeholder"));
        String value = JsonUtil.asString(options == null ? null : JsonUtil.property(options, "value"));
        if (value == null) {
            // The wiki and the reference call this option "initial"; "value" is the older name and still works.
            value = JsonUtil.asString(options == null ? null : JsonUtil.property(options, "initial"));
        }
        inputs.add(new Input(id, placeholder == null ? "" : placeholder, value == null ? "" : value, x != null ? x : 0, y != null ? y : 40,
            w != null ? w : 200, h != null ? h : 20, max != null ? max : 64));
    }

    public void input(String id) {
        input(id, null);
    }

    public void showVersion(boolean show) {
        this.showVersion = show;
    }

    /**
     * Adds a button. {@code action}: "singleplayer", "multiplayer", "options", "mods", "languages", "quit",
     * "connect:play.example.com", "url:https://example.com", or a function. Without {@code x}/{@code y} buttons stack in
     * the middle; with them, {@code x} is the offset from the screen center and {@code y} the offset from the top.
     */
    public void button(String label, Object action) {
        button(label, action, null);
    }

    public void button(String label, Object action, Object options) {
        Integer x = number(options, "x");
        Integer y = number(options, "y");
        Integer w = number(options, "width");
        Integer h = number(options, "height");
        buttons.add(new Button(label, action instanceof Function ? null : String.valueOf(action),
            action instanceof Function f ? f : null, x, y, w != null ? w : 200, h != null ? h : 20));
    }

    /** Adds text. Options: {@code x} (offset from center), {@code y} (from top), {@code color}, {@code left} (not centered). */
    public void text(String text, Object options) {
        Integer color = number(options, "color");
        Integer x = number(options, "x");
        Integer y = number(options, "y");
        Object left = options == null ? null : JsonUtil.property(options, "left");
        texts.add(new Text(text, x != null ? x : 0, y != null ? y : 20, color != null ? 0xFF000000 | color : 0xFFFFFFFF, !Boolean.TRUE.equals(left)));
    }

    public void text(String text) {
        text(text, null);
    }

    private static Integer number(Object options, String key) {
        Object value = options == null ? null : JsonUtil.property(options, key);
        return value instanceof Number n ? n.intValue() : null;
    }

    // -- read by the platform module -----------------------------------------------------------------------------

    public List<Button> buttons() {
        return buttons;
    }

    public List<Input> inputs() {
        return inputs;
    }

    public boolean closesOnEsc() {
        return closeOnEsc;
    }

    public List<Text> texts() {
        return texts;
    }

    public String backgroundKind() {
        return background;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public String logoKind() {
        return logo;
    }

    public boolean splashShown() {
        return showSplash;
    }

    public boolean versionShown() {
        return showVersion;
    }
}
