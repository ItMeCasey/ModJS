package com.itmecasey.modjs.script;

import com.itmecasey.modjs.item.IngredientBinding;
import com.itmecasey.modjs.item.ItemBinding;
import com.itmecasey.modjs.persistent.JsonIOBinding;
import com.itmecasey.modjs.persistent.PersistentBinding;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Helper namespaces available to every script type, plus the hook platform modules use to add their own. */
public final class ExtraBindings {
    private static final List<BindingProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private ExtraBindings() {
    }

    /** Registers a provider; platform modules call this once during mod construction. */
    public static void register(BindingProvider provider) {
        PROVIDERS.add(provider);
    }

    static void install(ScriptType type, Context cx, ScriptableObject scope) {
        ScriptableObject.putProperty(scope, "Item", Context.javaToJS(new ItemBinding(), scope));
        ScriptableObject.putProperty(scope, "Ingredient", Context.javaToJS(new IngredientBinding(), scope));
        ScriptableObject.putProperty(scope, "Platform", Context.javaToJS(new PlatformBinding(), scope));
        ScriptableObject.putProperty(scope, "SECOND", 1000);
        ScriptableObject.putProperty(scope, "MINUTE", 60_000);
        ScriptableObject.putProperty(scope, "HOUR", 3_600_000);
        ScriptableObject.putProperty(scope, "JsonIO", Context.javaToJS(new JsonIOBinding(), scope));
        ScriptableObject.putProperty(scope, "Persistent", Context.javaToJS(new PersistentBinding(), scope));
        for (BindingProvider provider : PROVIDERS) {
            provider.install(type, cx, scope);
        }
    }
}
