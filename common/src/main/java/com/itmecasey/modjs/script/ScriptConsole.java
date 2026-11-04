package com.itmecasey.modjs.script;

import org.slf4j.Logger;

import java.util.StringJoiner;

/** The {@code console} object exposed to scripts. */
public final class ScriptConsole {
    private final ScriptType type;
    private final Logger logger;

    public ScriptConsole(ScriptType type, Logger logger) {
        this.type = type;
        this.logger = logger;
    }

    private static String join(Object[] args) {
        StringJoiner joiner = new StringJoiner(" ");
        for (Object arg : args) {
            joiner.add(String.valueOf(arg));
        }
        return joiner.toString();
    }

    public void log(Object... args) {
        logger.info("[{}] {}", type, join(args));
    }

    public void info(Object... args) {
        log(args);
    }

    public void debug(Object... args) {
        logger.debug("[{}] {}", type, join(args));
    }

    public void warn(Object... args) {
        logger.warn("[{}] {}", type, join(args));
    }

    public void error(Object... args) {
        logger.error("[{}] {}", type, join(args));
        ScriptManager.of(type).addError(join(args));
    }
}
