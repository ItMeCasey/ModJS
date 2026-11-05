package com.itmecasey.modjs.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Script-friendly view of a block entity (chest, furnace, barrel, sign...). Exposes ids and counts instead of Minecraft
 * objects, so scripts never touch the engine classes directly.
 */
public final class BlockEntityJS {
    private final Level level;
    private final BlockPos pos;
    private final BlockEntity entity;

    private BlockEntityJS(Level level, BlockPos pos, BlockEntity entity) {
        this.level = level;
        this.pos = pos;
        this.entity = entity;
    }

    /** The block entity at a position, or null if the block has none. */
    public static BlockEntityJS at(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity == null ? null : new BlockEntityJS(level, pos, entity);
    }

    /** Type id such as {@code minecraft:chest}. */
    public String getId() {
        ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType());
        return key == null ? "unknown" : key.toString();
    }

    public String getBlockId() {
        return BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString();
    }

    public int getX() {
        return pos.getX();
    }

    public int getY() {
        return pos.getY();
    }

    public int getZ() {
        return pos.getZ();
    }

    /** True for chests, barrels, furnaces, hoppers and other block entities with item slots. */
    public boolean hasInventory() {
        return entity instanceof Container;
    }

    /** Number of item slots, or 0 without an inventory. */
    public int getSize() {
        return entity instanceof Container container ? container.getContainerSize() : 0;
    }

    /** Item id in a slot ({@code minecraft:air} when empty). */
    public String getItemId(int slot) {
        return BuiltInRegistries.ITEM.getKey(stack(slot).getItem()).toString();
    }

    public int getCount(int slot) {
        return stack(slot).getCount();
    }

    /** Puts {@code count} of an item in a slot, replacing what was there. */
    public void setItem(int slot, String itemId, int count) {
        Container container = container();
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item '" + itemId + "'");
        }
        container.setItem(checkSlot(container, slot), new ItemStack(item, count));
        entity.setChanged();
    }

    public void clearSlot(int slot) {
        Container container = container();
        container.setItem(checkSlot(container, slot), ItemStack.EMPTY);
        entity.setChanged();
    }

    /** Every non-empty slot as {@code {slot, id, count}}. */
    public List<java.util.Map<String, Object>> getItems() {
        List<java.util.Map<String, Object>> out = new ArrayList<>();
        for (int slot = 0; slot < getSize(); slot++) {
            ItemStack stack = stack(slot);
            if (!stack.isEmpty()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("slot", slot);
                row.put("id", getItemId(slot));
                row.put("count", stack.getCount());
                out.add(row);
            }
        }
        return out;
    }

    /** The full saved data as SNBT text, for reading custom names, lock codes, sign text and similar. */
    public String getNbt() {
        return entity.saveWithFullMetadata(level.registryAccess()).toString();
    }

    private Container container() {
        if (!(entity instanceof Container container)) {
            throw new IllegalStateException(getId() + " has no inventory");
        }
        return container;
    }

    private static int checkSlot(Container container, int slot) {
        if (slot < 0 || slot >= container.getContainerSize()) {
            throw new IllegalArgumentException("Slot " + slot + " is out of range 0-" + (container.getContainerSize() - 1));
        }
        return slot;
    }

    private ItemStack stack(int slot) {
        Container container = container();
        return container.getItem(checkSlot(container, slot));
    }
}
