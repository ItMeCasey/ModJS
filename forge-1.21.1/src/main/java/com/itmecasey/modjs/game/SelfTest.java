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

        java.util.List<String> traced = new java.util.ArrayList<>();
        com.itmecasey.modjs.event.EventTrace.start(traced::add, false);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        com.itmecasey.modjs.event.EventTrace.stop();
        check("trace reports events with details", traced.stream().anyMatch(l -> l.startsWith("PlayerEvents.loggedIn") && l.contains("player=ModJSTester")));

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

        BlockPos chestPos = new BlockPos(2, 100, 2);
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        BlockEntityJS chest = new UtilsBinding().blockEntity("minecraft:overworld", 2, 100, 2);
        check("block entity found at a chest", chest != null && chest.getId().equals("minecraft:chest"));
        if (chest != null) {
            chest.setItem(3, "minecraft:diamond", 5);
            check("block entity inventory round trip", chest.hasInventory() && chest.getSize() == 27
                && chest.getItemId(3).equals("minecraft:diamond") && chest.getCount(3) == 5 && chest.getItems().size() == 1);
            check("block entity nbt readable", chest.getNbt().contains("minecraft:diamond"));
        }
        check("no block entity on plain blocks", new UtilsBinding().blockEntity("minecraft:overworld", 0, 100, 0) == null);

        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess());
        for (String name : new String[] {"void_test", "flat_test", "nether_copy"}) {
            byte[] bytes = com.itmecasey.modjs.pack.GeneratedData.SERVER.get("modjs", "dimension/" + name + ".json");
            boolean ok = false;
            if (bytes != null) {
                var json = com.google.gson.JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                var result = net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops, json);
                result.error().ifPresent(e -> LOGGER.info("SELFTEST dimension {} error: {}", name, e.message()));
                ok = result.result().isPresent();
            }
            check("generated dimension " + name + " parses as a level stem", ok);
        }
        check("DimensionEvents.drop removes a dimension", com.itmecasey.modjs.pack.GeneratedData.SERVER.get("modjs", "dimension/dropped.json") == null);

        DimensionManager dimensions = new DimensionManager();
        org.mozilla.javascript.NativeObject spec = new org.mozilla.javascript.NativeObject();
        spec.put("generator", spec, "void");
        dimensions.delete("modjs:selftest_world");
        boolean created = dimensions.create("modjs:selftest_world", spec);
        check("Dimensions.create builds a dimension at run time", created && dimensions.exists("modjs:selftest_world"));
        check("Dimensions.create refuses a duplicate id", !dimensions.create("modjs:selftest_world", spec));
        ServerLevel runtime = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.ResourceLocation.parse("modjs:selftest_world")));
        check("run-time dimension is a real level", runtime != null && runtime.getBlockState(new BlockPos(0, 64, 0)).isAir());
        if (runtime != null) {
            runtime.setBlockAndUpdate(new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState());
            check("run-time dimension keeps blocks", runtime.getBlockState(new BlockPos(0, 64, 0)).is(Blocks.STONE));
            check("Dimensions.teleport rejects an unknown dimension", !dimensions.teleport(player, "modjs:no_such_world", 0, 64, 0));
        }
        check("Dimensions.delete removes it", dimensions.delete("modjs:selftest_world") && !dimensions.exists("modjs:selftest_world"));

        com.itmecasey.modjs.unify.Unifier unifier = com.itmecasey.modjs.unify.Unifier.current();
        LOGGER.info("SELFTEST unify groups {}", unifier.groups());
        check("Unify picks our item as canonical", "modjs:tin_ingot".equals(unifier.canonicalFor("minecraft:iron_ingot")));
        check("Unify rewrote recipe outputs and inputs", unifier.outputsRewritten() > 0 && unifier.inputsRewritten() > 0);
        check("Unify added a conversion recipe", unifier.conversionRecipes().containsKey("modjs:unify/modjs_tin_ingot_from_minecraft_iron_ingot")
            && server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().toString().equals("modjs:unify/modjs_tin_ingot_from_minecraft_iron_ingot")));

        var modifier = new com.itmecasey.modjs.unify.UnifyLootModifier(new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0]);
        it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> drops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        drops.add(new ItemStack(Items.IRON_INGOT, 3));
        drops.add(new ItemStack(Items.STICK, 2));
        var replaced = modifier.apply(drops, null);
        check("Unify replaces non-canonical drops", replaced.size() == 2 && replaced.get(0).getCount() == 3
            && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(replaced.get(0).getItem()).toString().equals("modjs:tin_ingot")
            && replaced.get(1).is(Items.STICK));

        player.getInventory().setItem(5, new ItemStack(Items.IRON_INGOT, 7));
        int swapped = com.itmecasey.modjs.unify.InventoryUnifier.swap(player);
        ItemStack inSlot = player.getInventory().getItem(5);
        check("Unify swaps inventory stacks", swapped >= 1 && inSlot.getCount() == 7
            && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inSlot.getItem()).toString().equals("modjs:tin_ingot"));
        player.getInventory().setItem(5, ItemStack.EMPTY);

        check("Unify fluid group picks our fluid", unifier.fluidGroups().size() == 1 && "modjs:test_goo".equals(unifier.fluidCanonicalFor("minecraft:lava")));
        for (String blockId : new String[] {"zinc_ore"}) {
            check("registered unify block " + blockId, net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                .anyMatch(b -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString().equals("modjs:" + blockId)));
        }
        for (String itemId : new String[] {"crushed_zinc", "zinc_clump", "zinc_shard", "zinc_dirty_dust", "raw_zinc"}) {
            check("registered unify item " + itemId, net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
                .anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals("modjs:" + itemId)));
        }

        var oreBlocks = com.itmecasey.modjs.unify.OreUnifier.blockMap();
        check("Unify maps foreign ores to the canonical ore", oreBlocks.get(Blocks.COAL_ORE) != null
            && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(oreBlocks.get(Blocks.COAL_ORE)).toString().equals("modjs:zinc_ore"));
        boolean zincInWorldgen = false;
        boolean coalInWorldgen = false;
        for (var biome : server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)) {
            for (var step : biome.getGenerationSettings().features()) {
                for (var placed : step) {
                    if (placed.value().feature().value().config() instanceof net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration ore) {
                        for (var target : ore.targetStates) {
                            zincInWorldgen |= target.state.is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("modjs:zinc_ore")));
                            coalInWorldgen |= target.state.is(Blocks.COAL_ORE) || target.state.is(Blocks.DEEPSLATE_COAL_ORE);
                        }
                    }
                }
            }
        }
        check("Unify swaps ore features in biomes", zincInWorldgen && !coalInWorldgen);
        BlockPos orePos = new BlockPos(4, 100, 4);
        level.setBlockAndUpdate(orePos, Blocks.COAL_ORE.defaultBlockState());
        int converted = com.itmecasey.modjs.unify.OreUnifier.convertChunk(level.getChunkAt(orePos), oreBlocks);
        check("Unify converts ore blocks in a chunk", converted >= 1 && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(orePos).getBlock()).toString().equals("modjs:zinc_ore"));
        level.setBlockAndUpdate(orePos, Blocks.AIR.defaultBlockState());

        LOGGER.info("SELFTEST DONE failures={}", failures);
    }
}
