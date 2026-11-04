package com.itmecasey.modjs.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** {@code /modjs hand}, {@code /modjs toolbar} and {@code /modjs inventory}: tell the player which item ids they hold. */
final class ItemListCommands {
    private static final int HOTBAR_SLOTS = 9;
    private static final int MAIN_END = 36;

    private ItemListCommands() {
    }

    static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("hand").executes(context -> hand(context.getSource())));
        root.then(Commands.literal("toolbar").executes(context -> list(context.getSource(), "Toolbar", 0, HOTBAR_SLOTS)));
        root.then(Commands.literal("inventory").executes(context -> {
            Player player = context.getSource().getPlayerOrException();
            return list(context.getSource(), "Inventory", 0, player.getInventory().getContainerSize());
        }));
    }

    private static String describe(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return stack.getCount() > 1 ? id + " x" + stack.getCount() : id;
    }

    private static int hand(CommandSourceStack source) throws CommandSyntaxException {
        Player player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        String line = stack.isEmpty() ? "Main hand is empty." : "Main hand: " + describe(stack);
        source.sendSuccess(() -> Component.literal(line), false);
        return stack.isEmpty() ? 0 : 1;
    }

    private static String slotLabel(int slot) {
        if (slot < HOTBAR_SLOTS) {
            return "Toolbar " + (slot + 1);
        }
        if (slot < MAIN_END) {
            return "Inventory " + (slot - HOTBAR_SLOTS + 1);
        }
        return switch (slot - MAIN_END) {
            case 0 -> "Feet";
            case 1 -> "Legs";
            case 2 -> "Chest";
            case 3 -> "Head";
            case 4 -> "Off hand";
            default -> "Slot " + slot;
        };
    }

    private static int list(CommandSourceStack source, String title, int from, int to) throws CommandSyntaxException {
        Player player = source.getPlayerOrException();
        Inventory inventory = player.getInventory();
        int found = 0;
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            found++;
            String line = slotLabel(slot) + ": " + describe(stack);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        int count = found;
        source.sendSuccess(() -> Component.literal(title + ": " + count + (count == 1 ? " item" : " items") + "."), false);
        return found;
    }
}
