package com.itmecasey.modjs.game;

import com.itmecasey.modjs.event.EventGroup;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.script.ErrorNotice;
import com.itmecasey.modjs.script.ScriptType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.fml.LogicalSide;
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

    /** The vanilla compost chance closest to the requested one (0.3, 0.5, 0.65, 0.85 or always one layer). */
    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> compostableKey(double chance) {
        if (chance <= 0.4) {
            return net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_LOW;
        } else if (chance <= 0.575) {
            return net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_LOW_MEDIUM;
        } else if (chance <= 0.75) {
            return net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_MEDIUM;
        } else if (chance <= 0.925) {
            return net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH;
        }
        return net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_ALWAYS_ADD_ONE;
    }

    /** The event passed to {@code ServerEvents.compostableRecipes(event => event.add(0.3, 'minecraft:stone'))}. */
    public static final class CompostEventJS extends EventJS {
        /** Sets the chance (0 to 1) that an item raises the composter level; use 0 to remove it. */
        public void add(double chance, String... itemIds) {
            for (String itemId : itemIds) {
                Identifier id = Identifier.tryParse(itemId);
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) {
                    throw new IllegalArgumentException("Unknown item '" + itemId + "'");
                }
                // Compostability is a default item component now; it is replaced on the item's registry holder.
                if (BuiltInRegistries.ITEM.wrapAsHolder(item) instanceof net.minecraft.core.Holder.Reference<Item> reference && reference.components != null) {
                    net.minecraft.core.component.DataComponentMap.Builder components = net.minecraft.core.component.DataComponentMap.builder().addAll(reference.components);
                    components.set(net.minecraft.core.component.DataComponents.COMPOSTABLE,
                        chance <= 0 ? null : new net.minecraft.world.item.component.Compostable(compostableKey(chance)));
                    reference.components = components.build();
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
        PlayerInteractEvent.RightClickItem.BUS.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ITEM, "firstRightClicked")) {
                return false;
            }
            long now = e.getLevel().getGameTime();
            Long last = LAST_RIGHT.put(e.getEntity().getUUID(), now);
            if (last == null || now - last > CLICK_GAP) {
                GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack()).hand(e.getHand().name());
                return EventGroups.ITEM.post("firstRightClicked", itemId(e.getItemStack()), js);
            }
            return false;
        });

        PlayerInteractEvent.LeftClickBlock.BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (e.getLevel().isClientSide() || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !wants(EventGroups.ITEM, "firstLeftClicked")) {
                return false;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack()).hand(e.getHand().name());
            return EventGroups.ITEM.post("firstLeftClicked", itemId(e.getItemStack()), js);
        });

        EntityItemPickupEvent.BUS.addListener((EntityItemPickupEvent e) -> {
            if (e.getEntity().level().isClientSide() || !wants(EventGroups.ITEM, "canPickUp")) {
                return false;
            }
            ItemStack stack = e.getItem().getItem();
            GameEventJS js = new GameEventJS().player(e.getEntity()).item(stack).entity(e.getItem());
            return EventGroups.ITEM.post("canPickUp", itemId(stack), js);
        });

        TickEvent.PlayerTickEvent.Post.BUS.addListener((TickEvent.PlayerTickEvent.Post e) -> {
            if (e.side() != LogicalSide.SERVER || !wants(EventGroups.PLAYER, "inventoryChanged")) {
                return;
            }
            if (e.player().tickCount % 2 != 0) {
                return;
            }
            Player player = e.player();
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

        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            LAST_RIGHT.remove(e.getEntity().getUUID());
            SNAPSHOTS.remove(e.getEntity().getUUID());
        });

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && player.createCommandSourceStack().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
                for (String line : ErrorNotice.lines(ScriptType.STARTUP, ScriptType.SERVER)) {
                    player.sendSystemMessage(Component.literal(line).withStyle(net.minecraft.ChatFormatting.RED));
                }
            }
        });

        ServerStartedEvent.BUS.addListener((ServerStartedEvent e) -> {
            if (wants(EventGroups.SERVER, "compostableRecipes")) {
                EventGroups.SERVER.post("compostableRecipes", new CompostEventJS());
            }
        });
    }
}
