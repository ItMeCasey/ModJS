package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventGroups;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only platform registration and click delivery for script key bindings. */
@EventBusSubscriber(modid = "modjs", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ScriptedKeys {
    private static final Map<String, KeyMapping> KEYS = new LinkedHashMap<>();
    private ScriptedKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        var scripts = com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.CLIENT);
        if (scripts.scope() == null) scripts.reload();
        KeyBindingEventJS.rebuild();
        for (KeyBindingEventJS.Spec spec : KeyBindingEventJS.current()) {
            KeyMapping mapping = new KeyMapping(spec.translationKey(), KeyConflictContext.IN_GAME, KeyModifier.NONE,
                InputConstants.Type.KEYSYM, spec.defaultKey(), "key.categories.misc");
            KEYS.put(spec.id(), mapping);
            event.register(mapping);
        }
    }

    /** Useful to client scripts that need to inspect the user's current binding. */
    public static KeyMapping get(String id) { return KEYS.get(id); }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        KEYS.forEach((id, mapping) -> {
            while (mapping.consumeClick()) EventGroups.CLIENT.post("keyBindingPressed", id, new KeyBindingEventJS.Pressed(id));
        });
    }
}
