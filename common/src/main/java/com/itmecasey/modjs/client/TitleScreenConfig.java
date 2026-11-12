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
    public record Button(String label, String action, Function callback, Integer x, Integer y, int width, int height,
                         String icon, int iconSize, String iconSide, String id, Spot spot) {
        /** A button without an id (every button made before block screens). */
        public Button(String label, String action, Function callback, Integer x, Integer y, int width, int height,
                      String icon, int iconSize, String iconSide) {
            this(label, action, callback, x, y, width, height, icon, iconSize, iconSide, null, Spot.NONE);
        }

        /** Top left corner on a screen of this size; the second value is {@link Spot#STACK} when the button has no y and joins the stack. */
        public int[] place(int screenWidth, int screenHeight) {
            return spot.place(x, y, screenWidth, screenHeight, width, height, null);
        }
    }

    /**
     * Where a widget sits relative to the screen. Without an {@code anchor} the old rules hold ({@code x} from the center, {@code y} from the top);
     * with one, {@code x} and {@code y} are offsets from the anchor point and the widget's matching edge or corner aligns to it, the way overlay
     * elements work. {@code xPercent} and {@code yPercent} (from {@code '25%'}) add a share of the screen's width or height.
     */
    public record Spot(String anchor, Double xPercent, Double yPercent) {
        public static final Spot NONE = new Spot(null, null, null);
        /** Returned as y by {@link #place} when no y was given and the widget has no default. */
        public static final int STACK = Integer.MIN_VALUE;
        static final java.util.Set<String> ANCHORS = java.util.Set.of("topleft", "top", "topright", "left", "center", "right", "bottomleft", "bottom", "bottomright");

        public int[] place(Integer x, Integer y, int screenWidth, int screenHeight, int width, int height, Integer defaultY) {
            int offsetX = (x == null ? 0 : x) + (xPercent == null ? 0 : (int) Math.round(xPercent * screenWidth / 100.0));
            boolean hasY = y != null || yPercent != null;
            int offsetY = (y == null ? 0 : y) + (yPercent == null ? 0 : (int) Math.round(yPercent * screenHeight / 100.0));
            if (anchor != null) {
                return new int[] {(int) Math.round(com.itmecasey.modjs.client.overlay.OverlayRenderer.anchorX(anchor, screenWidth, width)) + offsetX,
                    (int) Math.round(com.itmecasey.modjs.client.overlay.OverlayRenderer.anchorY(anchor, screenHeight, height)) + offsetY};
            }
            int top = hasY ? offsetY : defaultY == null ? STACK : defaultY;
            return new int[] {screenWidth / 2 - width / 2 + offsetX, top};
        }
    }

    /**
     * A progress bar of a block screen. {@code value} and {@code max} are the name of a synced block value or a number written as text;
     * {@code x} and {@code y} count from the screen's top left corner.
     */
    public record Bar(String value, String max, int x, int y, int width, int height, int color, int background) {
    }

    public record Text(String text, int x, int y, int color, boolean centered, Spot spot) {
        public Text(String text, int x, int y, int color, boolean centered) {
            this(text, x, y, color, centered, Spot.NONE);
        }

        /** Top left corner of the drawn text, given its width and line height. */
        public int[] place(int screenWidth, int screenHeight, int textWidth, int lineHeight) {
            // without an anchor, centered text is centered on x and left text starts at x
            return spot.place(x, y, screenWidth, screenHeight, spot.anchor() != null || centered ? textWidth : 0, lineHeight, 0);
        }
    }

    /** A text box. {@code id} is how a button callback reads it back with {@code screen.getValue(id)}. */
    public record Input(String id, String placeholder, String initial, int x, int y, int width, int height, int maxLength, Spot spot) {
        public Input(String id, String placeholder, String initial, int x, int y, int width, int height, int maxLength) {
            this(id, placeholder, initial, x, y, width, height, maxLength, Spot.NONE);
        }

        /** Top left corner of the box on a screen of this size. */
        public int[] place(int screenWidth, int screenHeight) {
            return spot.place(x, y, screenWidth, screenHeight, width, height, null);
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<Input> inputs = new ArrayList<>();
    private final List<Bar> bars = new ArrayList<>();
    private int panelWidth = 176;
    private int panelHeight = 166;
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
        Spot spot = spot(options);
        Integer w = number(options, "width");
        Integer h = number(options, "height");
        Integer max = number(options, "maxLength");
        String placeholder = JsonUtil.asString(options == null ? null : JsonUtil.property(options, "placeholder"));
        String value = JsonUtil.asString(options == null ? null : JsonUtil.property(options, "value"));
        if (value == null) {
            // The wiki and the reference call this option "initial"; "value" is the older name and still works.
            value = JsonUtil.asString(options == null ? null : JsonUtil.property(options, "initial"));
        }
        inputs.add(new Input(id, placeholder == null ? "" : placeholder, value == null ? "" : value, x != null ? x : 0,
            y != null ? y : spot.yPercent() != null || spot.anchor() != null ? 0 : 40,
            w != null ? w : 200, h != null ? h : 20, max != null ? max : 64, spot));
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
        if (action instanceof org.mozilla.javascript.Scriptable && !(action instanceof Function)) {
            // block screens give buttons by options only: screen.button('Go', { id: 'go', x: 10, y: 20 })
            button(label, null, action);
            return;
        }
        button(label, action, null);
    }

    public void button(String label, Object action, Object options) {
        Integer x = number(options, "x");
        Integer y = number(options, "y");
        Integer w = number(options, "width");
        Integer h = number(options, "height");
        buttons.add(new Button(label, action == null || action instanceof Function ? null : String.valueOf(action),
            action instanceof Function f ? f : null, x, y, w != null ? w : 200, h != null ? h : 20,
            string(options, "icon"), number(options, "iconSize") != null ? Math.max(1, number(options, "iconSize")) : 16,
            string(options, "iconSide") != null ? string(options, "iconSide") : "left", string(options, "id"), spot(options)));
    }

    /** Size of a block screen's panel in pixels (default 176 by 166, the size of a chest or furnace screen). */
    public void size(int width, int height) {
        this.panelWidth = Math.max(64, width);
        this.panelHeight = Math.max(32, height);
    }

    /**
     * Adds a progress bar to a block screen. Options: {@code value} and {@code max} (a synced value name such as 'progress', or a number),
     * {@code x}, {@code y} (from the top left), {@code width}, {@code height}, {@code color}, {@code background}.
     */
    public void bar(Object options) {
        String value = nameOrNumber(options, "value");
        String max = nameOrNumber(options, "max");
        Integer x = number(options, "x");
        Integer y = number(options, "y");
        Integer w = number(options, "width");
        Integer h = number(options, "height");
        Integer color = number(options, "color");
        Integer background = number(options, "background");
        if (value == null) {
            throw org.mozilla.javascript.Context.reportRuntimeError("screen.bar needs a value, for example { value: 'progress', max: 'max', x: 80, y: 35 }");
        }
        bars.add(new Bar(value, max == null ? "100" : max, x != null ? x : 0, y != null ? y : 0, w != null ? w : 40, h != null ? h : 6,
            color != null ? 0xFF000000 | color : 0xFF3FDC5A, background != null ? 0xFF000000 | background : 0xFF373737));
    }

    /** Adds text. Options: {@code x} (offset from center), {@code y} (from top), {@code color}, {@code left} (not centered). */
    public void text(String text, Object options) {
        Integer color = number(options, "color");
        Integer x = number(options, "x");
        Integer y = number(options, "y");
        Object left = options == null ? null : JsonUtil.property(options, "left");
        Spot spot = spot(options);
        texts.add(new Text(text, x != null ? x : 0, y != null ? y : spot.yPercent() != null || spot.anchor() != null ? 0 : 20,
            color != null ? 0xFF000000 | color : 0xFFFFFFFF, !Boolean.TRUE.equals(left), spot));
    }

    public void text(String text) {
        text(text, null);
    }

    private static String nameOrNumber(Object options, String key) {
        Object value = options == null ? null : JsonUtil.property(options, key);
        if (value instanceof Number n) {
            return n.doubleValue() == Math.rint(n.doubleValue()) ? String.valueOf(n.longValue()) : String.valueOf(n.doubleValue());
        }
        return value == null || value instanceof org.mozilla.javascript.Undefined ? null : String.valueOf(value);
    }

    private static String string(Object options, String key) {
        Object value = options == null ? null : JsonUtil.property(options, key);
        return value == null || value instanceof org.mozilla.javascript.Undefined ? null : String.valueOf(value);
    }

    /** Reads {@code anchor} and any {@code '25%'} style x or y from an options object. */
    private static Spot spot(Object options) {
        String anchor = string(options, "anchor");
        if (anchor != null) {
            anchor = anchor.trim().toLowerCase(java.util.Locale.ROOT).replace("-", "").replace("_", "");
            if (!Spot.ANCHORS.contains(anchor)) {
                throw org.mozilla.javascript.Context.reportRuntimeError("Unknown anchor '" + anchor + "'. Use topleft, top, topright, left, center, right, bottomleft, bottom or bottomright.");
            }
        }
        Double xPercent = percent(options, "x");
        Double yPercent = percent(options, "y");
        return anchor == null && xPercent == null && yPercent == null ? Spot.NONE : new Spot(anchor, xPercent, yPercent);
    }

    private static Double percent(Object options, String key) {
        Object value = options == null ? null : JsonUtil.property(options, key);
        if (value instanceof CharSequence text && text.toString().trim().endsWith("%")) {
            try {
                return Double.parseDouble(text.toString().trim().replace("%", "").trim());
            } catch (NumberFormatException e) {
                throw org.mozilla.javascript.Context.reportRuntimeError("'" + key + "' must be a number or a percentage such as '25%', not '" + text + "'");
            }
        }
        return null;
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

    public List<Bar> bars() {
        return bars;
    }

    public int panelWidth() {
        return panelWidth;
    }

    public int panelHeight() {
        return panelHeight;
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
