package com.itmecasey.modjs.loot;

import com.google.gson.JsonObject;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.item.ItemRef;
import com.itmecasey.modjs.pack.GeneratedData;
import org.mozilla.javascript.Context;

import java.util.function.Consumer;

/**
 * The event passed to {@code ServerEvents.blockLootTables}, {@code entityLootTables}, {@code chestLootTables} and
 * {@code genericLootTables}. Tables replace the table with the same id, vanilla ones included.
 */
public final class LootEventJS extends EventJS {
    private final String folder;
    private final String tableType;
    private final GeneratedData data;

    public LootEventJS(String folder, String tableType, GeneratedData data) {
        this.folder = folder;
        this.tableType = tableType;
        this.data = data;
    }

    private void write(String id, LootTableJS table) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        data.putJson(namespace, "loot_table/" + (folder.isEmpty() ? "" : folder + "/") + path, table.toJson());
    }

    /** Builds a table for {@code id} (a block, entity type or chest id depending on the event). */
    public void add(String id, Consumer<LootTableJS> configure) {
        LootTableJS table = new LootTableJS(tableType);
        configure.accept(table);
        write(id, table);
    }

    /** Block, entity and chest events share this name; it adds a table dropping a single item. */
    public void addSimple(String id, Object item) {
        ItemRef ref = ItemRef.parse(item);
        add(id, table -> table.addPool(pool -> {
            pool.rolls(1);
            pool.addItem(ref.id(), ref.count() > 1 ? (Object) ref.count() : null);
            if (tableType.equals("minecraft:block")) {
                pool.survivesExplosion();
            }
        }));
    }

    /** Block drops: {@code event.addSimpleBlock('minecraft:dirt', '2x minecraft:diamond')}. */
    public void addSimpleBlock(String block, Object drop) {
        addSimple(block, drop);
    }

    /** Makes a block drop nothing. */
    public void clear(String id) {
        add(id, table -> { });
    }

    public void addJson(String id, Object json) {
        if (!(com.itmecasey.modjs.json.JsonUtil.toJson(json) instanceof JsonObject object)) {
            throw Context.reportRuntimeError("Loot table JSON must be an object");
        }
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        data.putJson(namespace, "loot_table/" + (folder.isEmpty() ? "" : folder + "/") + path, object);
    }
}
