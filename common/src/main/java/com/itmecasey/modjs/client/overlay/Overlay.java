package com.itmecasey.modjs.client.overlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.mozilla.javascript.Context;

/**
 * The retained overlay: elements that scripts create once and the game draws every frame (see {@link OverlayRenderer}).
 * The static methods are the registry; {@link Binding} is the {@code Overlay} global in client scripts.
 */
public final class Overlay {
    private static final Map<String, OverlayElement> ELEMENTS = new LinkedHashMap<>();
    private static LongSupplier clock = () -> System.nanoTime() / 1_000_000L;

    private Overlay() {
    }

    public static long now() {
        return clock.getAsLong();
    }

    /** Tests replace the clock. */
    public static void setClock(LongSupplier supplier) {
        clock = supplier == null ? () -> System.nanoTime() / 1_000_000L : supplier;
    }

    public static synchronized void clear() {
        ELEMENTS.clear();
        WorldOverlay.clear();
        WorldOutlines.clear();
    }

    public static synchronized OverlayElement get(String id) {
        return ELEMENTS.get(id);
    }

    public static synchronized OverlayElement remove(String id) {
        return ELEMENTS.remove(id);
    }

    public static synchronized void add(OverlayElement element) {
        ELEMENTS.remove(element.id);
        ELEMENTS.put(element.id, element);
    }

    public static synchronized boolean any(String surface) {
        for (OverlayElement element : ELEMENTS.values()) {
            if (element.surface.equals(surface)) {
                return true;
            }
        }
        return false;
    }

    static synchronized List<OverlayElement> elements(String surface) {
        List<OverlayElement> list = new ArrayList<>();
        for (OverlayElement element : ELEMENTS.values()) {
            if (element.surface.equals(surface)) {
                list.add(element);
            }
        }
        list.sort((a, b) -> Integer.compare(a.layer, b.layer));
        return list;
    }

    /** The {@code Overlay} global. */
    public static final class Binding {
        private static String checkId(String id) {
            if (id == null || id.isEmpty()) {
                throw Context.reportRuntimeError("Overlay: every element needs an id, for example Overlay.bar('hp', {...})");
            }
            return id;
        }

        private static <T extends OverlayElement> T register(T element, Object options) {
            element.apply(options);
            add(element);
            return element;
        }

        /** A progress bar with a value and a max. Replaces an element with the same id. */
        public OverlayBar bar(String id, Object options) {
            return register(new OverlayBar(checkId(id)), options);
        }

        public OverlayText text(String id, Object options) {
            return register(new OverlayText(checkId(id)), options);
        }

        public OverlayRect rect(String id, Object options) {
            return register(new OverlayRect(checkId(id)), options);
        }

        public OverlayImage image(String id, Object options) {
            return register(new OverlayImage(checkId(id)), options);
        }

        public OverlayItem item(String id, Object options) {
            return register(new OverlayItem(checkId(id)), options);
        }

        public OverlayElement get(String id) {
            return Overlay.get(id);
        }

        private static int coordinate(Object value, String name) {
            Object plain = com.itmecasey.modjs.json.JsonUtil.unwrap(value);
            if (plain instanceof Number number) {
                return (int) Math.floor(number.doubleValue());
            }
            throw Context.reportRuntimeError("Overlay.at: " + name + " must be a number");
        }

        /** The world overlay of a block position: {@code Overlay.at(x, y, z)} or {@code Overlay.at({x, y, z})}. */
        public WorldOverlay.At at(Object x, Object y, Object z) {
            return WorldOverlay.at(coordinate(x, "x"), coordinate(y, "y"), coordinate(z, "z"));
        }

        public WorldOverlay.At at(Object pos) {
            Object px = com.itmecasey.modjs.json.JsonUtil.property(pos, "x");
            Object py = com.itmecasey.modjs.json.JsonUtil.property(pos, "y");
            Object pz = com.itmecasey.modjs.json.JsonUtil.property(pos, "z");
            if (px == null || py == null || pz == null) {
                throw Context.reportRuntimeError("Overlay.at needs x, y and z: Overlay.at(1, 64, 2) or Overlay.at({ x: 1, y: 64, z: 2 })");
            }
            return WorldOverlay.at(coordinate(px, "x"), coordinate(py, "y"), coordinate(pz, "z"));
        }

        /** The synced data of the scripted block at a position, as an object (empty when there is none). */
        public Object data(Object x, Object y, Object z) {
            return com.itmecasey.modjs.script.ScriptBlockCalls.toClientObject(WorldOverlay.data(coordinate(x, "x"), coordinate(y, "y"), coordinate(z, "z")));
        }

        /**
         * Describes the overlay of every block of one id: {@code Overlay.forBlock('modjs:smelter', (at, data, pos) => {...})} runs when
         * such a block comes near and again when its synced data changes. {@code at} is cleared before each run.
         */
        public void forBlock(String blockId, org.mozilla.javascript.Function template) {
            WorldOverlay.forBlock(blockId, template);
        }

        /**
         * Outlines (and optionally tints) whole blocks, any block in the world: {@code Overlay.outline('veins', positions, { color: '#ffd24a', width: 2, fill: '#33ffd24a' })}.
         * {@code positions} is an array of {x, y, z} objects or [x, y, z] arrays, such as the result of {@code world.connected(...)}. Calling it again with the
         * same id replaces the blocks; the returned set has {@code setPositions}, {@code add}, {@code clear} and {@code remove}.
         */
        public WorldOutlines.Outline outline(String id, Object positions, Object options) {
            return WorldOutlines.put(id, positions, options);
        }

        public WorldOutlines.Outline outline(String id, Object positions) {
            return WorldOutlines.put(id, positions, null);
        }

        /** The outline set with this id, or null. */
        public WorldOutlines.Outline getOutline(String id) {
            return WorldOutlines.get(id);
        }

        /** Removes one outline set; returns whether it existed. */
        public boolean removeOutline(String id) {
            return WorldOutlines.remove(id);
        }

        /** Global limits of world overlays: {@code Overlay.world.maxDistance} and {@code maxElements}. */
        public WorldOverlay.Limits getWorld() {
            return new WorldOverlay.Limits();
        }

        public boolean has(String id) {
            return Overlay.get(id) != null;
        }

        public void remove(String id) {
            Overlay.remove(id);
        }

        /** Removes every element. Client scripts do this by themselves whenever they reload. */
        public void clear() {
            Overlay.clear();
        }
    }
}
