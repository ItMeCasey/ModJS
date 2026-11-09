package com.itmecasey.modjs.client;

import com.itmecasey.modjs.script.names.NameMap;
import com.itmecasey.modjs.event.EventGroups;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client behavior scripts can change: which toasts show, the tutorial, and the experimental-settings warning. It reaches the
 * game by reflection and class names, so one copy serves every Minecraft version (a class that is missing or renamed only
 * logs a warning). Only call it on the physical client.
 */
public final class ClientTweaks {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final Set<String> HIDDEN = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile boolean skipExperimental;

    private ClientTweaks() {
    }

    // ---- settings ----

    /** Hides toasts of these kinds ({@code system}, {@code tutorial}, {@code recipe}, {@code advancement}, a system toast id, or {@code all}). */
    public static void hideToasts(String... kinds) {
        for (String kind : kinds) {
            HIDDEN.add(kind.toLowerCase(Locale.ROOT));
        }
    }

    public static void clearHiddenToasts() {
        HIDDEN.clear();
    }

    public static void setSkipExperimentalWarning(boolean skip) {
        skipExperimental = skip;
    }

    public static void reset() {
        HIDDEN.clear();
        skipExperimental = false;
    }

    // ---- reflection helpers ----

    private static void warnOnce(String key, String message, Throwable cause) {
        if (WARNED.add(key)) {
            LOGGER.warn("ModJS client tweak '{}' is not available on this Minecraft version: {}", message, cause == null ? "" : cause.toString());
        }
    }

    private static Object minecraft() throws ReflectiveOperationException {
        return NameMap.method(Class.forName("net.minecraft.client.Minecraft"), "getInstance").invoke(null);
    }

    private static Field fieldOfType(Class<?> owner, String... simpleTypeNames) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                for (String name : simpleTypeNames) {
                    if (field.getType().getSimpleName().equals(name)) {
                        field.setAccessible(true);
                        return field;
                    }
                }
            }
        }
        return null;
    }

    private static Field fieldNamed(Class<?> owner, String name) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            Field field = NameMap.declaredField(c, name);
            if (field != null) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private static Method methodNamed(Class<?> owner, String name, int parameters) {
        for (Class<?> c = owner; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (NameMap.sameMethodName(c, method.getName(), name) && method.getParameterCount() == parameters) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Object literal(String text) throws ReflectiveOperationException {
        return NameMap.method(Class.forName("net.minecraft.network.chat.Component"), "literal", String.class).invoke(null, text);
    }

    private static String text(Object component) {
        try {
            return component == null ? null : (String) NameMap.method(component.getClass(), "getString").invoke(component);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static void onMainThread(Runnable task) {
        try {
            Object mc = minecraft();
            NameMap.method(mc.getClass(), "execute", Runnable.class).invoke(mc, task);
        } catch (ReflectiveOperationException e) {
            task.run();
        }
    }

    // ---- toasts ----

    /** Replaces the game's toast component with ours, which asks {@link #acceptToast} before showing anything. */
    public static void installToastComponent(Object component) {
        try {
            Object mc = minecraft();
            Field field = fieldOfType(mc.getClass(), "ToastComponent", "ToastManager");
            if (field == null) {
                warnOnce("toast-field", "toasts", null);
                return;
            }
            field.set(mc, component);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("toast-install", "toasts", e);
        }
    }

    private static Object toastComponent() throws ReflectiveOperationException {
        Object mc = minecraft();
        Field field = fieldOfType(mc.getClass(), "ToastComponent", "ToastManager");
        return field == null ? null : field.get(mc);
    }

    private static String kindOf(Object toast) {
        String name = toast.getClass().getSimpleName();
        if (name.endsWith("Toast")) {
            name = name.substring(0, name.length() - "Toast".length());
        }
        return name.toLowerCase(Locale.ROOT);
    }

    /** The name of a system toast's id constant, or an empty string. */
    private static String systemId(Object toast) {
        try {
            Field idField = fieldOfType(toast.getClass(), "SystemToastId");
            if (idField == null) {
                return "";
            }
            Object id = idField.get(toast);
            for (Field constant : idField.getType().getDeclaredFields()) {
                if (Modifier.isStatic(constant.getModifiers()) && constant.getType() == idField.getType()) {
                    constant.setAccessible(true);
                    if (constant.get(null) == id) {
                        return constant.getName().toLowerCase(Locale.ROOT);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("toast-id", "toast ids", e);
        }
        return "";
    }

    private static String linesToText(Object lines) {
        if (!(lines instanceof List<?> list)) {
            return null;
        }
        try {
            Class<?> sinkClass = Class.forName("net.minecraft.util.FormattedCharSink");
            StringBuilder out = new StringBuilder();
            for (Object line : list) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                Object sink = Proxy.newProxyInstance(sinkClass.getClassLoader(), new Class<?>[] {sinkClass}, (proxy, method, args) -> {
                    if (method.getName().equals("accept") && args != null && args.length == 3) {
                        out.appendCodePoint((Integer) args[2]);
                        return true;
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    return method.getName().equals("toString") ? "sink" : null;
                });
                NameMap.method(line.getClass(), "accept", sinkClass).invoke(line, sink);
            }
            return out.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Called by the game's toast component for every toast. Runs {@code ClientEvents.toast}, applies edits, and says whether the
     * toast should show.
     */
    public static boolean acceptToast(Object toast) {
        try {
            String type = kindOf(toast);
            String id = type.equals("system") ? systemId(toast) : "";
            if (HIDDEN.contains("all") || HIDDEN.contains(type) || (!id.isEmpty() && HIDDEN.contains(id))) {
                return false;
            }
            if (!EventGroups.CLIENT.hasHandlers("toast")) {
                return true;
            }
            Field titleField = type.equals("system") ? fieldNamed(toast.getClass(), "title") : null;
            Field linesField = type.equals("system") ? fieldNamed(toast.getClass(), "messageLines") : null;
            Object titleComponent = titleField == null ? null : titleField.get(toast);
            ToastEventJS event = new ToastEventJS(type, id, text(titleComponent), linesField == null ? null : linesToText(linesField.get(toast)));
            EventGroups.CLIENT.post("toast", event);
            if (event.isCancelled()) {
                return false;
            }
            if (event.newMessage() != null) {
                Method reset = methodNamed(toast.getClass(), "reset", 2);
                if (reset != null) {
                    Object newTitle = event.newTitle() != null ? literal(event.newTitle()) : titleComponent;
                    reset.invoke(toast, newTitle, literal(event.newMessage()));
                }
            } else if (event.newTitle() != null && titleField != null) {
                titleField.set(toast, literal(event.newTitle()));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("toast-event", "toast events", e);
        }
        return true;
    }

    /** Shows a system toast with this text. */
    public static void showToast(String title, String message) {
        onMainThread(() -> {
            try {
                Object component = toastComponent();
                Class<?> system = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast");
                Class<?> idClass = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId");
                Object id = idClass.getField("PERIODIC_NOTIFICATION").get(null);
                Method add = methodNamed(system, "addOrUpdate", 4);
                add.invoke(null, component, id, literal(title), message == null ? null : literal(message));
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("toast-show", "showing toasts", e);
            }
        });
    }

    /** Removes every toast that is on screen or waiting. */
    public static void clearToasts() {
        onMainThread(() -> {
            try {
                Object component = toastComponent();
                if (component != null) {
                    NameMap.method(component.getClass(), "clear").invoke(component);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                warnOnce("toast-clear", "clearing toasts", e);
            }
        });
    }

    // ---- tutorial ----

    /** Turns the in-game tutorial hints off for good (the same as the game's own setting) and hides their toasts. */
    public static void disableTutorial() {
        hideToasts("tutorial");
        try {
            Object mc = minecraft();
            Class<?> steps = Class.forName("net.minecraft.client.tutorial.TutorialSteps");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object none = Enum.valueOf((Class<Enum>) steps, "NONE");
            Object options = fieldNamed(mc.getClass(), "options").get(mc);
            Field step = fieldNamed(options.getClass(), "tutorialStep");
            if (step != null && step.getType() == steps) {
                step.set(options, none);
                Method save = methodNamed(options.getClass(), "save", 0);
                if (save != null) {
                    save.invoke(options);
                }
            }
            Object tutorial = NameMap.method(mc.getClass(), "getTutorial").invoke(mc);
            if (tutorial != null) {
                methodNamed(tutorial.getClass(), "setStep", 1).invoke(tutorial, none);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("tutorial", "disabling the tutorial", e);
        }
    }

    // ---- experimental warning ----

    /**
     * If scripts asked to skip the experimental-settings warning and this screen is one, answers it with "proceed" and returns
     * true, so the caller can cancel opening the screen.
     */
    public static boolean autoConfirmExperimental(Object screen) {
        if (!skipExperimental || screen == null) {
            return false;
        }
        try {
            String name = screen.getClass().getSimpleName();
            boolean warning = name.equals("ConfirmExperimentalFeaturesScreen");
            if (!warning && name.equals("ConfirmScreen")) {
                Object title = NameMap.method(screen.getClass(), "getTitle").invoke(screen);
                Object contents = NameMap.method(title.getClass(), "getContents").invoke(title);
                if (contents.getClass().getSimpleName().equals("TranslatableContents")) {
                    String key = (String) NameMap.method(contents.getClass(), "getKey").invoke(contents);
                    warning = key.equals("selectWorld.warning.experimental.title");
                }
            }
            if (!warning) {
                return false;
            }
            Field callback = fieldOfType(screen.getClass(), "BooleanConsumer");
            if (callback == null) {
                warnOnce("experimental-field", "skipping the experimental warning", null);
                return false;
            }
            Object consumer = callback.get(screen);
            Method accept = NameMap.method(callback.getType(), "accept", boolean.class);
            onMainThread(() -> {
                try {
                    accept.invoke(consumer, true);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    warnOnce("experimental-run", "skipping the experimental warning", e);
                }
            });
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warnOnce("experimental", "skipping the experimental warning", e);
            return false;
        }
    }
}
