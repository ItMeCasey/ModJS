package com.itmecasey.modjs.script;

import java.util.ArrayList;
import java.util.List;

/** Short, player-facing summaries of script errors, shown in chat when a player joins. */
public final class ErrorNotice {
    private static final int MAX_LINES = 5;

    private ErrorNotice() {
    }

    /** The lines to show for these script types; empty when there are no errors. */
    public static List<String> lines(ScriptType... types) {
        List<String> all = new ArrayList<>();
        for (ScriptType type : types) {
            for (String error : ScriptManager.of(type).errors()) {
                all.add("[" + type + "] " + error);
            }
        }
        List<String> out = new ArrayList<>();
        if (all.isEmpty()) {
            return out;
        }
        out.add("ModJS: " + all.size() + " script error" + (all.size() == 1 ? "" : "s") + ".");
        for (int i = 0; i < Math.min(MAX_LINES, all.size()); i++) {
            String line = all.get(i);
            out.add(line.length() > 160 ? line.substring(0, 157) + "..." : line);
        }
        if (all.size() > MAX_LINES) {
            out.add("... and " + (all.size() - MAX_LINES) + " more. Run /modjs errors for the full list.");
        }
        return out;
    }
}
