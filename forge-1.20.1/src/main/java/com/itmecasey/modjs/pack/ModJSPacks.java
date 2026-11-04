package com.itmecasey.modjs.pack;

import com.itmecasey.modjs.data.DataBuilder;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;

import java.util.function.Consumer;

/**
 * Registers the generated packs. Each time the game scans for packs (world load, {@code /reload}, F3+T) the scripts
 * of that side are re-run and the pack contents rebuilt before the pack is handed over.
 */
public final class ModJSPacks {
    public static final String SERVER_PACK_ID = "modjs_data";
    public static final String CLIENT_PACK_ID = "modjs_assets";

    private ModJSPacks() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        if (type == PackType.SERVER_DATA) {
            event.addRepositorySource(consumer -> {
                DataBuilder.buildServerData(GeneratedData.SERVER);
                consumer.accept(create(type, GeneratedData.SERVER, SERVER_PACK_ID, "ModJS data"));
            });
        } else if (type == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(consumer -> {
                DataBuilder.buildClientAssets(GeneratedData.CLIENT);
                consumer.accept(create(type, GeneratedData.CLIENT, CLIENT_PACK_ID, "ModJS assets"));
            });
        }
    }

    private static Pack create(PackType type, GeneratedData data, String id, String title) {
        return Pack.readMetaAndCreate(id, net.minecraft.network.chat.Component.literal(title), true,
            packId -> new GeneratedPack(type, data, id, title), type, Pack.Position.TOP, PackSource.BUILT_IN);
    }
}
