package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.event.EventJS;
import org.mozilla.javascript.Context;

import java.util.Map;
import java.util.function.Function;

/**
 * The event passed to {@code StartupEvents.registry('item' | 'block', event => event.create('id'))}. An id without a namespace is
 * registered as {@code modjs:id}; {@code event.create('mypack:ruby')} registers {@code mypack:ruby}, and {@code event.namespace('mypack')}
 * makes plain ids in this handler go to {@code mypack}.
 */
public final class RegistryEventJS<B> extends EventJS {
    private final String kind;
    private final Map<String, B> target;
    private final Function<String, B> factory;
    private String defaultNamespace = RegistryNames.DEFAULT_NAMESPACE;

    RegistryEventJS(String kind, Map<String, B> target, Function<String, B> factory) {
        this.kind = kind;
        this.target = target;
        this.factory = factory;
    }

    /** The namespace plain ids get in this event from now on (default {@code modjs}). */
    public RegistryEventJS<B> namespace(String namespace) {
        try {
            this.defaultNamespace = RegistryNames.checkNamespace(namespace);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(e.getMessage());
        }
        return this;
    }

    public String getNamespace() {
        return defaultNamespace;
    }

    /** Creates an entry: {@code "ruby"} is registered as {@code modjs:ruby}, {@code "mypack:ruby"} as {@code mypack:ruby}. */
    public B create(String id) {
        RegistryNames.Id parsed;
        try {
            parsed = RegistryNames.parse(id, defaultNamespace);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError("Invalid " + kind + " id: " + e.getMessage());
        }
        if (target.containsKey(parsed.full())) {
            throw Context.reportRuntimeError("Duplicate " + kind + " id '" + parsed.full() + "'");
        }
        B builder = factory.apply(parsed.full());
        target.put(parsed.full(), builder);
        return builder;
    }

    public B create(String id, String ignoredType) {
        return create(id);
    }
}
