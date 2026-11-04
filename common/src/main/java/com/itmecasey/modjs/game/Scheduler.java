package com.itmecasey.modjs.game;

import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;

import java.util.ArrayList;
import java.util.List;

/** Runs script callbacks after a number of server ticks, once or repeatedly. */
public final class Scheduler {
    /** Handle returned to scripts; call {@code cancel()} to stop a repeating task. */
    public static final class Task {
        private final Function callback;
        private final Scriptable scope;
        private final int interval;
        private final boolean repeating;
        private int remaining;
        private boolean cancelled;

        private Task(Function callback, Scriptable scope, int ticks, boolean repeating) {
            this.callback = callback;
            this.scope = scope;
            this.interval = Math.max(1, ticks);
            this.remaining = this.interval;
            this.repeating = repeating;
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private Scheduler() {
    }

    public static synchronized Task after(int ticks, Function callback) {
        return add(new Task(callback, ScriptManager.of(ScriptType.SERVER).scope(), ticks, false));
    }

    public static synchronized Task every(int ticks, Function callback) {
        return add(new Task(callback, ScriptManager.of(ScriptType.SERVER).scope(), ticks, true));
    }

    private static Task add(Task task) {
        PENDING.add(task);
        return task;
    }

    static synchronized void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    /** Drops tasks from the previous script run; called when server scripts reload. */
    public static synchronized void reset() {
        clear();
    }

    static void tick() {
        List<Task> due = new ArrayList<>();
        synchronized (Scheduler.class) {
            TASKS.addAll(PENDING);
            PENDING.clear();
            for (Task task : TASKS) {
                if (!task.cancelled && --task.remaining <= 0) {
                    due.add(task);
                }
            }
        }
        for (Task task : due) {
            try {
                com.itmecasey.modjs.script.ModJSContextFactory.INSTANCE.call(cx ->
                    task.callback.call(cx, task.scope, task.scope, new Object[0]));
            } catch (RhinoException e) {
                ScriptManager.of(ScriptType.SERVER).reportError("scheduled task", e);
                task.cancelled = true;
            }
            if (task.repeating) {
                task.remaining = task.interval;
            } else {
                task.cancelled = true;
            }
        }
        synchronized (Scheduler.class) {
            TASKS.removeIf(task -> task.cancelled);
        }
    }
}
