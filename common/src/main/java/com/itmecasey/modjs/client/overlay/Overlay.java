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
