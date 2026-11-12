package com.itmecasey.modjs.worldcraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

/** Registers the game events in-world crafting listens to. */
public final class WorldCraftHooks {
    private WorldCraftHooks() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (!e.getLevel().isClientSide() && e.getEntity() instanceof ServerPlayer player && WorldCraftAdapters.click(player, e.getPos(), false, e.getHand())) {
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (!e.getLevel().isClientSide() && e.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && e.getEntity() instanceof ServerPlayer player
                && WorldCraftAdapters.click(player, e.getPos(), true, e.getHand())) {
                e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> {
            if (e.getPlayer() instanceof ServerPlayer player && WorldCraftAdapters.breakBlock(player, e.getPos())) {
                e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent e) -> {
            if (!e.getLevel().isClientSide()) {
                WorldCraftAdapters.entityLeft(e.getEntity());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && !e.loadedFromDisk()) {
                WorldCraftAdapters.entityJoined(e.getEntity());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel level) {
                WorldCraftAdapters.levelTick(level);
            }
        });
    }
}
