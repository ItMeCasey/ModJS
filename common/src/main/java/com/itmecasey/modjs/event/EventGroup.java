package com.itmecasey.modjs.event;

import com.itmecasey.modjs.script.ModJSContextFactory;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A script-visible namespace of events, such as {@code ServerEvents}. Reading any property yields a function that
 * registers a handler: {@code ServerEvents.recipes(event => ...)} or, with an id filter,
 * {@code ServerEvents.tags('item', event => ...)}.
 */
public final class EventGroup extends ScriptableObject {
    private record Handler(ScriptType owner, Function function, Scriptable scope, int priority, boolean once) {
    }

    private final String name;
    private final ScriptType allowedType;
    private final Map<String, List<Handler>> handlers = new HashMap<>();
    private final Map<String, Registrar> registrars = new HashMap<>();

    public EventGroup(String name, ScriptType allowedType) {
        this.name = name;
        this.allowedType = allowedType;
    }

    @Override
    public String getClassName() {
        return "EventGroup";
    }

    public String groupName() {
        return name;
    }

    @Override
    public boolean has(String property, Scriptable start) {
        return true;
    }

    @Override
    public Object get(String property, Scriptable start) {
        return registrars.computeIfAbsent(property, Registrar::new);
    }

    private static String key(String event, String extraId) {
        return extraId == null ? event : event + ":" + extraId;
    }

    /** Removes every handler a given script type registered, called before that type's scripts are re-run. */
    public synchronized void clear(ScriptType owner) {
        for (List<Handler> list : handlers.values()) {
            list.removeIf(handler -> handler.owner == owner);
        }
    }

    private synchronized void register(ScriptType owner, String event, String extraId, Function function, Scriptable scope,
                                       int priority, boolean once) {
        if (allowedType != null && allowedType != owner) {
            ScriptManager.of(owner).addError(name + "." + event + " can only be used in " + allowedType.directory);
            return;
        }
        handlers.computeIfAbsent(key(event, extraId), k -> new CopyOnWriteArrayList<>()).add(new Handler(owner, function, scope, priority, once));
    }

    public synchronized boolean hasHandlers(String event) {
        if (EventTrace.enabled()) {
            return true;
        }
        for (Map.Entry<String, List<Handler>> entry : handlers.entrySet()) {
            String key = entry.getKey();
            if ((key.equals(event) || key.startsWith(event + ":")) && !entry.getValue().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Ids that handlers registered for {@code event} with an id filter, such as the registries of {@code tags}. */
    public synchronized java.util.Set<String> extraIds(String event) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, List<Handler>> entry : handlers.entrySet()) {
            if (entry.getKey().startsWith(event + ":") && !entry.getValue().isEmpty()) {
                ids.add(entry.getKey().substring(event.length() + 1));
            }
        }
        return ids;
    }

    /** Runs only the handlers registered for exactly {@code event:extraId}. Returns true if cancelled. */
    public boolean postExact(String event, String extraId, EventJS eventObject) {
        List<Handler> toRun;
        synchronized (this) {
            List<Handler> list = handlers.get(key(event, extraId));
            toRun = list == null ? List.of() : new ArrayList<>(list);
        }
        run(event, toRun, eventObject);
        return eventObject.isCancelled();
    }

    /** Removes every registration of {@code function} for {@code event}, with or without an id filter. */
    private synchronized void unregister(String event, Function function) {
        for (Map.Entry<String, List<Handler>> entry : handlers.entrySet()) {
            String key = entry.getKey();
            if (key.equals(event) || key.startsWith(event + ":")) {
                entry.getValue().removeIf(handler -> handler.function == function);
            }
        }
    }

    private synchronized void unregisterHandler(Handler handler) {
        for (List<Handler> list : handlers.values()) {
            list.remove(handler);
        }
    }

    /** Posts an event with no id filter. Returns true if a handler cancelled it. */
    public boolean post(String event, EventJS eventObject) {
        return post(event, null, eventObject);
    }

    /**
     * Runs every handler registered for {@code event}, then those registered for {@code event:extraId}.
     * Returns true if the event was cancelled.
     */
    public boolean post(String event, String extraId, EventJS eventObject) {
        EventTrace.log(name, event, extraId, eventObject);
        List<Handler> toRun = new ArrayList<>();
        synchronized (this) {
            List<Handler> general = handlers.get(event);
            if (general != null) {
                toRun.addAll(general);
            }
            if (extraId != null) {
                List<Handler> specific = handlers.get(key(event, extraId));
                if (specific != null) {
                    toRun.addAll(specific);
                }
            }
        }
        run(event, toRun, eventObject);
        return eventObject.isCancelled();
    }

    /**
     * Like {@link #post(String, String, EventJS)} but runs only the handlers whose script type {@code owners} accepts.
     * Info handlers use it so the server runs the ones that can see real state and the client the ones written for it.
     */
    public void postFor(String event, String extraId, EventJS eventObject, java.util.function.Predicate<ScriptType> owners) {
        List<Handler> toRun = new ArrayList<>();
        synchronized (this) {
            List<Handler> general = handlers.get(event);
            if (general != null) {
                general.stream().filter(handler -> owners.test(handler.owner)).forEach(toRun::add);
            }
            if (extraId != null) {
                List<Handler> specific = handlers.get(key(event, extraId));
                if (specific != null) {
                    specific.stream().filter(handler -> owners.test(handler.owner)).forEach(toRun::add);
                }
            }
        }
        run(event, toRun, eventObject);
    }

    private void run(String event, List<Handler> toRun, EventJS eventObject) {
        List<Handler> ordered = new ArrayList<>(toRun);
        ordered.sort((a, b) -> Integer.compare(b.priority, a.priority));
        for (Handler handler : ordered) {
            if (handler.once) {
                unregisterHandler(handler);
            }
            try {
                ModJSContextFactory.INSTANCE.call(cx -> {
                    Object wrapped = Context.javaToJS(eventObject, handler.scope);
                    return handler.function.call(cx, handler.scope, handler.scope, new Object[] {wrapped});
                });
            } catch (RhinoException e) {
                ScriptManager.of(handler.owner).reportError(name + "." + event, e);
            }
        }
    }

    /**
     * The function behind {@code ServerEvents.recipes}. Calling it registers a handler; its properties offer
     * {@code once(handler)}, {@code priority(n, handler)} (higher runs first) and {@code off(handler)}.
     */
    private final class Registrar extends BaseFunction {
        private final String event;
        private final Map<String, BaseFunction> helpers = new HashMap<>();

        Registrar(String event) {
            this.event = event;
        }

        @Override
        public boolean has(String property, Scriptable start) {
            return property.equals("once") || property.equals("priority") || property.equals("off") || super.has(property, start);
        }

        @Override
        public Object get(String property, Scriptable start) {
            return switch (property) {
                case "once", "priority", "off" -> helpers.computeIfAbsent(property, Helper::new);
                default -> super.get(property, start);
            };
        }

        private Scriptable scopeFor(ScriptType owner) {
            return ScriptManager.of(owner).scope();
        }

        private ScriptType requireOwner() {
            ScriptType owner = ScriptManager.currentType();
            if (owner == null) {
                throw Context.reportRuntimeError("Events can only be registered while scripts load");
            }
            return owner;
        }

        /** Handles (handler) and (id, handler) argument shapes starting at {@code from}. */
        private void registerArgs(Object[] args, int from, int priority, boolean once) {
            ScriptType owner = requireOwner();
            int count = args.length - from;
            if (count == 1 && args[from] instanceof Function function) {
                register(owner, event, null, function, scopeFor(owner), priority, once);
            } else if (count == 2 && args[from + 1] instanceof Function function) {
                register(owner, event, Context.toString(args[from]), function, scopeFor(owner), priority, once);
            } else {
                throw Context.reportRuntimeError(name + "." + event + " expects (handler) or (id, handler)");
            }
        }

        @Override
        public Object call(Context cx, Scriptable callScope, Scriptable thisObj, Object[] args) {
            registerArgs(args, 0, 0, false);
            return Context.getUndefinedValue();
        }

        private final class Helper extends BaseFunction {
            private final String kind;

            Helper(String kind) {
                this.kind = kind;
            }

            @Override
            public Object call(Context cx, Scriptable callScope, Scriptable thisObj, Object[] args) {
                switch (kind) {
                    case "once" -> registerArgs(args, 0, 0, true);
                    case "priority" -> {
                        if (args.length < 2 || !(args[0] instanceof Number number)) {
                            throw Context.reportRuntimeError(name + "." + event + ".priority expects (number, handler)");
                        }
                        registerArgs(args, 1, number.intValue(), false);
                    }
                    default -> {
                        if (args.length != 1 || !(args[0] instanceof Function function)) {
                            throw Context.reportRuntimeError(name + "." + event + ".off expects (handler)");
                        }
                        unregister(event, function);
                    }
                }
                return Context.getUndefinedValue();
            }
        }
    }
}
