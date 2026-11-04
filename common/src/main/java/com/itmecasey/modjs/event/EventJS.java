package com.itmecasey.modjs.event;

/** Base class for every event object handed to a script handler. */
public class EventJS {
    private boolean cancelled;

    /** Cancels the event; handlers registered after this one still run unless the event is posted with stop-on-cancel. */
    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
