package com.itmecasey.modjs.script;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;

/** The {@code Java} global: {@code Java.loadClass("net.minecraft.world.item.Items")} for classes that pass the sandbox filter. */
public final class JavaBinding {
    public Object loadClass(String name) {
        if (!ModJSContextFactory.isVisible(name)) {
            throw Context.reportRuntimeError("Class " + name + " is not allowed in scripts");
        }
        Class<?> cls;
        try {
            cls = Class.forName(name, true, JavaBinding.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw Context.reportRuntimeError("Class " + name + " was not found");
        }
        Context cx = Context.getCurrentContext();
        Scriptable scope = ScriptRuntime.getTopCallScope(cx);
        return new NativeJavaClass(scope, cls);
    }

    public boolean exists(String name) {
        if (!ModJSContextFactory.isVisible(name)) {
            return false;
        }
        try {
            Class.forName(name, false, JavaBinding.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
