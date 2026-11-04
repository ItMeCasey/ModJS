package com.itmecasey.modjs.game;

import com.itmecasey.modjs.event.EventGroup;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.script.ErrorNotice;
import com.itmecasey.modjs.script.ScriptType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * More KubeJS events: first clicks, pickup checks, inventory changes, composter chances and the join-time error notice.
 * Kept apart from {@link GameEvents} so each bridge stays small.
 */
public final class ExtraGameEvents {
    /** Ticks without a click after which the next click counts as a new, first click. */
    private static final long CLICK_GAP = 6;

    private static final Map<UUID, Long> LAST_RIGHT = new HashMap<>();
    private static final Map<UUID, String[]> SNAPSHOTS = new HashMap<>();

    private ExtraGameEvents() {
    }

    /** The event passed to {@code ServerEvents.compostableRecipes(event => event.add(0.3, 'minecraft:stone'))}. */
    public static final class CompostEventJS extends EventJS {
        /** Sets the chance (0 to 1) that an item raises the composter level; use 0 to remove it. */
        public void add(double chance, String... itemIds) {
            for (String itemId : itemIds) {
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) {
                    throw new IllegalArgumentException("Unknown item '" + itemId + "'");
                }
                if (chance <= 0) {
                    ComposterBlock.COMPOSTABLES.removeFloat(item);
                } else {
                    ComposterBlock.COMPOSTABLES.put(item, (float) Math.min(1.0, chance));
                }
            }
        }
    }

    private static boolean wants(EventGroup group, String name) {
        return group.hasHandlers(name);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ITEM, "firstRightClicked")) {
                return;
            }
            long now = e.getLevel().getGameTime();
            Long last = LAST_RIGHT.put(e.getEntity().getUUID(), now);
            if (last == null || now - last > CLICK_GAP) {
                GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack()).hand(e.getHand().name());
                if (EventGroups.ITEM.post("firstRightClicked", itemId(e.getItemStack()), js)) {
                    e.setCanceled(true);
                }
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !wants(EventGroups.ITEM, "firstLeftClicked")) {
                return;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack()).hand(e.getHand().name());
            if (EventGroups.ITEM.post("firstLeftClicked", itemId(e.getItemStack()), js)) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((EntityItemPickupEvent e) -> {
            if (e.getEntity().level().isClientSide() || !wants(EventGroups.ITEM, "canPickUp")) {
                return;
            }
            ItemStack stack = e.getItem().getItem();
            GameEventJS js = new GameEventJS().player(e.getEntity()).item(stack).entity(e.getItem());
            if (EventGroups.ITEM.post("canPickUp", itemId(stack), js)) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END || !e.side.isServer() || !wants(EventGroups.PLAYER, "inventoryChanged")) {
                return;
            }
            if (e.player.tickCount % 2 != 0) {
                return;
            }
            Player player = e.player;
            int size = player.getInventory().getContainerSize();
            String[] previous = SNAPSHOTS.get(player.getUUID());
            String[] now = new String[size];
            for (int slot = 0; slot < size; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                now[slot] = stack.isEmpty() ? "" : itemId(stack) + "x" + stack.getCount();
            }
            SNAPSHOTS.put(player.getUUID(), now);
            if (previous == null || previous.length != size) {
                return;
            }
            for (int slot = 0; slot < size; slot++) {
                if (!now[slot].equals(previous[slot])) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    EventGroups.PLAYER.post("inventoryChanged", stack.isEmpty() ? null : itemId(stack),
                        new GameEventJS().player(player).item(stack).slot(slot));
                }
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            LAST_RIGHT.remove(e.getEntity().getUUID());
            SNAPSHOTS.remove(e.getEntity().getUUID());
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && player.createCommandSourceStack().hasPermission(2)) {
                for (String line : ErrorNotice.lines(ScriptType.STARTUP, ScriptType.SERVER)) {
                    player.sendSystemMessage(Component.literal(line).withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            if (wants(EventGroups.SERVER, "compostableRecipes")) {
                EventGroups.SERVER.post("compostableRecipes", new CompostEventJS());
            }
        });
    }
}
