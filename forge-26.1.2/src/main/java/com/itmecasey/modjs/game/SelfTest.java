package com.itmecasey.modjs.game;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Development check, enabled with {@code -Dmodjs.selftest=true}. Once the server has started it posts real Forge
 * events for a player and checks that scripts saw them and that cancelling and changing values works.
 * Pair it with {@code run/modjs/server_scripts/selftest.js}.
 */
public final class SelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static int failures;

    private SelfTest() {
    }

    public static void register() {
        if (!Boolean.getBoolean("modjs.selftest")) {
            return;
        }
        ServerStartedEvent.BUS.addListener((ServerStartedEvent e) -> run(e.getServer()));
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            failures++;
        }
        LOGGER.info("SELFTEST {} {}", ok ? "PASS" : "FAIL", name);
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.nameUUIDFromBytes("modjs-selftest".getBytes()), "ModJSTester"), ClientInformation.createDefault());
        BlockPos pos = new BlockPos(0, 100, 0);

        boolean stoneCancelled = BlockEvent.BreakEvent.BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.STONE.defaultBlockState(), player, Result.DEFAULT));
        check("BlockEvents.broken cancel", stoneCancelled);

        boolean dirtCancelled = BlockEvent.BreakEvent.BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.DIRT.defaultBlockState(), player, Result.DEFAULT));
        check("BlockEvents.broken id filter leaves other blocks alone", !dirtCancelled);

        ServerChatEvent chat = new ServerChatEvent(player, "hello there", Component.literal("hello there"));
        ServerChatEvent.BUS.post(chat);
        check("PlayerEvents.chat rewrites the message", chat.getMessage().getString().equals("HELLO THERE"));

        PlayerEvent.PlayerLoggedInEvent.BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        PlayerEvent.ItemCraftedEvent.BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.STICK, 4), new SimpleContainer(1)));

        DamageSource source = level.damageSources().generic();
        LivingHurtEvent hurt = new LivingHurtEvent(player, source, 3.0f);
        LivingHurtEvent.BUS.post(hurt);
        check("EntityEvents.hurt changes the amount", hurt.getAmount() == 6.0f);

        LOGGER.info("SELFTEST DONE failures={}", failures);
    }
}
