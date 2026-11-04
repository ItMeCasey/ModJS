package com.itmecasey.modjs;

import com.itmecasey.modjs.script.ErrorNotice;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorNoticeTest {
    @Test
    void summarizesAndTruncatesErrors() {
        ScriptManager manager = ScriptManager.of(ScriptType.CLIENT);
        int before = manager.errors().size();
        for (int i = 0; i < 7; i++) {
            manager.addError("boom " + i + " " + "x".repeat(200));
        }
        List<String> lines = ErrorNotice.lines(ScriptType.CLIENT);
        assertTrue(lines.get(0).contains((before + 7) + " script error"));
        assertEquals(1 + 5 + 1, lines.size());
        assertTrue(lines.get(1).length() <= 160);
        assertTrue(lines.get(lines.size() - 1).contains("/modjs errors"));
    }
}
