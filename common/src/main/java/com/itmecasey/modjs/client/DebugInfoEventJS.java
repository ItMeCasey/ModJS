package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;

import java.util.ArrayList;
import java.util.List;

/** {@code ClientEvents.leftDebugInfo(event => event.add('text'))} and {@code rightDebugInfo}: extra lines on the F3 screen. */
public final class DebugInfoEventJS extends EventJS {
    private final List<String> lines = new ArrayList<>();

    public void add(String... text) {
        lines.addAll(List.of(text));
    }

    public List<String> lines() {
        return lines;
    }
}
