package com.itmecasey.modjs.unify;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Swaps non-canonical items in a player's inventory for the canonical item. */
public final class InventoryUnifier {
    /** Server ticks between sweeps (every two seconds). */
    public static final int INTERVAL = 40;

    private InventoryUnifier() {
    }

    /** Returns how many stacks were replaced. */
    public static int swap(Player player) {
        Unifier unifier = Unifier.current();
        if (!unifier.active() || !UnifyConfig.swapInventories()) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int swapped = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            String canonical = unifier.canonicalFor(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            if (canonical == null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(canonical);
            Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null) {
                inventory.setItem(slot, new ItemStack(item, stack.getCount()));
                swapped++;
            }
        }
        return swapped;
    }
}
