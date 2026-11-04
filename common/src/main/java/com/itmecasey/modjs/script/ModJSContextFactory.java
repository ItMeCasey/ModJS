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

    private static final java.util.List<java.util.function.UnaryOperator<Object>> WRAP_ADAPTERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final WrapFactory WRAP_FACTORY = new WrapFactory() {
        {
            setJavaPrimitiveWrap(false);
        }

        @Override
        public Object wrap(Context cx, org.mozilla.javascript.Scriptable scope, Object obj, Class<?> staticType) {
            Object adapted = obj;
            if (adapted != null) {
                for (java.util.function.UnaryOperator<Object> adapter : WRAP_ADAPTERS) {
                    Object replacement = adapter.apply(adapted);
                    if (replacement != null) {
                        adapted = replacement;
                    }
                }
            }
            return super.wrap(cx, scope, adapted, adapted == obj ? staticType : null);
        }
    };

    private ModJSContextFactory() {
    }

    /**
     * Lets a platform swap Java objects for script-friendly stand-ins before scripts see them. The adapter returns null
     * to leave an object alone. Used where the engine's own classes cannot be inspected safely.
     */
    public static void addWrapAdapter(java.util.function.UnaryOperator<Object> adapter) {
        WRAP_ADAPTERS.add(adapter);
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
