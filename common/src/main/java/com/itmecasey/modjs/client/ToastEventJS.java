package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;

/**
 * The event passed to {@code ClientEvents.toast} just before a toast (the small popup in the corner) is shown. Call
 * {@link #hide()} to drop it, or change the text of system toasts with {@link #setTitle} and {@link #setMessage}.
 */
public final class ToastEventJS extends EventJS {
    private final String type;
    private final String id;
    private final String title;
    private final String message;
    private String newTitle;
    private String newMessage;

    ToastEventJS(String type, String id, String title, String message) {
        this.type = type;
        this.id = id;
        this.title = title;
        this.message = message;
    }

    /** The kind of toast: {@code system}, {@code advancement}, {@code recipe}, {@code tutorial}, or the lower-cased class name. */
    public String getType() {
        return type;
    }

    /** For system toasts the specific one, such as {@code narrator_toggle}, {@code world_backup} or {@code unsecure_server_warning}; otherwise empty. */
    public String getId() {
        return id;
    }

    /** The toast's title, or null when it could not be read. */
    public String getTitle() {
        return title;
    }

    /** The toast's message, or null when it has none or could not be read. */
    public String getMessage() {
        return message;
    }

    /** Stops the toast from showing. */
    public void hide() {
        cancel();
    }

    /** Replaces the title of a system toast. */
    public void setTitle(String text) {
        newTitle = text;
    }

    /** Replaces the message of a system toast. */
    public void setMessage(String text) {
        newMessage = text;
    }

    String newTitle() {
        return newTitle;
    }

    String newMessage() {
        return newMessage;
    }

    @Override
    public String describe() {
        return "type=" + type + (id.isEmpty() ? "" : " id=" + id) + (title == null ? "" : " title=" + title);
    }
}
