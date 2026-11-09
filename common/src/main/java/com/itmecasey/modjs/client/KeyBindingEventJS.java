package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registers script keys in the game's Controls screen, under Miscellaneous. */
public final class KeyBindingEventJS extends EventJS {
    public record Spec(String id, int defaultKey, String name) {
        /** Stable across display-name edits, so saved user bindings survive a rename. */
        public String translationKey() { return "key.modjs." + id.replace(':', '/'); }
    }
    private static volatile List<Spec> current = List.of();

    public static void rebuild() {
        KeyBindingEventJS event = new KeyBindingEventJS();
        com.itmecasey.modjs.event.EventGroups.CLIENT.post("keyBindings", event);
        current = event.isCancelled() ? List.of() : event.definitions();
    }

    public static List<Spec> current() { return current; }
    public static void addNames(com.google.gson.JsonObject language) {
        current.forEach(spec -> language.addProperty(spec.translationKey(), spec.name()));
    }
    private final Map<String, Spec> definitions = new LinkedHashMap<>();

    public void register(String id, int defaultKey) { register(id, defaultKey, id); }

    /** Uses GLFW key codes; -1 means unbound. The name is displayed in Controls. */
    public void register(String id, int defaultKey, String name) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Key binding needs a namespaced id");
        if (defaultKey < -1 || defaultKey > 348)
            throw new IllegalArgumentException("Invalid GLFW key code: " + defaultKey);
        if (definitions.containsKey(id)) throw new IllegalArgumentException("Duplicate key binding: " + id);
        definitions.put(id, new Spec(id, defaultKey, name == null || name.isBlank() ? id : name));
    }

    public List<Spec> definitions() { return List.copyOf(definitions.values()); }

    /** A registered binding was pressed; its id stays the same after rebinding. */
    public static final class Pressed extends EventJS {
        private final String id;
        public Pressed(String id) { this.id = id; }
        public String getId() { return id; }
    }
}
