package com.itmecasey.modjs.info;

import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The event passed to {@code InfoEvents.block(...)} and {@code InfoEvents.entity(...)} handlers. The handler adds lines
 * to the overlay of Show Me What This Is and Jade for the block or entity the player is looking at.
 */
public final class InfoEventJS extends EventJS {
    /** {@code block} or {@code entity}. */
    public final String kind;
    /** Registry name of the block or entity type, such as {@code mymod:lamp}. */
    public final String id;
    /** {@code server} when the handler sees the real state, {@code client} when it sees what the client knows. */
    public final String side;
    /** Whether the player asked for the long form (sneaking). */
    public final boolean detailed;
    public final int x;
    public final int y;
    public final int z;

    /** The block state (blocks only): {@code event.state.getValue('lit')}. */
    public Object state;
    /** The block entity wrapper (blocks with one, either side): items, nbt. */
    public Object blockEntity;
    /** The entity (entities only). */
    public Object entity;

    private Supplier<String> nbt = () -> "";
    private final List<InfoLine> lines = new ArrayList<>();
    private final Set<String> hiddenTags = new LinkedHashSet<>();

    public InfoEventJS(String kind, String id, String side, boolean detailed, int x, int y, int z) {
        this.kind = kind;
        this.id = id;
        this.side = side;
        this.detailed = detailed;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public InfoEventJS withNbt(Supplier<String> nbt) {
        this.nbt = nbt;
        return this;
    }

    @Override
    public String describe() {
        return kind + " " + id + " (" + side + ")";
    }

    /** The saved data of the block entity or entity as SNBT text, or an empty string when there is none. */
    public String getNbt() {
        try {
            return nbt.get();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** True on the server, where block entities and entities hold their real state. */
    public boolean isServer() {
        return side.equals("server");
    }

    public List<InfoLine> lines() {
        return lines;
    }

    /** Jade tags (such as {@code jade:energy_storage}) a script asked to hide; only Jade can remove another mod's lines. */
    public Set<String> hiddenTags() {
        return hiddenTags;
    }

    /** Adds a line of text to the body. */
    public void add(Object text) {
        add(text, null);
    }

    /** Adds a line of text. Options: {@code color} (number or "#rrggbb"), {@code position} (head, body, tail, badge), {@code item}, {@code count}. */
    public void add(Object text, Object options) {
        String item = itemOf(JsonUtil.property(options, "item"));
        int count = intOf(JsonUtil.property(options, "count"), 1);
        int color = colorOf(JsonUtil.property(options, "color"), -1);
        lines.add(new InfoLine(text, color, InfoLine.NO_BAR, 0xFFFFFF, item, count, positionOf(JsonUtil.property(options, "position"))));
    }

    /** Adds a line of text with a bar under it: {@code event.bar('Power', 0.4, '#ffaa00')}; the fraction is clamped to 0 to 1. */
    public void bar(Object text, double fraction, Object color) {
        float clamped = (float) Math.max(0.0, Math.min(1.0, Double.isNaN(fraction) ? 0.0 : fraction));
        lines.add(new InfoLine(text, -1, clamped, colorOf(color, 0x55FF55), "", 1, "body"));
    }

    public void bar(Object text, double fraction) {
        bar(text, fraction, null);
    }

    /** Adds a line of text with a small icon of an item before it: {@code event.item('minecraft:coal', 'Fuel', 8)}. */
    public void item(Object itemId, Object text, Object count) {
        lines.add(new InfoLine(text, -1, InfoLine.NO_BAR, 0xFFFFFF, itemOf(itemId), intOf(count, 1), "body"));
    }

    public void item(Object itemId, Object text) {
        item(itemId, text, null);
    }

    /** Hides the lines a Jade provider added under a tag, such as {@code jade:energy_storage}. Ignored by Show Me What This Is. */
    public void hideTag(String tag) {
        if (tag != null && !tag.isBlank()) {
            hiddenTags.add(tag);
        }
    }

    private static String itemOf(Object raw) {
        String id = JsonUtil.asString(raw);
        if (id == null || id.isBlank()) {
            return "";
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static int intOf(Object raw, int fallback) {
        return raw instanceof Number number ? Math.max(1, number.intValue()) : fallback;
    }

    /** A color given as a number (0xRRGGBB) or a string such as "#ffaa00"; anything else is the fallback. */
    static int colorOf(Object raw, int fallback) {
        raw = JsonUtil.unwrap(raw);
        if (raw instanceof Number number) {
            return number.intValue() & 0xFFFFFF;
        }
        if (raw != null) {
            String text = Context.toString(raw).trim().toLowerCase(Locale.ROOT);
            if (text.startsWith("#")) {
                text = text.substring(1);
            } else if (text.startsWith("0x")) {
                text = text.substring(2);
            }
            if (text.matches("[0-9a-f]{6}")) {
                return Integer.parseInt(text, 16);
            }
        }
        return fallback;
    }

    private static String positionOf(Object raw) {
        String position = JsonUtil.asString(raw);
        if (position == null) {
            return "body";
        }
        position = position.toLowerCase(Locale.ROOT);
        return switch (position) {
            case "head", "tail", "badge" -> position;
            default -> "body";
        };
    }
}
