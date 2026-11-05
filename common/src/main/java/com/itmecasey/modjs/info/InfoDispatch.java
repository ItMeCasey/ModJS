package com.itmecasey.modjs.info;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.script.ScriptType;

import java.util.function.Predicate;

/**
 * Runs the {@code InfoEvents} handlers for the block or entity the player is looking at.
 *
 * <p>A handler written in {@code client_scripts} runs on the client with what the client knows. A handler in
 * {@code startup_scripts} or {@code server_scripts} runs on the server for blocks with a block entity and for entities,
 * where the real state lives; for a block without a block entity the server has nothing to add, so such a handler runs on
 * the client instead, with the block state.
 */
public final class InfoDispatch {
    private static final Predicate<ScriptType> NOT_CLIENT = type -> type != ScriptType.CLIENT;
    private static final Predicate<ScriptType> ONLY_CLIENT = type -> type == ScriptType.CLIENT;
    private static final Predicate<ScriptType> ANY = type -> true;

    private InfoDispatch() {
    }

    /** Whether any script registered info handlers, so the overlay can skip the work. */
    public static boolean active(String kind) {
        return EventGroups.INFO.hasHandlers(kind);
    }

    /** Runs the handlers that see the real state on the server. */
    public static InfoEventJS onServer(InfoEventJS event) {
        EventGroups.INFO.postFor(event.kind, event.id, event, NOT_CLIENT);
        return event;
    }

    /**
     * Runs the handlers that belong on the client. {@code serverCovers} says the server already ran the other handlers
     * for this target (a block entity or an entity), leaving only client script handlers here.
     */
    public static InfoEventJS onClient(InfoEventJS event, boolean serverCovers) {
        EventGroups.INFO.postFor(event.kind, event.id, event, serverCovers ? ONLY_CLIENT : ANY);
        return event;
    }
}
