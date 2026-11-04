package com.itmecasey.modjs.script;

import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.WrapFactory;

import java.util.List;
import java.util.Set;

/**
 * Creates sandboxed Rhino contexts. Scripts run interpreted (no bytecode generation) so they work under
 * Forge's module class loaders, and they can only see classes that pass {@link #isVisible(String)}.
 */
public final class ModJSContextFactory extends ContextFactory {
    public static final ModJSContextFactory INSTANCE = new ModJSContextFactory();

    private static final List<String> DENIED_PREFIXES = List.of(
        "java.io.", "java.nio.", "java.net.", "java.lang.reflect.", "java.lang.invoke.", "java.lang.foreign.",
        "java.lang.management.", "java.lang.instrument.", "java.sql.", "java.rmi.", "javax.script.",
        "sun.", "jdk.", "org.mozilla.javascript.", "java.util.concurrent.ThreadPool",
        "net.minecraftforge.fml.loading.", "cpw.mods."
    );

    private static final Set<String> DENIED_CLASSES = Set.of(
        "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.Process", "java.lang.ProcessHandle",
        "java.lang.System", "java.lang.Thread", "java.lang.ThreadGroup", "java.lang.ClassLoader", "java.lang.Class",
        "java.lang.Module", "java.lang.ModuleLayer", "java.lang.SecurityManager", "java.lang.StackWalker",
        "com.itmecasey.modjs.script.ScriptManager", "com.itmecasey.modjs.script.ModJSContextFactory"
    );

    private static final WrapFactory WRAP_FACTORY = new WrapFactory() {
        {
            setJavaPrimitiveWrap(false);
        }
    };

    private ModJSContextFactory() {
    }

    public static boolean isVisible(String className) {
        if (className.startsWith("jdk.proxy") || className.startsWith("$Proxy")) {
            // Rhino adapts script functions to Java interfaces (Consumer, Command, ...) with dynamic proxies.
            return true;
        }
        if (DENIED_CLASSES.contains(className)) {
            return false;
        }
        for (String prefix : DENIED_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected Context makeContext() {
        Context cx = super.makeContext();
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.setOptimizationLevel(-1);
        cx.setWrapFactory(WRAP_FACTORY);
        cx.setClassShutter(new ClassShutter() {
            @Override
            public boolean visibleToScripts(String fullClassName) {
                return isVisible(fullClassName);
            }
        });
        return cx;
    }
}
