package com.itmecasey.modjs.game;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Development check, enabled with {@code -Dmodjs.selftest=true}. Once the server has started it posts real Forge
 * events for a fake player and checks that scripts saw them and that cancelling and changing values works.
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
        // The 1.21.1 game test server only starts when a test exists, so the checks run inside one; see SelfTestCases.
    }

    private static void check(String name, boolean ok) {
        if (!ok) {
            failures++;
        }
        LOGGER.info("SELFTEST {} {}", ok ? "PASS" : "FAIL", name);
    }

    public static void run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.nameUUIDFromBytes("modjs-selftest".getBytes()), "ModJSTester"), ClientInformation.createDefault());
        BlockPos pos = new BlockPos(0, 100, 0);

        BlockEvent.BreakEvent brokenStone = new BlockEvent.BreakEvent(level, pos, Blocks.STONE.defaultBlockState(), player);
        MinecraftForge.EVENT_BUS.post(brokenStone);
        check("BlockEvents.broken cancel", brokenStone.isCanceled());

        BlockEvent.BreakEvent brokenDirt = new BlockEvent.BreakEvent(level, pos, Blocks.DIRT.defaultBlockState(), player);
        MinecraftForge.EVENT_BUS.post(brokenDirt);
        check("BlockEvents.broken id filter leaves other blocks alone", !brokenDirt.isCanceled());

        ServerChatEvent chat = new ServerChatEvent(player, "hello there", Component.literal("hello there"));
        MinecraftForge.EVENT_BUS.post(chat);
        check("PlayerEvents.chat rewrites the message", chat.getMessage().getString().equals("HELLO THERE"));

        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.STICK, 4), new SimpleContainer(1)));

        DamageSource source = level.damageSources().generic();
        LivingHurtEvent hurt = new LivingHurtEvent(player, source, 3.0f);
        MinecraftForge.EVENT_BUS.post(hurt);
        check("EntityEvents.hurt changes the amount", hurt.getAmount() == 6.0f);

        for (String shaped : new String[] {"test_slab", "test_stairs", "test_wall", "test_fence"}) {
            String key = "modjs:" + shaped;
            boolean found = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                .anyMatch(b -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString().equals(key));
            check("registered shaped block " + shaped, found);
        }

        for (String tool : new String[] {"test_pickaxe", "test_sword", "test_helmet", "test_boots"}) {
            String key = "modjs:" + tool;
            boolean found = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals(key));
            check("registered tool " + tool, found);
        }

        check("registered fluid test_goo", net.minecraft.core.registries.BuiltInRegistries.FLUID.stream()
            .anyMatch(f -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f).toString().equals("modjs:test_goo")));
        check("registered fluid test_goo_flowing", net.minecraft.core.registries.BuiltInRegistries.FLUID.stream()
            .anyMatch(f -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f).toString().equals("modjs:test_goo_flowing")));
        check("registered bucket test_goo_bucket", net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals("modjs:test_goo_bucket")));

        LOGGER.info("SELFTEST DONE failures={}", failures);
    }
}
