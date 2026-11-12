package com.itmecasey.modjs.worldcraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

/** Registers the game events in-world crafting listens to (Forge 26 listeners answer true to cancel). */
public final class WorldCraftHooks {
    private WorldCraftHooks() {
    }

    public static void register() {
        PlayerInteractEvent.RightClickBlock.BUS.addListener((PlayerInteractEvent.RightClickBlock e) ->
            !e.getLevel().isClientSide() && e.getEntity() instanceof ServerPlayer player && WorldCraftAdapters.click(player, e.getPos(), false, e.getHand()));
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((PlayerInteractEvent.LeftClickBlock e) ->
            !e.getLevel().isClientSide() && e.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && e.getEntity() instanceof ServerPlayer player
                && WorldCraftAdapters.click(player, e.getPos(), true, e.getHand()));
        BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent e) ->
            e.getPlayer() instanceof ServerPlayer player && WorldCraftAdapters.breakBlock(player, e.getPos()));
        EntityLeaveLevelEvent.BUS.addListener((EntityLeaveLevelEvent e) -> {
            if (!e.getLevel().isClientSide()) {
                WorldCraftAdapters.entityLeft(e.getEntity());
            }
        });
        EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && !e.loadedFromDisk()) {
                WorldCraftAdapters.entityJoined(e.getEntity());
            }
            return false;
        });
        TickEvent.LevelTickEvent.Post.BUS.addListener((TickEvent.LevelTickEvent.Post e) -> {
            if (e.level() instanceof ServerLevel level) {
                WorldCraftAdapters.levelTick(level);
            }
        });
    }
}
