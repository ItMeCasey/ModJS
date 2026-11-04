package com.itmecasey.modjs.script;

import com.itmecasey.modjs.event.EventGroup;
import com.itmecasey.modjs.event.EventGroups;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/** Installs the globals every script sees: console, event groups, and helper namespaces. */
final class Bindings {
    private static Scriptable sharedGlobal;

    private Bindings() {
    }

    static void install(ScriptType type, Context cx, ScriptableObject scope, ScriptConsole console) {
        ScriptableObject.putProperty(scope, "console", Context.javaToJS(console, scope));
        for (EventGroup group : EventGroups.all().values()) {
            ScriptableObject.putProperty(scope, group.groupName(), group);
        }
        ScriptableObject.putProperty(scope, "global", sharedGlobal(cx));
        ScriptableObject.putProperty(scope, "Java", Context.javaToJS(new JavaBinding(), scope));
        ExtraBindings.install(type, cx, scope);
    }

    private static synchronized Scriptable sharedGlobal(Context cx) {
        if (sharedGlobal == null) {
            sharedGlobal = cx.newObject(cx.initSafeStandardObjects());
        }
        return sharedGlobal;
    }
}
