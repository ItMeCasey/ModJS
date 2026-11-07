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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> run(e.getServer()));
    }

    private static int runCommand(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, String line, net.minecraft.commands.CommandSourceStack source) {
        try {
            return dispatcher.execute(line, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return -1;
        }
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

        BreakBlockEvent brokenStone = new BreakBlockEvent(level, pos, Blocks.STONE.defaultBlockState(), player);
        NeoForge.EVENT_BUS.post(brokenStone);
        boolean stoneCancelled = brokenStone.isCanceled();
        check("BlockEvents.broken cancel", stoneCancelled);

        BreakBlockEvent brokenDirt = new BreakBlockEvent(level, pos, Blocks.DIRT.defaultBlockState(), player);
        NeoForge.EVENT_BUS.post(brokenDirt);
        boolean dirtCancelled = brokenDirt.isCanceled();
        check("BlockEvents.broken id filter leaves other blocks alone", !dirtCancelled);

        ServerChatEvent chat = new ServerChatEvent(player, "hello there", Component.literal("hello there"));
        NeoForge.EVENT_BUS.post(chat);
        check("PlayerEvents.chat rewrites the message", chat.getMessage().getString().equals("HELLO THERE?"));

        java.util.List<String> traced = new java.util.ArrayList<>();
        com.itmecasey.modjs.event.EventTrace.start(traced::add, false);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        com.itmecasey.modjs.event.EventTrace.stop();
        check("trace reports events with details", traced.stream().anyMatch(l -> l.startsWith("PlayerEvents.loggedIn") && l.contains("player=ModJSTester")));

        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.STICK, 4), new SimpleContainer(1)));

        DamageSource source = level.damageSources().generic();
        LivingIncomingDamageEvent hurt = new LivingIncomingDamageEvent(player, new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 3.0f));
        NeoForge.EVENT_BUS.post(hurt);
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
            net.minecraft.resources.Identifier.parse("modjs:selftest_world")));
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
            && server.getRecipeManager().getRecipes().stream().anyMatch(r -> r.id().identifier().toString().equals("modjs:unify/modjs_tin_ingot_from_minecraft_iron_ingot")));

        var modifier = new com.itmecasey.modjs.unify.UnifyLootModifier(java.util.Optional.empty());
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
        for (var biome : server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)) {
            for (var step : biome.getGenerationSettings().features()) {
                for (var placed : step) {
                    if (placed.value().feature().value() instanceof net.minecraft.world.level.levelgen.feature.AbstractOreFeature ore) {
                        for (var target : ore.targetStates()) {
                            zincInWorldgen |= target.state().is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("modjs:zinc_ore")));
                            coalInWorldgen |= target.state().is(Blocks.COAL_ORE) || target.state().is(Blocks.DEEPSLATE_COAL_ORE);
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

        player.getInventory().setItem(7, ItemStack.EMPTY);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        player.getInventory().setItem(7, new ItemStack(Items.APPLE, 3));
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        check("ServerEvents.compostableRecipes sets a chance", Items.STONE.components().get(net.minecraft.core.component.DataComponents.COMPOSTABLE) instanceof net.minecraft.world.item.component.Compostable stoneCompost
            && stoneCompost.layers().equals(net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt.fromKey(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH)));
        player.getInventory().setItem(7, ItemStack.EMPTY);

        BlockPos stonePos = new BlockPos(6, 100, 6);
        level.setBlockAndUpdate(stonePos, Blocks.STONE.defaultBlockState());
        net.minecraft.world.level.block.state.BlockState stoneState = level.getBlockState(stonePos);
        ServerLevel genLevel = level;
        if (level.getChunkSource().getGenerator().getClass().getSimpleName().equals("FlatLevelSource")) {
            // The game-test overworld is flat and places no features, so generate in a run-time dimension that copies the overworld.
            org.mozilla.javascript.NativeObject genSpec = new org.mozilla.javascript.NativeObject();
            genSpec.put("generator", genSpec, "overworld");
            dimensions.delete("modjs:selftest_gen");
            if (dimensions.create("modjs:selftest_gen", genSpec)) {
                ServerLevel made = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            net.minecraft.resources.Identifier.parse("modjs:selftest_gen")));
                if (made != null) {
                    genLevel = made;
                }
            }
        }
        // The test world is saved between runs, so use chunks far from anything an earlier run generated.
        int baseChunk = 4000 + new java.util.Random().nextInt(20000);
        int generatedOre = 0;
        int vanillaOre = 0;
        for (int cx = baseChunk; cx < baseChunk + 4; cx++) {
            for (int cz = baseChunk; cz < baseChunk + 4; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = genLevel.getChunk(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y <= 60; y++) {
                            net.minecraft.world.level.block.state.BlockState found = chunk.getBlockState(new BlockPos(cx * 16 + x, y, cz * 16 + z));
                            if (found.is(Blocks.SEA_LANTERN)) {
                                generatedOre++;
                            } else if (found.is(Blocks.IRON_ORE)) {
                                vanillaOre++;
                            }
                        }
                    }
                }
            }
        }
        LOGGER.info("SELFTEST worldgen: {} script-added ore blocks and {} vanilla iron ore blocks in 16 generated chunks ({})", generatedOre, vanillaOre, genLevel.getChunkSource().getGenerator().getClass().getSimpleName());
        if (genLevel.getChunkSource().getGenerator().getClass().getSimpleName().equals("FlatLevelSource")) {
            LOGGER.info("SELFTEST worldgen check skipped: the game-test overworld is a flat world, which places no features");
        } else {
            check("WorldgenEvents.addOre ore appears in generated chunks", generatedOre > 0 && vanillaOre > 0);
        }
        if (genLevel != level) {
            dimensions.delete("modjs:selftest_gen");
        }
        TextBinding textHelper = new TextBinding();
        check("Text.of and Text.translate build components", textHelper.of("plain").getString().equals("plain") && textHelper.translate("modjs.selftest.key").getString().equals("modjs.selftest.key"));
        check("Text.red and Text.color style the text", textHelper.red("warn").getStyle().getColor() != null && textHelper.gold("x").getStyle().getColor() != null
            && textHelper.color("no_such_color", "y").getStyle().getColor() == null && textHelper.color("no_such_color", "y").getString().equals("y"));
        check("BlockEvents.modification changes hardness, resistance and light",
            stoneState.getDestroySpeed(level, stonePos) == 9.0f && Blocks.STONE.getExplosionResistance() == 77.0f && stoneState.getLightEmission() == 5);
        level.setBlockAndUpdate(stonePos, Blocks.AIR.defaultBlockState());

        ItemStack propsStack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("modjs:props_item")));
        check("item builder: maxStackSize", propsStack.getMaxStackSize() == 16);
        check("item builder: rarity", propsStack.getRarity() == net.minecraft.world.item.Rarity.RARE);
        check("item builder: glow", propsStack.hasFoil());
        check("item builder: food", propsStack.has(net.minecraft.core.component.DataComponents.FOOD));
        check("item builder: unstackable", new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("modjs:props_unstackable"))).getMaxStackSize() == 1);
        net.minecraft.world.level.block.Block propsBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("modjs:props_block"));
        check("block builder: light level, tool requirement and resistance", propsBlock.defaultBlockState().getLightEmission() == 7
            && propsBlock.defaultBlockState().requiresCorrectToolForDrops() && propsBlock.getExplosionResistance() == 11.0f);
        check("block builder: noItem registers no block item", net.minecraft.world.item.Item.byBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("modjs:props_noitem"))) == Items.AIR);
        ItemStack rubyPick = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("modjs:ruby_pickaxe")));
        ItemStack rubyHelmet = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("modjs:ruby_helmet")));
        check("custom tool tier sets durability", rubyPick.getMaxDamage() == 900);
        check("custom armor material sets durability", rubyHelmet.getMaxDamage() == 11 * 30);

        BlockPos cratePos = new BlockPos(8, 100, 8);
        level.setBlockAndUpdate(cratePos, net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("modjs:test_crate")).defaultBlockState());
        BlockEntityJS crate = new UtilsBinding().blockEntity("minecraft:overworld", 8, 100, 8);
        check("container block has a block entity with its rows", crate != null && crate.getId().equals("modjs:container") && crate.getSize() == 18);
        if (crate != null) {
            crate.setItem(4, "minecraft:emerald", 9);
            check("container block keeps items in its data", crate.getNbt().contains("minecraft:emerald"));
        }
        level.setBlockAndUpdate(cratePos, Blocks.AIR.defaultBlockState());

        Integer copperColor = com.itmecasey.modjs.unify.ColorSampler.sample("copper");
        LOGGER.info("SELFTEST sampled copper color {}", copperColor == null ? "none" : Integer.toHexString(copperColor));
        check("Unify samples a material color from the game's textures", copperColor != null);


        com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        net.minecraft.commands.CommandSourceStack operator = server.createCommandSourceStack();
        net.minecraft.commands.CommandSourceStack anyone = server.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS);
        check("commandRegistry add runs a script command with an argument", runCommand(dispatcher, "selftest_hello bob", operator) == 7);
        check("commandRegistry add rejects a bad argument", runCommand(dispatcher, "selftest_hello", operator) == -1);
        check("commandRegistry add with a number range", runCommand(dispatcher, "selftest_pick 5", operator) == 5 && runCommand(dispatcher, "selftest_pick 500", operator) == -1);
        com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.CommandSourceStack> secret = dispatcher.getRoot().getChild("selftest_secret");
        check("commandRegistry add keeps a permission level", secret != null && secret.canUse(operator) && !secret.canUse(anyone));
        check("commandRegistry alias runs the same command", runCommand(dispatcher, "selftest_hi alice", operator) == 7);
        check("commandRegistry remove drops a vanilla command and its aliases", dispatcher.getRoot().getChild("seed") == null
            && dispatcher.getRoot().getChild("teleport") == null && dispatcher.getRoot().getChild("tp") == null);
        check("commandRegistry remove drops a sub-command", dispatcher.getRoot().getChild("time") != null && dispatcher.getRoot().getChild("time").getChild("query") == null
            && dispatcher.getRoot().getChild("time").getChild("set") != null);
        com.mojang.brigadier.tree.CommandNode<net.minecraft.commands.CommandSourceStack> list = dispatcher.getRoot().getChild("list");
        check("commandRegistry edit changes who may use a command", list != null && list.canUse(operator) && !list.canUse(anyone));
        check("commandRegistry edit replaces what a command does", runCommand(dispatcher, "help", operator) == 42);
        check("commandRegistry rename moves a command", dispatcher.getRoot().getChild("kill") == null && dispatcher.getRoot().getChild("selftest_kill") != null);

        ScoreboardBinding board = new ScoreboardBinding();
        check("Scoreboard script call set and added a score", board.get("Alice", "st_points") == 8 && board.has("Alice", "st_points"));
        board.set(player, "st_points", 11);
        check("Scoreboard takes a player as holder", board.get(player.getScoreboardName(), "st_points") == 11);
        check("Scoreboard add returns the new total", board.add("Alice", "st_points", -3) == 5);
        check("Scoreboard scores lists highest first", board.scores("st_points").size() == 2 && board.scores("st_points").get(0).getScore() == 11);
        board.setDisplay("sidebar", "st_points");
        check("Scoreboard shows an objective in a display slot", "st_points".equals(board.getDisplay("sidebar")));
        board.setDisplay("sidebar", null);
        check("Scoreboard clears a display slot", board.getDisplay("sidebar") == null);
        check("Scoreboard teams hold members", "st_team".equals(board.teamOf("Alice")) && board.teamMembers("st_team").contains("Alice"));
        board.reset("Alice", "st_points");
        check("Scoreboard reset removes a score", !board.has("Alice", "st_points") && board.get("Alice", "st_points") == 0);
        board.reset(player.getScoreboardName());
        board.removeObjective("st_points");
        check("Scoreboard removeObjective deletes it", !board.hasObjective("st_points"));
        board.leaveTeam("Alice");
        board.removeTeam("st_team");
        check("Scoreboard removeTeam deletes it", board.teams().stream().noneMatch("st_team"::equals));


        com.itmecasey.modjs.pack.GeneratedData serverData = com.itmecasey.modjs.pack.GeneratedData.SERVER;
        for (String name : new String[] {"modjs:selftest_void", "modjs:selftest_amp", "minecraft:large_biomes"}) {
            String ns = name.substring(0, name.indexOf(':'));
            byte[] bytes = serverData.get(ns, "worldgen/world_preset/" + name.substring(name.indexOf(':') + 1) + ".json");
            boolean ok = false;
            if (bytes != null) {
                var json = com.google.gson.JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                var result = net.minecraft.world.level.levelgen.presets.WorldPreset.DIRECT_CODEC.parse(ops, json);
                result.error().ifPresent(e -> LOGGER.info("SELFTEST world preset {} error: {}", name, e.message()));
                ok = result.result().isPresent();
            }
            check("generated world type " + name + " parses as a world preset", ok);
        }
        String normalTag = new String(serverData.get("minecraft", "tags/worldgen/world_preset/normal.json"), java.nio.charset.StandardCharsets.UTF_8);
        check("world types: new ones join the list and vanilla ones stay", normalTag.contains("modjs:selftest_void") && normalTag.contains("modjs:selftest_amp") && normalTag.contains("minecraft:flat"));
        check("world types: hide removes a vanilla one, visible false keeps one out", !normalTag.contains("single_biome_surface") && !normalTag.contains("selftest_hidden"));
        check("world types: a hidden one still exists", serverData.has("modjs", "worldgen/world_preset/selftest_hidden.json"));
        check("world types: drop removes a script-made one", !serverData.has("modjs", "worldgen/world_preset/selftest_gone.json"));
        com.google.gson.JsonObject largeBiomes = com.google.gson.JsonParser.parseString(new String(serverData.get("minecraft", "worldgen/world_preset/large_biomes.json"), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        check("world types: edit keeps the other dimensions and swaps the one given", largeBiomes.getAsJsonObject("dimensions").size() == 3
            && largeBiomes.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether").getAsJsonObject("generator").get("type").getAsString().equals("minecraft:flat")
            && largeBiomes.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator").get("type").getAsString().equals("minecraft:noise"));
        com.google.gson.JsonObject worldTypeLang = new com.google.gson.JsonObject();
        com.itmecasey.modjs.worldgen.WorldTypes.addNames(worldTypeLang);
        check("world types: labels become language entries", "Self Test Void".equals(worldTypeLang.get("generator.modjs.selftest_void").getAsString())
            && "Self Test Amplified".equals(worldTypeLang.get("generator.modjs.selftest_amp").getAsString()));

        LOGGER.info("SELFTEST DONE failures={}", failures);
        server.halt(false);
    }
}
