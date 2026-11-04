package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.event.EventJS;
import org.mozilla.javascript.Context;

import java.util.Map;
import java.util.function.Function;

/** The event passed to {@code StartupEvents.registry('item' | 'block', event => event.create('id'))}. */
public final class RegistryEventJS<B> extends EventJS {
    private final String kind;
    private final Map<String, B> target;
    private final Function<String, B> factory;

    RegistryEventJS(String kind, Map<String, B> target, Function<String, B> factory) {
        this.kind = kind;
        this.target = target;
        this.factory = factory;
    }

    /** Creates an entry with a path-only id such as {@code "ruby"}; it is registered as {@code modjs:ruby}. */
    public B create(String id) {
        if (!id.matches("[a-z0-9_./-]+")) {
            throw Context.reportRuntimeError("Invalid " + kind + " id '" + id + "': use lowercase letters, digits, _ . / -");
        }
        if (target.containsKey(id)) {
            throw Context.reportRuntimeError("Duplicate " + kind + " id '" + id + "'");
        }
        B builder = factory.apply(id);
        target.put(id, builder);
        return builder;
    }

    public B create(String id, String ignoredType) {
        return create(id);
    }
}
