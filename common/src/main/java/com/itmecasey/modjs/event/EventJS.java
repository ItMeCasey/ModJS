package com.itmecasey.modjs.event;

/** Base class for every event object handed to a script handler. */
public class EventJS {
    private boolean cancelled;

    /** Cancels the event; handlers registered after this one still run unless the event is posted with stop-on-cancel. */
    public void cancel() {
        cancelled = true;
    }

    /** A short summary shown by {@code /modjs trace}; subclasses add the player, block, item or entity involved. */
    public String describe() {
        return "";
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
