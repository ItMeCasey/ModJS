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
import net.minecraftforge.fml.LogicalSide;

/**
 * Bridges Forge gameplay events to script handlers. Every bridge checks for handlers first so unused events cost
 * almost nothing, and cancellable events return whether a script cancelled them.
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
        net.minecraftforge.event.server.ServerAboutToStartEvent.BUS.addListener((net.minecraftforge.event.server.ServerAboutToStartEvent e) -> refreshModifiedItems());
        net.minecraftforge.event.OnDatapackSyncEvent.BUS.addListener((net.minecraftforge.event.OnDatapackSyncEvent e) -> refreshModifiedItems());
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

    private static boolean isServer(PlayerInteractEvent event) {
        return event.getSide() == LogicalSide.SERVER;
    }

    // -- blocks --------------------------------------------------------------------------------------------------

    private static void registerBlocks() {
        BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent e) -> {
            GameEventJS js = new GameEventJS().player(e.getPlayer()).block(e.getPos(), e.getState());
            js.level((net.minecraft.world.level.Level) e.getLevel());
            return fire(EventGroups.BLOCK, "broken", blockId(e.getState()), js);
        });

        BlockEvent.EntityPlaceEvent.BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            Entity placer = e.getEntity();
            GameEventJS js = new GameEventJS().entity(placer).block(e.getPos(), e.getPlacedBlock());
            if (placer instanceof Player player) {
                js.player(player);
            }
            return fire(EventGroups.BLOCK, "placed", blockId(e.getPlacedBlock()), js);
        });

        PlayerInteractEvent.RightClickBlock.BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (!isServer(e) || !wants(EventGroups.BLOCK, "rightClicked")) {
                return false;
            }
            BlockState state = e.getLevel().getBlockState(e.getPos());
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).block(e.getPos(), state)
                .item(e.getItemStack()).hand(e.getHand().name()).face(e.getFace() == null ? null : e.getFace().getName());
            return fire(EventGroups.BLOCK, "rightClicked", blockId(state), js);
        });

        PlayerInteractEvent.LeftClickBlock.BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (!isServer(e) || !wants(EventGroups.BLOCK, "leftClicked")) {
                return false;
            }
            BlockState state = e.getLevel().getBlockState(e.getPos());
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).block(e.getPos(), state)
                .item(e.getItemStack()).hand(e.getHand().name()).face(e.getFace() == null ? null : e.getFace().getName());
            return fire(EventGroups.BLOCK, "leftClicked", blockId(state), js);
        });
    }

    // -- items ---------------------------------------------------------------------------------------------------

    private static void registerItems() {
        PlayerInteractEvent.RightClickItem.BUS.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (!isServer(e) || !wants(EventGroups.ITEM, "rightClicked")) {
                return false;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack())
                .hand(e.getHand().name());
            return fire(EventGroups.ITEM, "rightClicked", itemId(e.getItemStack()), js);
        });

        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> {
            if (!isServer(e) || !wants(EventGroups.ITEM, "entityInteracted")) {
                return false;
            }
            GameEventJS js = new GameEventJS().player(e.getEntity()).level(e.getLevel()).item(e.getItemStack())
                .entity(e.getTarget()).hand(e.getHand().name());
            return fire(EventGroups.ITEM, "entityInteracted", itemId(e.getItemStack()), js);
        });

        PlayerEvent.ItemCraftedEvent.BUS.addListener((PlayerEvent.ItemCraftedEvent e) -> {
            if (wants(EventGroups.ITEM, "crafted")) {
                fire(EventGroups.ITEM, "crafted", itemId(e.getCrafting()), new GameEventJS().player(e.getEntity()).item(e.getCrafting()));
            }
        });

        PlayerEvent.ItemSmeltedEvent.BUS.addListener((PlayerEvent.ItemSmeltedEvent e) -> {
            if (wants(EventGroups.ITEM, "smelted")) {
                fire(EventGroups.ITEM, "smelted", itemId(e.getSmelting()), new GameEventJS().player(e.getEntity()).item(e.getSmelting()));
            }
        });

        LivingEntityUseItemEvent.Finish.BUS.addListener((LivingEntityUseItemEvent.Finish e) -> {
            if (wants(EventGroups.ITEM, "foodEaten") && e.getEntity() instanceof Player player && !player.level().isClientSide()
                && e.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
                fire(EventGroups.ITEM, "foodEaten", itemId(e.getItem()), new GameEventJS().player(player).item(e.getItem()));
            }
        });

        ItemTossEvent.BUS.addListener((ItemTossEvent e) -> {
            if (!wants(EventGroups.ITEM, "dropped") || e.getPlayer().level().isClientSide()) {
                return false;
            }
            ItemStack stack = e.getEntity().getItem();
            return fire(EventGroups.ITEM, "dropped", itemId(stack), new GameEventJS().player(e.getPlayer()).item(stack).entity(e.getEntity()));
        });

        EntityItemPickupEvent.BUS.addListener((EntityItemPickupEvent e) -> {
            if (!wants(EventGroups.ITEM, "pickedUp") || e.getEntity().level().isClientSide()) {
                return false;
            }
            ItemStack stack = e.getItem().getItem();
            return fire(EventGroups.ITEM, "pickedUp", itemId(stack), new GameEventJS().player(e.getEntity()).item(stack).entity(e.getItem()));
        });
    }

    // -- players -------------------------------------------------------------------------------------------------

    private static void registerPlayers() {
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (wants(EventGroups.PLAYER, "loggedIn")) {
                fire(EventGroups.PLAYER, "loggedIn", null, new GameEventJS().player(e.getEntity()));
            }
        });

        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (wants(EventGroups.PLAYER, "loggedOut")) {
                fire(EventGroups.PLAYER, "loggedOut", null, new GameEventJS().player(e.getEntity()));
            }
        });

        PlayerEvent.PlayerRespawnEvent.BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (wants(EventGroups.PLAYER, "respawned")) {
                fire(EventGroups.PLAYER, "respawned", null, new GameEventJS().player(e.getEntity()));
            }
        });

        TickEvent.PlayerTickEvent.Post.BUS.addListener((TickEvent.PlayerTickEvent.Post e) -> {
            if (e.side() == LogicalSide.SERVER && e.player().tickCount % com.itmecasey.modjs.unify.InventoryUnifier.INTERVAL == 0) {
                com.itmecasey.modjs.unify.InventoryUnifier.swap(e.player());
            }
            if (e.side() == LogicalSide.SERVER && wants(EventGroups.PLAYER, "tick")) {
                fire(EventGroups.PLAYER, "tick", null, new GameEventJS().player(e.player()));
            }
        });

        AdvancementEvent.AdvancementEarnEvent.BUS.addListener((AdvancementEvent.AdvancementEarnEvent e) -> {
            if (wants(EventGroups.PLAYER, "advancement") && !e.getEntity().level().isClientSide()) {
                String id = e.getAdvancement().id().toString();
                fire(EventGroups.PLAYER, "advancement", id, new GameEventJS().player(e.getEntity()));
            }
        });

        ServerChatEvent.BUS.addListener((ServerChatEvent e) -> {
            if (!wants(EventGroups.PLAYER, "chat")) {
                return false;
            }
            GameEventJS js = new GameEventJS().player(e.getPlayer()).message(e.getRawText());
            boolean cancelled = fire(EventGroups.PLAYER, "chat", null, js);
            if (!cancelled && !js.getMessage().equals(e.getRawText())) {
                e.setMessage(Component.literal(js.getMessage()));
            }
            return cancelled;
        });
    }

    // -- entities ------------------------------------------------------------------------------------------------

    private static void registerEntities() {
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.ENTITY, "spawned")) {
                return false;
            }
            return fire(EventGroups.ENTITY, "spawned", entityId(e.getEntity()), new GameEventJS().entity(e.getEntity()).level(e.getLevel()));
        });

        LivingDeathEvent.BUS.addListener((LivingDeathEvent e) -> {
            LivingEntity entity = e.getEntity();
            if (entity.level().isClientSide()) {
                return false;
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
            return cancelled;
        });

        LivingHurtEvent.BUS.addListener((LivingHurtEvent e) -> {
            LivingEntity entity = e.getEntity();
            if (entity.level().isClientSide() || !wants(EventGroups.ENTITY, "hurt")) {
                return false;
            }
            GameEventJS js = new GameEventJS().entity(entity).level(entity.level()).amount(e.getAmount())
                .damageType(e.getSource().type().msgId());
            if (e.getSource().getEntity() instanceof Player p) {
                js.player(p);
            }
            boolean cancelled = fire(EventGroups.ENTITY, "hurt", entityId(entity), js);
            if (!cancelled && js.amountChanged()) {
                e.setAmount(js.finalAmount());
            }
            return cancelled;
        });
    }

    // -- more KubeJS events --------------------------------------------------------------------------------------

    private static void registerExtras() {
        net.minecraftforge.event.level.ChunkEvent.Load.BUS.addListener((net.minecraftforge.event.level.ChunkEvent.Load e) -> {
            if (!com.itmecasey.modjs.unify.UnifyConfig.oreChunks() || !(e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
                || chunk.getLevel().isClientSide()) {
                return;
            }
            com.itmecasey.modjs.unify.OreUnifier.convertChunk(chunk, com.itmecasey.modjs.unify.OreUnifier.blockMap());
        });

        LevelEvent.Unload.BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel() instanceof Level level && !level.isClientSide() && wants(EventGroups.LEVEL, "unloaded")) {
                fire(EventGroups.LEVEL, "unloaded", level.dimension().identifier().toString(), new GameEventJS().level(level));
            }
        });

        ExplosionEvent.Start.BUS.addListener((ExplosionEvent.Start e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.LEVEL, "beforeExplosion")) {
                return false;
            }
            return fire(EventGroups.LEVEL, "beforeExplosion", null, new GameEventJS().level(e.getLevel()));
        });

        net.minecraftforge.event.CommandEvent.BUS.addListener((net.minecraftforge.event.CommandEvent e) -> {
            if (!wants(EventGroups.SERVER, "command")) {
                return false;
            }
            String line = e.getParseResults().getReader().getString();
            GameEventJS js = new GameEventJS().message(line);
            if (e.getParseResults().getContext().getSource().getEntity() instanceof Player p) {
                js.player(p);
            }
            String name = line.startsWith("/") ? line.substring(1) : line;
            int space = name.indexOf(' ');
            return fire(EventGroups.SERVER, "command", space < 0 ? name : name.substring(0, space), js);
        });

        ExplosionEvent.Detonate.BUS.addListener((ExplosionEvent.Detonate e) -> {
            if (!e.getLevel().isClientSide() && wants(EventGroups.LEVEL, "afterExplosion")) {
                fire(EventGroups.LEVEL, "afterExplosion", null, new GameEventJS().level(e.getLevel()));
            }
        });

        PlayerContainerEvent.Open.BUS.addListener((PlayerContainerEvent.Open e) -> {
            if (!e.getEntity().level().isClientSide() && wants(EventGroups.PLAYER, "inventoryOpened")) {
                fire(EventGroups.PLAYER, "inventoryOpened", null, new GameEventJS().player(e.getEntity()));
            }
        });

        PlayerContainerEvent.Close.BUS.addListener((PlayerContainerEvent.Close e) -> {
            if (!e.getEntity().level().isClientSide() && wants(EventGroups.PLAYER, "inventoryClosed")) {
                fire(EventGroups.PLAYER, "inventoryClosed", null, new GameEventJS().player(e.getEntity()));
            }
        });

        PlayerContainerEvent.Open.BUS.addListener((PlayerContainerEvent.Open e) -> {
            if (!e.getEntity().level().isClientSide() && e.getContainer() instanceof net.minecraft.world.inventory.ChestMenu && wants(EventGroups.PLAYER, "chestOpened")) {
                fire(EventGroups.PLAYER, "chestOpened", null, new GameEventJS().player(e.getEntity()));
            }
        });

        PlayerContainerEvent.Close.BUS.addListener((PlayerContainerEvent.Close e) -> {
            if (!e.getEntity().level().isClientSide() && e.getContainer() instanceof net.minecraft.world.inventory.ChestMenu && wants(EventGroups.PLAYER, "chestClosed")) {
                fire(EventGroups.PLAYER, "chestClosed", null, new GameEventJS().player(e.getEntity()));
            }
        });

        BlockEvent.FarmlandTrampleEvent.BUS.addListener((BlockEvent.FarmlandTrampleEvent e) -> {
            if (e.getLevel().isClientSide() || !wants(EventGroups.BLOCK, "farmlandTrampled")) {
                return false;
            }
            GameEventJS js = new GameEventJS().entity(e.getEntity()).block(e.getPos(), e.getState());
            js.level((net.minecraft.world.level.Level) e.getLevel());
            return fire(EventGroups.BLOCK, "farmlandTrampled", blockId(e.getState()), js);
        });
    }

    // -- levels and server ---------------------------------------------------------------------------------------

    private static void registerLevelAndServer() {
        TickEvent.LevelTickEvent.Post.BUS.addListener((TickEvent.LevelTickEvent.Post e) -> {
            if (e.side() == LogicalSide.SERVER && wants(EventGroups.LEVEL, "tick")) {
                fire(EventGroups.LEVEL, "tick", e.level().dimension().identifier().toString(), new GameEventJS().level(e.level()));
            }
        });

        LevelEvent.Load.BUS.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof net.minecraft.world.level.Level level && !level.isClientSide() && wants(EventGroups.LEVEL, "loaded")) {
                fire(EventGroups.LEVEL, "loaded", level.dimension().identifier().toString(), new GameEventJS().level(level));
            }
        });

        ServerStartedEvent.BUS.addListener((ServerStartedEvent e) -> {
            ServerState.set(e.getServer());
            DimensionManager.restore(e.getServer());
            if (wants(EventGroups.SERVER, "loaded")) {
                fire(EventGroups.SERVER, "loaded", null, new GameEventJS().server(e.getServer()));
            }
        });

        ServerStoppedEvent.BUS.addListener((ServerStoppedEvent e) -> {
            if (wants(EventGroups.SERVER, "unloaded")) {
                fire(EventGroups.SERVER, "unloaded", null, new GameEventJS().server(e.getServer()));
            }
            DimensionManager.clear();
            ServerState.clear();
        });

        TickEvent.ServerTickEvent.Post.BUS.addListener((TickEvent.ServerTickEvent.Post e) -> {
            Scheduler.tick();
            if (wants(EventGroups.SERVER, "tick")) {
                fire(EventGroups.SERVER, "tick", null, new GameEventJS().server(e.server()));
            }
        });
    }

    // -- item modification ---------------------------------------------------------------------------------------

    public static void registerComponentChanges() {
        GatherComponentsEvent.Item.BUS.addListener((GatherComponentsEvent.Item e) -> apply(e));
    }

    /**
     * Rebinds the default components of every modified item. In 26.1 the game binds item components after the
     * registries load and again on each data reload, so this runs after both (the Forge component event fires too
     * early for vanilla items).
     */
    public static void refreshModifiedItems() {
        for (String id : ItemModifications.ids()) {
            net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
            ItemModifications.Changes changes = ItemModifications.forItem(id);
            if (key == null || changes == null) {
                continue;
            }
            BuiltInRegistries.ITEM.get(key).ifPresent(reference -> {
                if (!reference.areComponentsBound()) {
                    return;
                }
                net.minecraft.core.component.DataComponentMap.Builder builder = net.minecraft.core.component.DataComponentMap.builder().addAll(reference.components());
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
                reference.components = builder.build();
            });
        }
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
