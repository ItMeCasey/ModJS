package com.itmecasey.modjs;

import com.itmecasey.modjs.client.KeyBindingEventJS;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyBindingTest {
    @Test
    void displayNameChangesPreserveTheSavedBindingKey() {
        var before = new KeyBindingEventJS.Spec("pack:open_menu", 71, "Open menu");
        var renamed = new KeyBindingEventJS.Spec("pack:open_menu", 71, "Open pack menu");
        assertEquals(before.translationKey(), renamed.translationKey());
        assertFalse(before.translationKey().contains(":"), "Options uses colon as its key/value separator");
        assertNotEquals(new KeyBindingEventJS.Spec("a.b:c", 71, "A").translationKey(),
            new KeyBindingEventJS.Spec("a:b.c", 71, "B").translationKey());
    }

    @Test
    void invalidAndDuplicateKeysCannotReplaceAValidDefinition() {
        KeyBindingEventJS event = new KeyBindingEventJS();
        event.register("pack:menu", 71, "Menu");
        assertThrows(IllegalArgumentException.class, () -> event.register("pack:menu", 72, "Replacement"));
        assertThrows(IllegalArgumentException.class, () -> event.register("not namespaced", 71));
        assertThrows(IllegalArgumentException.class, () -> event.register("pack:bad", 349));
        assertEquals(1, event.definitions().size());
        assertEquals(71, event.definitions().get(0).defaultKey());
    }
}
