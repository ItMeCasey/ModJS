package com.itmecasey.modjs.event;

import java.util.function.BooleanSupplier;

/**
 * Marks block breaks that a script started with {@code event.breakBlock(x, y, z)}, so the {@code BlockEvents.broken} handlers do not run
 * again for them (a vein miner that breaks every connected log from inside {@code BlockEvents.broken} would otherwise start over for each
 * log). Scripts that want the chain can ask for it with {@code event.breakBlock(x, y, z, true)}; nesting stops at {@link #MAX_DEPTH}.
 */
public final class ScriptBreak {
    /** How many script-started breaks may nest when scripts ask for events. */
    public static final int MAX_DEPTH = 3;
    /** The farthest a script may break a block from the player, in blocks. */
    public static final double MAX_DISTANCE = 64.0;

    private static final ThreadLocal<int[]> STATE = ThreadLocal.withInitial(() -> new int[2]); // depth, events

    private ScriptBreak() {
    }

    /** True while a break started by a script is in progress on this thread. */
    public static boolean active() {
        return STATE.get()[0] > 0;
    }

    /** True when {@code BlockEvents.broken} handlers must be skipped for the break in progress. */
    public static boolean suppressesEvents() {
        int[] state = STATE.get();
        return state[0] > 0 && state[1] == 0;
    }

    /** Runs a break as a script-started one. Returns false without running when the nesting limit is reached. */
    public static boolean run(boolean fireEvents, BooleanSupplier action) {
        int[] state = STATE.get();
        if (state[0] >= MAX_DEPTH) {
            return false;
        }
        int depth = state[0];
        int events = state[1];
        state[0] = depth + 1;
        state[1] = fireEvents ? 1 : 0;
        try {
            return action.getAsBoolean();
        } finally {
            state[0] = depth;
            state[1] = events;
        }
    }
}
