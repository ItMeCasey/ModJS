package com.itmecasey.modjs.game;

import com.itmecasey.modjs.event.EventGroup;
import com.itmecasey.modjs.event.EventGroups;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import com.itmecasey.modjs.item.ItemModifications;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.minecraft.world.inventory.ChestMenu;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Bridges Forge gameplay events to script handlers. Every bridge checks for handlers first so unused events cost
 * almost nothing, and cancellable events are cancelled when a script cancels them.
 */
public final class GameEvents {
    private GameEvents() {
    }

    public static void register() {
        registerBlocks();
        registerItems();
        registerPlayers();
        registerEntities();
        registerLevelAndServer();
        registerExtras();
    }

    private static boolean fire(EventGroup group, String name, String id, GameEventJS event) {
        if (!group.hasHandlers(name)) {
            return false;
        }
        return group.post(name, id, event);
    }

    /** Block events reach handlers by block id and by block tag: {@code BlockEvents.broken('#minecraft:logs', ...)}. */
    private static boolean fireBlock(String name, BlockState state, GameEventJS event) {
        if (!EventGroups.BLOCK.hasHandlers(name)) {
            return false;
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.add(blockId(state));
        if (EventGroups.BLOCK.hasTagHandlers(name)) {
            state.getTags().forEach(tag -> ids.add("#" + tag.location()));
        }
        return EventGroups.BLOCK.post(name, ids, event);
    }

    private static boolean wants(EventGroup group, String name) {
        return group.hasHandlers(name);
    }

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String entityId(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    // -- blocks --------------------------------------------------------------------------------------------------

    private static void registerBlocks() {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> {
            GameEventJS js = new GameEventJS().player(e.getPlayer()).block(e.getPos(), e.getState());
            js.level((Level) e.getLevel());
            if (fireBlock("broken", e.getState(), js)) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            Entity placer = e.getEntity();
            GameEventJS js = new GameEventJS().entity(placer).block(e.getPos(), e.getPlacedBlock());
            if (placer instanceof Player player) {
                js.player(player);
            }
            if (fireBlock("placed", e.getPlacedBlock(), js)) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.BLOCK, "rightClicked")) {
                return;
            }
            BlockState state = e.getLevel().getBlockState(e.getPos());
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).block(e.getPos(), state)
                .item(e.getItemStack()).hand(e.getHand().name()).face(e.getFace() == null ? null : e.getFace().getName());
            if (fireBlock("rightClicked", state, js)) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.BLOCK, "leftClicked")) {
                return;
            }
            BlockState state = e.getLevel().getBlockState(e.getPos());
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).block(e.getPos(), state)
                .item(e.getItemStack()).hand(e.getHand().name()).face(e.getFace() == null ? null : e.getFace().getName());
            if (fireBlock("leftClicked", state, js)) {
                e.setCanceled(true);
            }
        });
    }

    // -- items ---------------------------------------------------------------------------------------------------

    private static void registerItems() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ITEM, "rightClicked")) {
                return;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack())
                .hand(e.getHand().name());
            if (fire(EventGroups.ITEM, "rightClicked", itemId(e.getItemStack()), js)) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ITEM, "entityInteracted")) {
                return;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack())
                .entity(e.getTarget()).hand(e.getHand().name());
            if (fire(EventGroups.ITEM, "entityInteracted", itemId(e.getItemStack()), js)) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e) -> {
            if (wants(EventGroups.ITEM, "crafted")) {
                fire(EventGroups.ITEM, "crafted", itemId(e.getCrafting()), new GameEventJS().player(e.getEntity()).item(e.getCrafting()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemSmeltedEvent e) -> {
            if (wants(EventGroups.ITEM, "smelted")) {
                fire(EventGroups.ITEM, "smelted", itemId(e.getSmelting()), new GameEventJS().player(e.getEntity()).item(e.getSmelting()));
            }
        });

        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish e) -> {
            if (wants(EventGroups.ITEM, "foodEaten") && e.getEntity() instanceof Player player && !player.level().isClientSide()
                && e.getItem().has(DataComponents.FOOD)) {
                fire(EventGroups.ITEM, "foodEaten", itemId(e.getItem()), new GameEventJS().player(player).item(e.getItem()));
            }
        });

        NeoForge.EVENT_BUS.addListener((ItemTossEvent e) -> {
            if (!wants(EventGroups.ITEM, "dropped") || e.getPlayer().level().isClientSide()) {
                return;
            }
            ItemStack stack = e.getEntity().getItem();
            if (fire(EventGroups.ITEM, "dropped", itemId(stack), new GameEventJS().player(e.getPlayer()).item(stack).entity(e.getEntity()))) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((ItemEntityPickupEvent.Pre e) -> {
            if (!wants(EventGroups.ITEM, "pickedUp") || e.getPlayer().level().isClientSide()) {
                return;
            }
            ItemStack stack = e.getItemEntity().getItem();
            if (fire(EventGroups.ITEM, "pickedUp", itemId(stack), new GameEventJS().player(e.getPlayer()).item(stack).entity(e.getItemEntity()))) {
                e.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
            }
        });
    }

    // -- players -------------------------------------------------------------------------------------------------

    private static void registerPlayers() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (wants(EventGroups.PLAYER, "loggedIn")) {
                fire(EventGroups.PLAYER, "loggedIn", null, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (wants(EventGroups.PLAYER, "loggedOut")) {
                fire(EventGroups.PLAYER, "loggedOut", null, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (wants(EventGroups.PLAYER, "respawned")) {
                fire(EventGroups.PLAYER, "respawned", null, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity().level().isClientSide()) {
                return;
            }
            if (e.getEntity().tickCount % com.itmecasey.modjs.unify.InventoryUnifier.INTERVAL == 0) {
                com.itmecasey.modjs.unify.InventoryUnifier.swap(e.getEntity());
            }
            if (wants(EventGroups.PLAYER, "tick")) {
                fire(EventGroups.PLAYER, "tick", null, new GameEventJS().player(e.getEntity()));
            }
            if ((wants(EventGroups.PLAYER, "sneakStarted") || wants(EventGroups.PLAYER, "sneakStopped"))) {
                int sneakChange = com.itmecasey.modjs.event.SneakTracker.update(e.getEntity().getUUID(), e.getEntity().isShiftKeyDown());
                if (sneakChange != 0) {
                    fire(EventGroups.PLAYER, sneakChange > 0 ? "sneakStarted" : "sneakStopped", null, new GameEventJS().player(e.getEntity()));
                }
            }
        });

        NeoForge.EVENT_BUS.addListener((AdvancementEvent.AdvancementEarnEvent e) -> {
            if (wants(EventGroups.PLAYER, "advancement") && !e.getEntity().level().isClientSide()) {
                String id = e.getAdvancement().id().toString();
                fire(EventGroups.PLAYER, "advancement", id, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((ServerChatEvent e) -> {
            if (!wants(EventGroups.PLAYER, "chat") && !wants(EventGroups.PLAYER, "decorateChat")) {
                return;
            }
            GameEventJS js = new GameEventJS().player(e.getPlayer()).message(e.getRawText());
            if (wants(EventGroups.PLAYER, "decorateChat")) {
                fire(EventGroups.PLAYER, "decorateChat", null, js);
            }
            boolean cancelled = fire(EventGroups.PLAYER, "chat", null, js);
            if (cancelled) {
                e.setCanceled(true);
            } else if (!js.getMessage().equals(e.getRawText())) {
                e.setMessage(Component.literal(js.getMessage()));
            }
        });
    }

    // -- entities ------------------------------------------------------------------------------------------------

    private static void registerEntities() {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ENTITY, "spawned")) {
                return;
            }
            if (fire(EventGroups.ENTITY, "spawned", entityId(e.getEntity()), new GameEventJS().entity(e.getEntity()).level(e.getLevel()))) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            LivingEntity entity = e.getEntity();
            if (entity.level().isClientSide()) {
                return;
            }
            boolean cancelled = false;
            if (wants(EventGroups.ENTITY, "death")) {
                GameEventJS js = new GameEventJS().entity(entity).level(entity.level());
                Entity attacker = e.getSource().getEntity();
                if (attacker instanceof Player p) {
                    js.player(p);
                }
                js.damageType(e.getSource().type().msgId());
                cancelled = fire(EventGroups.ENTITY, "death", entityId(entity), js);
            }
            if (!cancelled && entity instanceof ServerPlayer player && wants(EventGroups.PLAYER, "death")) {
                cancelled = fire(EventGroups.PLAYER, "death", null, new GameEventJS().player(player).damageType(e.getSource().type().msgId()));
            }
            if (cancelled) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e) -> {
            LivingEntity entity = e.getEntity();
            if (entity.level().isClientSide() || !wants(EventGroups.ENTITY, "hurt")) {
                return;
            }
            GameEventJS js = new GameEventJS().entity(entity).level(entity.level()).amount(e.getAmount())
                .damageType(e.getSource().type().msgId());
            if (e.getSource().getEntity() instanceof Player p) {
                js.player(p);
            }
            boolean cancelled = fire(EventGroups.ENTITY, "hurt", entityId(entity), js);
            if (cancelled) {
                e.setCanceled(true);
            } else if (js.amountChanged()) {
                e.setAmount(js.finalAmount());
            }
        });
    }

    // -- more KubeJS events --------------------------------------------------------------------------------------

    private static void registerExtras() {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.CommandEvent e) -> {
            if (wants(EventGroups.SERVER, "basicCommand")) {
                if (fire(EventGroups.SERVER, "basicCommand", null, new GameEventJS().message(e.getParseResults().getReader().getString()))) {
                    e.setCanceled(true);
                    return;
                }
            }
            if (!wants(EventGroups.SERVER, "command")) {
                return;
            }
            String line = e.getParseResults().getReader().getString();
            GameEventJS js = new GameEventJS().message(line);
            if (e.getParseResults().getContext().getSource().getEntity() instanceof Player p) {
                js.player(p);
            }
            String name = line.startsWith("/") ? line.substring(1) : line;
            int space = name.indexOf(' ');
            if (fire(EventGroups.SERVER, "command", space < 0 ? name : name.substring(0, space), js)) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load e) -> {
            if (e.getLevel().isClientSide() || !com.itmecasey.modjs.unify.UnifyConfig.oreChunks()
                || !(e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) {
                return;
            }
            com.itmecasey.modjs.unify.OreUnifier.convertChunk(chunk, com.itmecasey.modjs.unify.OreUnifier.blockMap());
        });

        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof Level level && !level.isClientSide() && wants(EventGroups.LEVEL, "unloaded")) {
                fire(EventGroups.LEVEL, "unloaded", level.dimension().location().toString(), new GameEventJS().level(level));
            }
        });

        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Start e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.LEVEL, "beforeExplosion")) {
                return;
            }
            if (fire(EventGroups.LEVEL, "beforeExplosion", null, new GameEventJS().level(e.getLevel()))) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) -> {
            if (!e.getLevel().isClientSide() && wants(EventGroups.LEVEL, "afterExplosion")) {
                fire(EventGroups.LEVEL, "afterExplosion", null, new GameEventJS().level(e.getLevel()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Open e) -> {
            if (!e.getEntity().level().isClientSide() && wants(EventGroups.PLAYER, "inventoryOpened")) {
                fire(EventGroups.PLAYER, "inventoryOpened", null, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close e) -> {
            if (!e.getEntity().level().isClientSide() && wants(EventGroups.PLAYER, "inventoryClosed")) {
                fire(EventGroups.PLAYER, "inventoryClosed", null, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((BlockEvent.FarmlandTrampleEvent e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.BLOCK, "farmlandTrampled")) {
                return;
            }
            GameEventJS js = new GameEventJS().entity(e.getEntity()).block(e.getPos(), e.getState());
            js.level((Level) e.getLevel());
            if (fire(EventGroups.BLOCK, "farmlandTrampled", blockId(e.getState()), js)) {
                e.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Open e) -> {
            if (!e.getEntity().level().isClientSide() && e.getContainer() instanceof ChestMenu && wants(EventGroups.PLAYER, "chestOpened")) {
                fire(EventGroups.PLAYER, "chestOpened", null, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close e) -> {
            if (!e.getEntity().level().isClientSide() && e.getContainer() instanceof ChestMenu && wants(EventGroups.PLAYER, "chestClosed")) {
                fire(EventGroups.PLAYER, "chestClosed", null, new GameEventJS().player(e.getEntity()));
            }
        });

        NeoForge.EVENT_BUS.addListener((MobSpawnEvent.PositionCheck e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ENTITY, "checkSpawn")) {
                return;
            }
            GameEventJS js = new GameEventJS().entity(e.getEntity()).level(e.getLevel().getLevel());
            if (fire(EventGroups.ENTITY, "checkSpawn", entityId(e.getEntity()), js)) {
                e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            }
        });
    }

    // -- levels and server ---------------------------------------------------------------------------------------

    private static void registerLevelAndServer() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (!e.getLevel().isClientSide() && wants(EventGroups.LEVEL, "tick")) {
                fire(EventGroups.LEVEL, "tick", e.getLevel().dimension().location().toString(), new GameEventJS().level(e.getLevel()));
            }
        });

        NeoForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof Level level && !level.isClientSide() && wants(EventGroups.LEVEL, "loaded")) {
                fire(EventGroups.LEVEL, "loaded", level.dimension().location().toString(), new GameEventJS().level(level));
            }
        });

        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            ServerState.set(e.getServer());
            DimensionManager.restore(e.getServer());
            if (wants(EventGroups.SERVER, "loaded")) {
                fire(EventGroups.SERVER, "loaded", null, new GameEventJS().server(e.getServer()));
            }
        });

        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            if (wants(EventGroups.SERVER, "unloaded")) {
                fire(EventGroups.SERVER, "unloaded", null, new GameEventJS().server(e.getServer()));
            }
            DimensionManager.clear();
            ServerState.clear();
        });

        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Scheduler.tick();
            if (wants(EventGroups.SERVER, "tick")) {
                fire(EventGroups.SERVER, "tick", null, new GameEventJS().server(e.getServer()));
            }
        });
    }

    // -- item modification ---------------------------------------------------------------------------------------

    /** Mod-bus listener: applies {@code ItemEvents.modification} to the default components of existing items. */
    public static void modifyComponents(ModifyDefaultComponentsEvent event) {
        event.getAllItems().forEach(item -> {
            ItemModifications.Changes changes = ItemModifications.forItem(BuiltInRegistries.ITEM.getKey(item).toString());
            if (changes == null) {
                return;
            }
            event.modify(item, builder -> {
                if (changes.getMaxStackSize() != null) {
                    builder.set(DataComponents.MAX_STACK_SIZE, changes.getMaxStackSize());
                }
                if (changes.getMaxDamage() != null) {
                    builder.set(DataComponents.MAX_DAMAGE, changes.getMaxDamage());
                }
                if (changes.getRarity() != null) {
                    builder.set(DataComponents.RARITY, switch (changes.getRarity().toLowerCase()) {
                        case "uncommon" -> net.minecraft.world.item.Rarity.UNCOMMON;
                        case "rare" -> net.minecraft.world.item.Rarity.RARE;
                        case "epic" -> net.minecraft.world.item.Rarity.EPIC;
                        default -> net.minecraft.world.item.Rarity.COMMON;
                    });
                }
            });
        });
    }
}
