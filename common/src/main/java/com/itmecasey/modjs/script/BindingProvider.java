package com.itmecasey.modjs.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

/** Adds platform-specific globals (Utils, Text, Network, ...) to every script scope. */
@FunctionalInterface
public interface BindingProvider {
    void install(ScriptType type, Context cx, ScriptableObject scope);
}
