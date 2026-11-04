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
import net.minecraftforge.common.MinecraftForge;
import com.itmecasey.modjs.item.ItemModifications;
import net.minecraftforge.event.GatherComponentsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

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
        registerComponentChanges();
    }

    private static boolean fire(EventGroup group, String name, String id, GameEventJS event) {
        if (!group.hasHandlers(name)) {
            return false;
        }
        return group.post(name, id, event);
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
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> {
            GameEventJS js = new GameEventJS().player(e.getPlayer()).block(e.getPos(), e.getState());
            js.level((Level) e.getLevel());
            if (fire(EventGroups.BLOCK, "broken", blockId(e.getState()), js)) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            Entity placer = e.getEntity();
            GameEventJS js = new GameEventJS().entity(placer).block(e.getPos(), e.getPlacedBlock());
            if (placer instanceof Player player) {
                js.player(player);
            }
            if (fire(EventGroups.BLOCK, "placed", blockId(e.getPlacedBlock()), js)) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.BLOCK, "rightClicked")) {
                return;
            }
            BlockState state = e.getLevel().getBlockState(e.getPos());
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).block(e.getPos(), state)
                .item(e.getItemStack()).hand(e.getHand().name()).face(e.getFace() == null ? null : e.getFace().getName());
            if (fire(EventGroups.BLOCK, "rightClicked", blockId(state), js)) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.BLOCK, "leftClicked")) {
                return;
            }
            BlockState state = e.getLevel().getBlockState(e.getPos());
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).block(e.getPos(), state)
                .item(e.getItemStack()).hand(e.getHand().name()).face(e.getFace() == null ? null : e.getFace().getName());
            if (fire(EventGroups.BLOCK, "leftClicked", blockId(state), js)) {
                e.setCanceled(true);
            }
        });
    }

    // -- items ---------------------------------------------------------------------------------------------------

    private static void registerItems() {
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ITEM, "rightClicked")) {
                return;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack())
                .hand(e.getHand().name());
            if (fire(EventGroups.ITEM, "rightClicked", itemId(e.getItemStack()), js)) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ITEM, "entityInteracted")) {
                return;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack())
                .entity(e.getTarget()).hand(e.getHand().name());
            if (fire(EventGroups.ITEM, "entityInteracted", itemId(e.getItemStack()), js)) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent e) -> {
            if (wants(EventGroups.ITEM, "crafted")) {
                fire(EventGroups.ITEM, "crafted", itemId(e.getCrafting()), new GameEventJS().player(e.getEntity()).item(e.getCrafting()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.ItemSmeltedEvent e) -> {
            if (wants(EventGroups.ITEM, "smelted")) {
                fire(EventGroups.ITEM, "smelted", itemId(e.getSmelting()), new GameEventJS().player(e.getEntity()).item(e.getSmelting()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish e) -> {
            if (wants(EventGroups.ITEM, "foodEaten") && e.getEntity() instanceof Player player && !player.level().isClientSide()
                && e.getItem().has(DataComponents.FOOD)) {
                fire(EventGroups.ITEM, "foodEaten", itemId(e.getItem()), new GameEventJS().player(player).item(e.getItem()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ItemTossEvent e) -> {
            if (!wants(EventGroups.ITEM, "dropped") || e.getPlayer().level().isClientSide()) {
                return;
            }
            ItemStack stack = e.getEntity().getItem();
            if (fire(EventGroups.ITEM, "dropped", itemId(stack), new GameEventJS().player(e.getPlayer()).item(stack).entity(e.getEntity()))) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((EntityItemPickupEvent e) -> {
            if (!wants(EventGroups.ITEM, "pickedUp") || e.getEntity().level().isClientSide()) {
                return;
            }
            ItemStack stack = e.getItem().getItem();
            if (fire(EventGroups.ITEM, "pickedUp", itemId(stack), new GameEventJS().player(e.getEntity()).item(stack).entity(e.getItem()))) {
                e.setCanceled(true);
            }
        });
    }

    // -- players -------------------------------------------------------------------------------------------------

    private static void registerPlayers() {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (wants(EventGroups.PLAYER, "loggedIn")) {
                fire(EventGroups.PLAYER, "loggedIn", null, new GameEventJS().player(e.getEntity()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (wants(EventGroups.PLAYER, "loggedOut")) {
                fire(EventGroups.PLAYER, "loggedOut", null, new GameEventJS().player(e.getEntity()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (wants(EventGroups.PLAYER, "respawned")) {
                fire(EventGroups.PLAYER, "respawned", null, new GameEventJS().player(e.getEntity()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && e.side.isServer() && wants(EventGroups.PLAYER, "tick")) {
                fire(EventGroups.PLAYER, "tick", null, new GameEventJS().player(e.player));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((AdvancementEvent.AdvancementEarnEvent e) -> {
            if (wants(EventGroups.PLAYER, "advancement") && !e.getEntity().level().isClientSide()) {
                String id = e.getAdvancement().id().toString();
                fire(EventGroups.PLAYER, "advancement", id, new GameEventJS().player(e.getEntity()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ServerChatEvent e) -> {
            if (!wants(EventGroups.PLAYER, "chat")) {
                return;
            }
            GameEventJS js = new GameEventJS().player(e.getPlayer()).message(e.getRawText());
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
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ENTITY, "spawned")) {
                return;
            }
            if (fire(EventGroups.ENTITY, "spawned", entityId(e.getEntity()), new GameEventJS().entity(e.getEntity()).level(e.getLevel()))) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
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

        MinecraftForge.EVENT_BUS.addListener((LivingHurtEvent e) -> {
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
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof Level level && !level.isClientSide() && wants(EventGroups.LEVEL, "unloaded")) {
                fire(EventGroups.LEVEL, "unloaded", level.dimension().location().toString(), new GameEventJS().level(level));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ExplosionEvent.Start e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.LEVEL, "beforeExplosion")) {
                return;
            }
            if (fire(EventGroups.LEVEL, "beforeExplosion", null, new GameEventJS().level(e.getLevel()))) {
                e.setCanceled(true);
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) -> {
            if (!e.getLevel().isClientSide() && wants(EventGroups.LEVEL, "afterExplosion")) {
                fire(EventGroups.LEVEL, "afterExplosion", null, new GameEventJS().level(e.getLevel()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerContainerEvent.Open e) -> {
            if (!e.getEntity().level().isClientSide() && wants(EventGroups.PLAYER, "inventoryOpened")) {
                fire(EventGroups.PLAYER, "inventoryOpened", null, new GameEventJS().player(e.getEntity()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((PlayerContainerEvent.Close e) -> {
            if (!e.getEntity().level().isClientSide() && wants(EventGroups.PLAYER, "inventoryClosed")) {
                fire(EventGroups.PLAYER, "inventoryClosed", null, new GameEventJS().player(e.getEntity()));
            }
        });
    }

    // -- levels and server ---------------------------------------------------------------------------------------

    private static void registerLevelAndServer() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && e.side.isServer() && wants(EventGroups.LEVEL, "tick")) {
                fire(EventGroups.LEVEL, "tick", e.level.dimension().location().toString(), new GameEventJS().level(e.level));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof Level level && !level.isClientSide() && wants(EventGroups.LEVEL, "loaded")) {
                fire(EventGroups.LEVEL, "loaded", level.dimension().location().toString(), new GameEventJS().level(level));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            ServerState.set(e.getServer());
            if (wants(EventGroups.SERVER, "loaded")) {
                fire(EventGroups.SERVER, "loaded", null, new GameEventJS().server(e.getServer()));
            }
        });

        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            if (wants(EventGroups.SERVER, "unloaded")) {
                fire(EventGroups.SERVER, "unloaded", null, new GameEventJS().server(e.getServer()));
            }
            ServerState.clear();
        });

        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            Scheduler.tick();
            if (wants(EventGroups.SERVER, "tick")) {
                fire(EventGroups.SERVER, "tick", null, new GameEventJS().server(e.getServer()));
            }
        });
    }

    // -- item modification ---------------------------------------------------------------------------------------

    public static void registerComponentChanges() {
        MinecraftForge.EVENT_BUS.addListener((GatherComponentsEvent.Item e) -> apply(e));
    }

    private static void apply(GatherComponentsEvent.Item e) {
        if (!(e.getOwner() instanceof net.minecraft.world.item.Item item)) {
            return;
        }
        ItemModifications.Changes changes = ItemModifications.forItem(BuiltInRegistries.ITEM.getKey(item).toString());
        if (changes == null) {
            return;
        }
        if (changes.getMaxStackSize() != null) {
            e.register(DataComponents.MAX_STACK_SIZE, changes.getMaxStackSize());
        }
        if (changes.getMaxDamage() != null) {
            e.register(DataComponents.MAX_DAMAGE, changes.getMaxDamage());
        }
        if (changes.getRarity() != null) {
            e.register(DataComponents.RARITY, switch (changes.getRarity().toLowerCase()) {
                case "uncommon" -> net.minecraft.world.item.Rarity.UNCOMMON;
                case "rare" -> net.minecraft.world.item.Rarity.RARE;
                case "epic" -> net.minecraft.world.item.Rarity.EPIC;
                default -> net.minecraft.world.item.Rarity.COMMON;
            });
        }
    }
}
