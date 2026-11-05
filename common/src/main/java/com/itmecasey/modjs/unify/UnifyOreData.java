package com.itmecasey.modjs.unify;

import com.google.gson.JsonObject;
import com.itmecasey.modjs.data.DataBuilder;
import com.itmecasey.modjs.format.LoaderNames;
import com.itmecasey.modjs.pack.GeneratedData;

/**
 * Writes the biome modifier that swaps ores, only while {@code Unify.ores()} is on. It used to ship inside the mod jar, but on
 * Forge 26.x and 1.21.1 any biome modifier read from a mod makes every world "use experimental settings", which stops the world
 * from opening until the player clicks through a warning. Written into the generated data pack, it only exists for packs that
 * actually unify ores.
 */
public final class UnifyOreData {
    public static final String ID = "unify_ores";

    private UnifyOreData() {
    }

    /** Registers the contributor; call once while the mod loads. */
    public static void install() {
        DataBuilder.addServerContributor(UnifyOreData::contribute);
    }

    public static void contribute(GeneratedData data) {
        if (!UnifyConfig.oreWorldgen()) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "modjs:" + ID);
        data.putJson("modjs", LoaderNames.biomeModifierFolder() + ID, json);
    }
}
