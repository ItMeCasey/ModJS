package com.itmecasey.modjs.block;

/**
 * What a script gets as {@code be} in {@code onTick}, {@code onButton} and the overlay templates: a scripted block entity seen
 * through item ids and counts, so scripts never touch Minecraft classes. Each Minecraft build implements it on its block entity.
 */
public interface ScriptBlockEntityJS {
    int getX();

    int getY();

    int getZ();

    /** The block's id, such as {@code modjs:smelter}. */
    String getBlockId();

    /** Number of slots (0 for a block with {@code entity()} only). */
    int getSize();

    /** Item id in a slot ({@code minecraft:air} when empty). */
    String getItemId(int slot);

    int getCount(int slot);

    /** Puts {@code count} of an item in a slot, replacing what was there. */
    void setItem(int slot, String itemId, int count);

    void clearSlot(int slot);

    /** Removes up to {@code amount} items from a slot and returns how many were removed. */
    int shrink(int slot, int amount);

    /** Puts items into the first slots that take them and returns how many did not fit. */
    int insert(String itemId, int count);

    /** A saved, synced value: number, string or boolean; null when unset. */
    Object getData(String key);

    /** Sets (or with null removes) a saved, synced value. Syncs to clients on the next tick. */
    void setData(String key, Object value);

    /** Reads a value declared in {@code inventory({ values: [...] })}. */
    int getValue(String name);

    void setValue(String name, int value);

    /** Marks the block entity as changed (it will be saved). */
    void markChanged();

    /** Sends the current data to clients right now. */
    void sync();

    boolean isClientSide();
}
