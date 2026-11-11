package com.itmecasey.modjs.block;

import com.itmecasey.modjs.registry.BlockBuilderJS;
import com.itmecasey.modjs.registry.RegistryManager;
import com.itmecasey.modjs.script.ScriptBlockCalls;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.Function;

/** Runs the callbacks scripts gave a block ({@code onTick}, {@code onButton}); a callback that fails is reported once, then skipped until reload. */
public final class ScriptBlockLogic {
    private ScriptBlockLogic() {
    }

    /** The builder of a ModJS block by path (the id without {@code modjs:}), or null. */
    public static BlockBuilderJS definition(String path) {
        for (BlockBuilderJS block : RegistryManager.blocks()) {
            if (block.id().equals(path)) {
                return block;
            }
        }
        return null;
    }

    /** Server tick of one block entity: runs {@code onTick} when the interval has come. */
    public static void tick(BlockBuilderJS def, ScriptBlockEntityJS be, long gameTime) {
        Function fn = def.tickFunction();
        if (fn == null || gameTime % def.tickInterval() != 0) {
            return;
        }
        call("block." + def.id() + ".onTick", fn, be);
    }

    /** A screen button was pressed: runs {@code onButton(id, be, player)}. */
    public static void button(BlockBuilderJS def, String buttonId, ScriptBlockEntityJS be, Object player) {
        Function fn = def.buttonFunction();
        if (fn != null) {
            call("block." + def.id() + ".onButton", fn, buttonId, be, player);
        }
    }

    public static void forgetFailures() {
        ScriptBlockCalls.forgetFailures();
    }

    private static void call(String where, Function fn, Object... args) {
        ScriptBlockCalls.invoke(ScriptType.STARTUP, where, fn, args);
    }
}
