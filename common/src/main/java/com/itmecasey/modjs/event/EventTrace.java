package com.itmecasey.modjs.event;

import java.util.function.Consumer;

/**
 * Debug trace behind {@code /modjs trace}. While on, every event bridge dispatches (even with no script handler) and each
 * posted event is reported to the sink. Frequent tick events are skipped unless {@code includeTicks} is set.
 */
public final class EventTrace {
    private static volatile Consumer<String> sink;
    private static volatile boolean includeTicks;

    private EventTrace() {
    }

    public static boolean enabled() {
        return sink != null;
    }

    public static void start(Consumer<String> target, boolean ticks) {
        includeTicks = ticks;
        sink = target;
    }

    public static void stop() {
        sink = null;
    }

    static void log(String group, String event, String id, EventJS eventObject) {
        Consumer<String> target = sink;
        if (target == null || (!includeTicks && event.equals("tick"))) {
            return;
        }
        String detail = eventObject.describe();
        target.accept(group + "." + event + (id != null ? ":" + id : "") + (detail.isEmpty() ? "" : "  " + detail));
    }
}
