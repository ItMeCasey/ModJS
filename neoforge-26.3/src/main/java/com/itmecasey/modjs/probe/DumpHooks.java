package com.itmecasey.modjs.probe;

import net.neoforged.fml.ModList;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The loader-specific part of a dump: the list of loaded mods. */
final class DumpHooks {
    private DumpHooks() {
    }

    static List<DumpData.Mod> mods() {
        List<DumpData.Mod> mods = new ArrayList<>();
        for (var info : ModList.get().getMods()) {
            mods.add(new DumpData.Mod(info.getModId(), info.getDisplayName(), String.valueOf(info.getVersion())));
        }
        return mods;
    }

    /** Every holder of a registry; the method that lists them was renamed after 1.20.1. */
    static Stream<? extends Holder.Reference<?>> holders(Registry<?> registry) {
        return registry.listElements();
    }
}
