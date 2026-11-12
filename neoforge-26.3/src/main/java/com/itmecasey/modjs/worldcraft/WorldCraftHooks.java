package com.itmecasey.modjs.worldcraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Registers the game events in-world crafting listens to. */
public final class WorldCraftHooks {
    private WorldCraftHooks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (!e.getLevel().isClientSide() && e.getEntity() instanceof ServerPlayer player && WorldCraftAdapters.click(player, e.getPos(), false, e.getHand())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (!e.getLevel().isClientSide() && e.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && e.getEntity() instanceof ServerPlayer player
                && WorldCraftAdapters.click(player, e.getPos(), true, e.getHand())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((BreakBlockEvent e) -> {
            if (e.getPlayer() instanceof ServerPlayer player && WorldCraftAdapters.breakBlock(player, e.getPos())) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e) -> {
            if (!e.getLevel().isClientSide()) {
                WorldCraftAdapters.entityLeft(e.getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && !e.loadedFromDisk()) {
                WorldCraftAdapters.entityJoined(e.getEntity());
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                WorldCraftAdapters.levelTick(level);
            }
        });
    }
}
