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

        boolean stoneCancelled = BlockEvent.BreakEvent.BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.STONE.defaultBlockState(), player, Result.DEFAULT));
        check("BlockEvents.broken cancel", stoneCancelled);

        boolean dirtCancelled = BlockEvent.BreakEvent.BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.DIRT.defaultBlockState(), player, Result.DEFAULT));
        check("BlockEvents.broken id filter leaves other blocks alone", !dirtCancelled);

        ServerChatEvent chat = new ServerChatEvent(player, "hello there", Component.literal("hello there"));
        ServerChatEvent.BUS.post(chat);
        check("PlayerEvents.chat rewrites the message", chat.getMessage().getString().equals("HELLO THERE?"));

        java.util.List<String> traced = new java.util.ArrayList<>();
        com.itmecasey.modjs.event.EventTrace.start(traced::add, false);
        PlayerEvent.PlayerLoggedInEvent.BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        com.itmecasey.modjs.event.EventTrace.stop();
        check("trace reports events with details", traced.stream().anyMatch(l -> l.startsWith("PlayerEvents.loggedIn") && l.contains("player=ModJSTester")));

        PlayerEvent.PlayerLoggedInEvent.BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        PlayerEvent.ItemCraftedEvent.BUS.post(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(Items.STICK, 4), new SimpleContainer(1)));

        DamageSource source = level.damageSources().generic();
        LivingHurtEvent hurt = new LivingHurtEvent(player, source, 3.0f);
        LivingHurtEvent.BUS.post(hurt);
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

        var modifier = new com.itmecasey.modjs.unify.UnifyLootModifier(new net.minecraft.world.level.storage.loot.predicates.LootItemCondition[0]);
        it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> drops = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        drops.add(new ItemStack(Items.IRON_INGOT, 3));
        drops.add(new ItemStack(Items.STICK, 2));
        var replaced = modifier.apply(null, drops, null);
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
                    if (placed.value().feature().value().config() instanceof net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration ore) {
                        for (var target : ore.targetStates) {
                            zincInWorldgen |= target.state.is(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("modjs:zinc_ore")));
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

        player.getInventory().setItem(7, ItemStack.EMPTY);
        net.minecraftforge.event.TickEvent.PlayerTickEvent.Post.BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent.Post(player));
        player.getInventory().setItem(7, new ItemStack(Items.APPLE, 3));
        net.minecraftforge.event.TickEvent.PlayerTickEvent.Post.BUS.post(new net.minecraftforge.event.TickEvent.PlayerTickEvent.Post(player));
        check("ServerEvents.compostableRecipes sets a chance", net.minecraft.world.level.block.ComposterBlock.COMPOSTABLES.getFloat(Items.STONE) == 0.85f);
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

        BlockPos machinePos = new BlockPos(10, 100, 10);
        net.minecraft.world.level.block.Block machineBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("modjs:test_machine"));
        level.setBlockAndUpdate(machinePos, machineBlock.defaultBlockState());
        check("scripted block: orientable model gives it a facing property", machineBlock.defaultBlockState().getProperties().stream().anyMatch(property -> property.getName().equals("facing")));
        check("scripted block: per-face textures leave a plain block without properties", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.parse("modjs:test_columns")).defaultBlockState().getProperties().isEmpty());
        if (level.getBlockEntity(machinePos) instanceof com.itmecasey.modjs.registry.ScriptBlocks.ScriptEntity machine) {
            check("scripted block: slots, stack limit and values come from the script", machine.getSize() == 3 && machine.getMaxStackSize() == 32 && machine.getValue("max") == 0);
            check("scripted block: insert fills slots up to the stack limit and returns the rest", machine.insert("minecraft:iron_ingot", 70) == 0
                && machine.getCount(0) == 32 && machine.getCount(1) == 32 && machine.getCount(2) == 6 && machine.insert("minecraft:iron_ingot", 70) == 44 && machine.getCount(2) == 32);
            check("scripted block: shrink and clearSlot", machine.shrink(2, 30) == 30 && machine.getCount(2) == 2 && machine.shrink(2, 9) == 2 && machine.getItemId(2).equals("minecraft:air"));
            machine.setData("mode", "fast");
            machine.setValue("progress", 7);
            machine.serverTick();
            machine.serverTick();
            machine.serverTick();
            check("scripted block: onTick runs on the server each interval", machine.getData("ticks") instanceof Number n && n.intValue() >= 3);
            net.minecraft.nbt.CompoundTag update = machine.getUpdateTag(level.registryAccess());
            check("scripted block: the update packet carries data and values for clients", update.getStringOr("ModJSData", "").contains("\"mode\":\"fast\"") && update.getIntArray("ModJSValues").orElse(new int[] {-1})[0] == 7);
            net.minecraft.nbt.CompoundTag saved = machine.saveWithFullMetadata(level.registryAccess());
            net.minecraft.world.level.block.entity.BlockEntity rebuilt = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(machinePos, machineBlock.defaultBlockState(), saved, level.registryAccess());
            if (rebuilt instanceof com.itmecasey.modjs.registry.ScriptBlocks.ScriptEntity reloaded) {
                check("scripted block: items, data and values survive saving and loading", reloaded.getCount(0) == 32 && reloaded.getItemId(1).equals("minecraft:iron_ingot")
                    && "fast".equals(reloaded.getData("mode")) && reloaded.getValue("progress") == 7 && reloaded.getData("ticks") instanceof Number t && t.intValue() >= 3);
                net.minecraft.world.inventory.AbstractContainerMenu menu = reloaded.createMenu(1, player.getInventory(), player);
                check("scripted block: its menu has the block's slots and the player inventory", menu != null && menu.slots.size() == 3 + 36);
                check("scripted block: a screen button runs onButton on the server", menu != null && menu.clickMenuButton(player, 0)
                    && "go".equals(reloaded.getData("pressed")) && reloaded.getValue("progress") == 40);
                check("scripted block: a button index without an id is refused", menu != null && !menu.clickMenuButton(player, 5));
                check("scripted block: the menu syncs named values", menu instanceof com.itmecasey.modjs.registry.ScriptBlocks.ScriptMenu scriptMenu && scriptMenu.value("progress") == 40);
            } else {
                check("scripted block: a saved block entity loads again", false);
            }
        } else {
            check("scripted block has a block entity", false);
        }
        level.setBlockAndUpdate(machinePos, Blocks.AIR.defaultBlockState());

        check("namespaces: an item registers under its own namespace and a plain id stays in modjs", net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals("nsdemo:ns_gem"))
            && net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals("nsdemo:ns_plain"))
            && net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .noneMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals("modjs:ns_gem"))
            && net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals("modjs:smoke_item")));
        check("namespaces: a block with its block item and a fluid register under their own namespace", net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
            .anyMatch(b -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString().equals("nsdemo:ns_block"))
            && net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .anyMatch(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).toString().equals("nsdemo:ns_block"))
            && net.minecraft.core.registries.BuiltInRegistries.FLUID.stream()
            .anyMatch(f -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f).toString().equals("nsdemo:ns_goo")));
        check("namespaces: block data (loot table) is written under the namespace folder", com.itmecasey.modjs.pack.GeneratedData.SERVER.has("nsdemo",
            com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/ns_block.json")
            && !com.itmecasey.modjs.pack.GeneratedData.SERVER.has("modjs", com.itmecasey.modjs.format.DataFolders.lootTable() + "/blocks/ns_block.json"));

        check("in-world recipes: the server script defined them", com.itmecasey.modjs.worldcraft.WorldRecipes.all().size() >= 6);
        // a fresh game test world can start players in creative, where recipes keep items and skip block drops
        com.itmecasey.modjs.worldcraft.WorldCraftAdapters.Player survivor = new com.itmecasey.modjs.worldcraft.WorldCraftAdapters.Player(player, true);
        net.minecraft.core.BlockPos wcPos = new net.minecraft.core.BlockPos(20, 100, 20);
        level.setBlockAndUpdate(wcPos, Blocks.STONE.defaultBlockState());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT, 3));
        boolean wcClicked = com.itmecasey.modjs.worldcraft.WorldCraftAdapters.click(survivor, wcPos, false, net.minecraft.world.InteractionHand.MAIN_HAND);
        check("in-world recipes: an item on a block changes the block, drops items and uses the item", wcClicked && level.getBlockState(wcPos).is(Blocks.COBBLESTONE)
            && player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).getCount() == 2
            && com.itmecasey.modjs.worldcraft.WorldCraftAdapters.recentSpawns().contains("minecraft:gravel x2"));
        check("in-world recipes: a click that matches no recipe is left alone", !com.itmecasey.modjs.worldcraft.WorldCraftAdapters.click(survivor, wcPos, false, net.minecraft.world.InteractionHand.MAIN_HAND));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        net.minecraft.core.BlockPos waterPos = new net.minecraft.core.BlockPos(22, 100, 20);
        level.setBlockAndUpdate(waterPos, Blocks.WATER.defaultBlockState());
        net.minecraft.world.entity.item.ItemEntity coal = new net.minecraft.world.entity.item.ItemEntity(level, 22.5, 100.3, 20.5, new ItemStack(Items.COAL, 2));
        net.minecraft.world.entity.item.ItemEntity sand = new net.minecraft.world.entity.item.ItemEntity(level, 22.4, 100.3, 20.6, new ItemStack(Items.SAND, 1));
        level.addFreshEntity(coal);
        level.addFreshEntity(sand);
        com.itmecasey.modjs.worldcraft.WorldRecipes.items(new com.itmecasey.modjs.worldcraft.WorldCraftAdapters.Level(level),
            java.util.List.of(new com.itmecasey.modjs.worldcraft.WorldCraftAdapters.Drop(coal), new com.itmecasey.modjs.worldcraft.WorldCraftAdapters.Drop(sand)));
        check("in-world recipes: dropped items in water are used up and make the result", coal.isRemoved() && sand.isRemoved()
            && com.itmecasey.modjs.worldcraft.WorldCraftAdapters.recentSpawns().contains("minecraft:diamond x1"));

        net.minecraft.core.BlockPos dirtPos = new net.minecraft.core.BlockPos(24, 100, 20);
        level.setBlockAndUpdate(dirtPos, Blocks.DIRT.defaultBlockState());
        com.itmecasey.modjs.worldcraft.WorldCraftAdapters.crush(level, dirtPos);
        check("in-world recipes: an anvil landing changes the block under it", level.getBlockState(dirtPos).is(Blocks.COARSE_DIRT));

        net.minecraft.core.BlockPos sandPos = new net.minecraft.core.BlockPos(26, 100, 20);
        level.setBlockAndUpdate(sandPos, Blocks.SAND.defaultBlockState());
        com.itmecasey.modjs.worldcraft.WorldCraftAdapters.strike(level, sandPos.above());
        check("in-world recipes: lightning changes the block it strikes", level.getBlockState(sandPos).is(Blocks.GLASS));

        net.minecraft.core.BlockPos wcOrePos = new net.minecraft.core.BlockPos(28, 100, 20);
        level.setBlockAndUpdate(wcOrePos, Blocks.COAL_ORE.defaultBlockState());
        boolean oreCancelled = com.itmecasey.modjs.worldcraft.WorldCraftAdapters.breakBlock(survivor, wcOrePos);
        check("in-world recipes: breaking a block can replace its drops", oreCancelled && level.getBlockState(wcOrePos).isAir()
            && com.itmecasey.modjs.worldcraft.WorldCraftAdapters.recentSpawns().contains("minecraft:emerald x2"));

        net.minecraft.core.BlockPos obsidianPos = new net.minecraft.core.BlockPos(30, 100, 20);
        level.setBlockAndUpdate(obsidianPos, Blocks.OBSIDIAN.defaultBlockState());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        check("in-world recipes: a left click recipe keeps the tool when consume is 0", com.itmecasey.modjs.worldcraft.WorldCraftAdapters.click(survivor, obsidianPos, true, net.minecraft.world.InteractionHand.MAIN_HAND)
            && level.getBlockState(obsidianPos).is(Blocks.CRYING_OBSIDIAN) && !player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).isEmpty());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        for (net.minecraft.core.BlockPos cleanup : new net.minecraft.core.BlockPos[] {wcPos, waterPos, dirtPos, sandPos, wcOrePos, obsidianPos}) {
            level.setBlockAndUpdate(cleanup, Blocks.AIR.defaultBlockState());
        }
        check("in-world recipes: recipe viewers get a page for the items they involve", new com.itmecasey.modjs.client.RecipeViewerEventJS.AddInformation().info().containsKey("minecraft:flint"));

        com.itmecasey.modjs.worldcraft.LevelJS waLevel = new com.itmecasey.modjs.worldcraft.LevelJS(new com.itmecasey.modjs.worldcraft.WorldCraftAdapters.Level(level));
        for (int i = 0; i < 4; i++) {
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(40, 100 + i, 40), Blocks.OAK_LOG.defaultBlockState());
        }
        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(41, 104, 41), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(new net.minecraft.core.BlockPos(43, 100, 40), Blocks.OAK_LOG.defaultBlockState());
        check("world access: reads the block id and the dimension", "minecraft:oak_log".equals(waLevel.getBlockId(40, 100, 40)) && waLevel.getDimension().equals("minecraft:overworld")
            && waLevel.matches(40, 100, 40, "#minecraft:logs") && !waLevel.matches(40, 100, 40, "minecraft:stone"));
        check("world access: searches a box for a tag", waLevel.findBlocks(38, 99, 38, 45, 106, 45, "#minecraft:logs").getLength() == 6);
        check("world access: flood fill follows faces, diagonals and the limit", waLevel.connected(40, 100, 40, "minecraft:oak_log", false, 0).getLength() == 4
            && waLevel.connected(40, 100, 40, "minecraft:oak_log", true, 0).getLength() == 5 && waLevel.connectedSame(40, 103, 40, true, 3).getLength() == 3);
        check("world access: destroys a block with drops", waLevel.destroyBlock(43, 100, 40, true) && level.getBlockState(new net.minecraft.core.BlockPos(43, 100, 40)).isAir()
            && !waLevel.destroyBlock(43, 100, 40, true));
        net.minecraft.core.BlockPos waFarm = new net.minecraft.core.BlockPos(46, 99, 40);
        level.setBlockAndUpdate(waFarm, Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(waFarm.above(), Blocks.WHEAT.defaultBlockState());
        org.mozilla.javascript.NativeObject waBefore = waLevel.getBlock(46, 100, 40);
        boolean waGrew = waLevel.bonemeal(46, 100, 40);
        org.mozilla.javascript.NativeObject waProps = (org.mozilla.javascript.NativeObject) ((org.mozilla.javascript.NativeObject) waLevel.getBlock(46, 100, 40)).get("properties", waBefore);
        check("world access: bone meal grows a crop and the new state reads back", waGrew && Integer.parseInt(String.valueOf(waProps.get("age", waProps))) > 0);
        boolean waCancelled = BlockEvent.BreakEvent.BUS.post(new BlockEvent.BreakEvent(level, new net.minecraft.core.BlockPos(40, 100, 40), Blocks.OAK_LOG.defaultBlockState(), player, Result.DEFAULT));
        check("BlockEvents.broken takes block tags", waCancelled);
        for (net.minecraft.core.BlockPos waClean : new net.minecraft.core.BlockPos[] {new net.minecraft.core.BlockPos(40, 100, 40), new net.minecraft.core.BlockPos(40, 101, 40), new net.minecraft.core.BlockPos(40, 102, 40),
            new net.minecraft.core.BlockPos(40, 103, 40), new net.minecraft.core.BlockPos(41, 104, 41), waFarm, waFarm.above()}) {
            level.setBlockAndUpdate(waClean, Blocks.AIR.defaultBlockState());
        }

        // player-rules block break (breakBlock): tool wear, no BlockEvents.broken recursion, reach limit
        net.minecraft.world.phys.Vec3 sbOrigin = player.position();
        net.minecraft.world.item.ItemStack sbAxe = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sbAxe);
        net.minecraft.world.level.GameType sbMode = player.gameMode.getGameModeForPlayer();
        sbSetMode(player, net.minecraft.world.level.GameType.SURVIVAL); // the test player starts in creative on some loaders; creative breaks use no tool
        player.setPos(52.5, 100.0, 40.5);
        net.minecraft.core.BlockPos sbBase = new net.minecraft.core.BlockPos(50, 100, 40);
        for (int i = 0; i < 4; i++) {
            level.setBlockAndUpdate(sbBase.above(i), Blocks.OAK_LOG.defaultBlockState());
        }
        GameEventJS sbEvent = new GameEventJS().player(player).level(level);
        boolean sbChained = false;
        try {
            sbChained = sbEvent.breakBlock(50, 100, 40, true);
        } catch (NullPointerException cancelledForFakePlayer) {
            // the script cancelled the break and the game then tried to resync the client of this connectionless test player
        }
        check("breakBlock runs BlockEvents.broken handlers only when asked (the self test script cancels log breaks)", !sbChained && level.getBlockState(sbBase).is(Blocks.OAK_LOG));
        boolean sbBroke = sbEvent.breakBlock(50, 100, 40);
        check("breakBlock skips BlockEvents.broken (no recursion) and breaks the block with the player's tool", sbBroke && level.getBlockState(sbBase).isAir() && sbAxe.getDamageValue() == 1);
        check("breakBlock refuses air and blocks out of reach", !sbEvent.breakBlock(50, 100, 40) && !sbEvent.breakBlock(500, 100, 40));
        int sbMany = sbEvent.breakBlocks(java.util.List.of(java.util.List.of(50, 101, 40), java.util.List.of(50, 102, 40), java.util.List.of(50, 103, 40)));
        check("breakBlocks breaks a whole list and wears the tool once per block", sbMany == 3 && level.getBlockState(sbBase.above(3)).isAir() && sbAxe.getDamageValue() == 4);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        player.setPos(sbOrigin.x, sbOrigin.y, sbOrigin.z);
        sbSetMode(player, sbMode);

        // script config: registers a typed config, checks values, creates its file with comments, reloads it from disk
        java.nio.file.Path cfgFile = com.itmecasey.modjs.script.ScriptManager.root().resolve("config/selftest_config.json5");
        Object cfgResult = com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.SERVER).eval("(function () { var c = Config.register('selftest_config', { speed: Config.number(1.5, { min: 0, max: 10, comment: 'Speed' }), mode: Config.choice('fast', ['fast', 'slow']) }); c.speed = 4; c.speed = 99; return '' + c.speed + c.mode; })()", "selftest-config");
        java.nio.file.Path cfgPath = cfgFile;
        boolean cfgFileOk = false;
        boolean cfgReload = false;
        try {
            cfgFileOk = java.nio.file.Files.readString(cfgPath).contains("// Speed");
            java.nio.file.Files.writeString(cfgFile, "{ \"speed\": 7 }");
            cfgReload = Boolean.TRUE.equals(com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.SERVER).eval("Config.get('selftest_config').reload() && Config.get('selftest_config').speed === 7 && Config.get('selftest_config').mode === 'fast'", "selftest-config-reload"));
            java.nio.file.Files.deleteIfExists(cfgFile);
        } catch (java.io.IOException cfgError) {
            cfgFileOk = false;
        }
        check("config: registers a typed config, refuses an out-of-range value, writes a commented file and reloads it", "4fast".equals(String.valueOf(cfgResult)) && cfgFileOk && cfgReload);

        // user files: the startup routines (folder scaffolding, typings, jsconfig) leave every existing file of the modjs folder byte for byte as it was
        java.nio.file.Path guardRoot = com.itmecasey.modjs.script.ScriptManager.root();
        String guardProblems = com.itmecasey.modjs.script.UserFilesGuard.run(guardRoot, () -> {
            com.itmecasey.modjs.script.ScriptFolders.prepare(guardRoot);
            com.itmecasey.modjs.script.ScriptFolders.prepare(guardRoot);
        }, "probe/", "config/selftest_config", "persistent/");
        check("user files: the startup routines leave every existing file in the modjs folder untouched" + (guardProblems.isEmpty() ? "" : " (" + guardProblems + ")"), guardProblems.isEmpty());

        // two scripts (a startup script and a server script) share configs: reading, writing, saving, onChange, a missing name and a name registered twice
        com.itmecasey.modjs.config.ConfigManager.pollNow(false);
        Object crossOk = com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.SERVER).eval("typeof global.cfgCross !== 'undefined' && Object.keys(global.cfgCross).length === 4 && Object.keys(global.cfgCross).every(function (k) { return global.cfgCross[k] === true; })"
            + " && global.cfgChanges.join(',').indexOf('value') >= 0 && JsonIO.read('config/selftest_shared.json5').value === global.cfgA.value", "selftest-config-cross");
        String crossDetail = Boolean.TRUE.equals(crossOk) ? "" : " " + com.itmecasey.modjs.script.ScriptManager.of(com.itmecasey.modjs.script.ScriptType.SERVER).eval("(typeof global.cfgCross === 'undefined' ? 'no result' : JSON.stringify(global.cfgCross)) + ' changes=' + (typeof global.cfgChanges === 'undefined' ? 'none' : global.cfgChanges.join(',')) + ' file=' + (JsonIO.exists('config/selftest_shared.json5') ? JSON.stringify(JsonIO.read('config/selftest_shared.json5')) : 'missing') + ' a=' + (typeof global.cfgA === 'undefined' ? 'none' : global.cfgA.value)", "selftest-config-detail");
        check("config: two scripts share a config (read, write, save, onChange, missing name, name registered twice)" + crossDetail, Boolean.TRUE.equals(crossOk));
        try {
            for (String leftover : new String[] {"selftest_shared", "selftest_collide"}) {
                try (java.util.stream.Stream<java.nio.file.Path> found = java.nio.file.Files.list(com.itmecasey.modjs.script.ScriptManager.root().resolve("config"))) {
                    for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) found.filter(p -> p.getFileName().toString().startsWith(leftover))::iterator) {
                        java.nio.file.Files.deleteIfExists(path);
                    }
                }
            }
        } catch (java.io.IOException ignored) {
            // the files are left for the next run, which reads their values as its starting point
        }

        // villager trades: a replaced tier, remove-all-then-add, a cleared profession, and conditions on the villager type
        java.util.List<String> sbCleric = sbOffers(level, net.minecraft.world.entity.npc.villager.VillagerProfession.CLERIC, null);
        check("villagers: replaceTrades gives a cleric exactly the new tier (" + sbCleric + ")", sbCleric.size() == 2
            && sbCleric.contains("minecraft:diamond x2 <- minecraft:emerald x3 max 7 xp 9") && sbCleric.contains("minecraft:gold_ingot x1 <- minecraft:emerald x1 + minecraft:book x1 max 12 xp 1"));
        java.util.List<String> sbTools = sbOffers(level, net.minecraft.world.entity.npc.villager.VillagerProfession.TOOLSMITH, null);
        check("villagers: removeTrades of everything and addTrade leave a toolsmith one trade (" + sbTools + ")", sbTools.equals(java.util.List.of("minecraft:iron_axe x1 <- minecraft:emerald x4 max 12 xp 1")));
        java.util.List<String> sbPlains = sbOffers(level, net.minecraft.world.entity.npc.villager.VillagerProfession.MASON, "plains");
        java.util.List<String> sbDesert = sbOffers(level, net.minecraft.world.entity.npc.villager.VillagerProfession.MASON, "desert");
        check("villagers: villager type conditions offer only the trade that fits (plains " + sbPlains + ", desert " + sbDesert + ")",
            sbPlains.size() == 1 && sbPlains.get(0).startsWith("minecraft:granite") && sbDesert.size() == 1 && sbDesert.get(0).startsWith("minecraft:stone"));
        java.util.List<String> sbLibrarian = sbOffers(level, net.minecraft.world.entity.npc.villager.VillagerProfession.LIBRARIAN, null);
        check("villagers: clearTrades leaves a librarian without trades (" + sbLibrarian + ")", sbLibrarian.isEmpty());

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
    }

    /** Sets the game mode of the connectionless test player (the public methods broadcast to its missing connection). */
    private static void sbSetMode(ServerPlayer player, net.minecraft.world.level.GameType mode) {
        try {
            java.lang.reflect.Method method = net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredMethod("setGameModeForPlayer",
                net.minecraft.world.level.GameType.class, net.minecraft.world.level.GameType.class);
            method.setAccessible(true);
            method.invoke(player.gameMode, mode, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.npc.villager.Villager> sbVillagerType() {
        return (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.npc.villager.Villager>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "villager"));
    }

    /** The level 1 trades a fresh villager of this profession (and type, when given) rolls, as text. */
    private static java.util.List<String> sbOffers(net.minecraft.server.level.ServerLevel level, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.npc.villager.VillagerProfession> key, String type) {
        var holder = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION).getOrThrow(key);
        net.minecraft.world.entity.npc.villager.Villager villager = new net.minecraft.world.entity.npc.villager.Villager(sbVillagerType(), level);
        var data = villager.getVillagerData().withProfession(holder).withLevel(1);
        if (type != null) {
            data = data.withType(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.VILLAGER_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", type))));
        }
        villager.setVillagerData(data);
        return sbText(villager.getOffers());
    }

    private static java.util.List<String> sbText(net.minecraft.world.item.trading.MerchantOffers offers) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (net.minecraft.world.item.trading.MerchantOffer offer : offers) {
            StringBuilder text = new StringBuilder();
            text.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getResult().getItem())).append(" x").append(offer.getResult().getCount());
            text.append(" <- ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getBaseCostA().getItem())).append(" x").append(offer.getBaseCostA().getCount());
            if (!offer.getCostB().isEmpty()) {
                text.append(" + ").append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getCostB().getItem())).append(" x").append(offer.getCostB().getCount());
            }
            text.append(" max ").append(offer.getMaxUses()).append(" xp ").append(offer.getXp());
            out.add(text.toString());
        }
        return out;
    }
}
