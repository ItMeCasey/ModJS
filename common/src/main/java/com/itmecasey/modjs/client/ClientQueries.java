package com.itmecasey.modjs.client;

import com.itmecasey.modjs.script.ScriptBlockCalls;
import com.itmecasey.modjs.script.ScriptType;
import com.itmecasey.modjs.worldcraft.LevelJS;
import com.itmecasey.modjs.worldcraft.WorldHandles;
import org.mozilla.javascript.NativeObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What client scripts can ask the game about the world around the player: the block under the crosshair, any loaded block, and whether
 * a script key is held down. The platform module installs a {@link Provider}; {@code Client} (see {@link ScriptedScreens.Binding}) calls here.
 */
public final class ClientQueries {
    private static volatile Provider provider;

    private ClientQueries() {
    }

    public interface Provider {
        /** The client's world, or null outside a world. */
        WorldHandles.LevelHandle level();

        /** The block under the crosshair as {@code x, y, z, id, face, distance}, or null. */
        Map<String, Object> lookingAt();

        /** Whether the key binding registered with {@code ClientEvents.keyBindings} under this id is held down. */
        boolean keyHeld(String id);

        /** Whether a key binding was registered under this id. */
        boolean keyKnown(String id);
    }

    public static void install(Provider next) {
        provider = next;
    }

    /** The client's world for scripts: read-only, so {@code getBlock}, {@code findBlocks}, {@code connected} and {@code matches} work. */
    public static LevelJS world() {
        Provider p = provider;
        WorldHandles.LevelHandle level = p == null ? null : p.level();
        return level == null ? null : new LevelJS(level, ScriptType.CLIENT, true);
    }

    public static NativeObject lookingAt() {
        Provider p = provider;
        if (p == null || p.level() == null) {
            return null;
        }
        Map<String, Object> hit = p.lookingAt();
        if (hit == null) {
            return null;
        }
        return ScriptBlockCalls.toClientObject(new LinkedHashMap<>(hit));
    }

    public static boolean keyHeld(String id) {
        Provider p = provider;
        if (p == null) {
            return false;
        }
        if (!p.keyKnown(id)) {
            throw org.mozilla.javascript.Context.reportRuntimeError("Client.isKeyHeld: no key binding '" + id + "' (register it in ClientEvents.keyBindings first)");
        }
        return p.keyHeld(id);
    }
}
