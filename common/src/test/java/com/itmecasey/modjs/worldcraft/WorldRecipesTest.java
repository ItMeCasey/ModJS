package com.itmecasey.modjs.worldcraft;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.itmecasey.modjs.worldcraft.WorldHandles.ItemEntityHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.ItemHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.LevelHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.PlayerHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldRecipesTest {
    @TempDir
    Path root;

    static final Map<String, Set<String>> TAGS = Map.of("#c:ingots", Set.of("minecraft:iron_ingot", "minecraft:gold_ingot"),
        "#minecraft:logs", Set.of("minecraft:oak_log"));

    static final class Stack implements ItemHandle {
        String id;
        int count;
        int damage;

        Stack(String id, int count) {
            this.id = id;
            this.count = count;
        }

        public String id() { return id; }
        public int count() { return count; }
        public boolean matches(String matcher) { return matcher.equals(id) || (matcher.startsWith("#") && TAGS.getOrDefault(matcher, Set.of()).contains(id)); }
        public void shrink(int amount) { count -= amount; }
        public void damage(int amount) { damage += amount; }
    }

    static final class Player implements PlayerHandle {
        boolean sneaking;
        boolean creative;
        Stack main = new Stack("minecraft:air", 0);
        Stack off = new Stack("minecraft:air", 0);
        final List<String> given = new ArrayList<>();

        public boolean sneaking() { return sneaking; }
        public boolean creative() { return creative; }
        public ItemHandle held(boolean offHand) { return offHand ? off : main; }
        public void give(String id, int count) { given.add(id + " x" + count); }
    }

    static final class Level implements LevelHandle {
        final Map<String, String> blocks = new HashMap<>();
        final List<String> spawned = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        double random = 0.0;

        static String key(int x, int y, int z) { return x + "," + y + "," + z; }

        public boolean blockMatches(int x, int y, int z, String matcher) {
            String spec = blocks.getOrDefault(key(x, y, z), "minecraft:air");
            String base = spec.contains("[") ? spec.substring(0, spec.indexOf('[')) : spec;
            String wantBase = matcher.contains("[") ? matcher.substring(0, matcher.indexOf('[')) : matcher;
            boolean idOk = wantBase.equals(base) || (wantBase.startsWith("#") && TAGS.getOrDefault(wantBase, Set.of()).contains(base));
            if (!idOk) {
                return false;
            }
            if (matcher.contains("[")) {
                return spec.contains(matcher.substring(matcher.indexOf('[')));
            }
            return true;
        }

        public boolean setBlock(int x, int y, int z, String spec) { blocks.put(key(x, y, z), spec); return true; }
        public void removeBlock(int x, int y, int z) { blocks.remove(key(x, y, z)); }
        public void spawnItem(double x, double y, double z, String id, int count) { spawned.add(id + " x" + count + " @" + (int) Math.floor(x) + "," + String.format("%.2f", y) + "," + (int) Math.floor(z)); }
        public void runCommand(int x, int y, int z, String command) { commands.add(command + " @" + x + "," + y + "," + z); }
        public double random() { return random; }
    }

    static final class Drop implements ItemEntityHandle {
        final Stack stack;
        final double x, y, z;
        boolean changed;

        Drop(String id, int count, double x, double y, double z) { this.stack = new Stack(id, count); this.x = x; this.y = y; this.z = z; }

        public ItemHandle stack() { return stack; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public void changed() { changed = true; }
    }

    private Level level;
    private Player player;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        EventGroups.clear(ScriptType.SERVER);
        WorldRecipes.set(List.of());
        level = new Level();
        player = new Player();
    }

    private void define(String script) throws IOException {
        Path file = root.resolve(ScriptType.SERVER.directory).resolve("world.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, script);
        ScriptManager.of(ScriptType.SERVER).reload();
        assertEquals(List.of(), ScriptManager.of(ScriptType.SERVER).errors());
    }

    @Test
    void itemOnBlockChangesTheBlockDropsAndUsesTheItem() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemOnBlock('minecraft:stone', 'minecraft:flint').turnInto('minecraft:cobblestone[wet=true]').drop('minecraft:gravel', 2).give('minecraft:stick').damage(3);
            });
            """);
        level.blocks.put("1,64,1", "minecraft:stone");
        player.main = new Stack("minecraft:flint", 5);
        assertTrue(WorldRecipes.click(level, player, 1, 64, 1, false, false));
        assertEquals("minecraft:cobblestone[wet=true]", level.blocks.get("1,64,1"));
        assertEquals(4, player.main.count);
        assertEquals(3, player.main.damage);
        assertEquals(List.of("minecraft:gravel x2 @1,65.05,1"), level.spawned);
        assertEquals(List.of("minecraft:stick x1"), player.given);
    }

    @Test
    void conditionsDecideWhetherARecipeApplies() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemOnBlock('minecraft:dirt', '#c:ingots').sneaking(true).turnInto('minecraft:gold_block');
              event.itemOnBlock('minecraft:sand', { item: 'minecraft:coal', count: 2 }).hand('off').breakBlock().consume(0);
            });
            """);
        level.blocks.put("0,0,0", "minecraft:dirt");
        level.blocks.put("5,0,0", "minecraft:sand");
        player.main = new Stack("minecraft:iron_ingot", 1);
        assertFalse(WorldRecipes.click(level, player, 0, 0, 0, false, false), "needs sneaking");
        player.sneaking = true;
        assertFalse(WorldRecipes.click(level, player, 0, 0, 0, true, false), "a left click is another trigger");
        assertTrue(WorldRecipes.click(level, player, 0, 0, 0, false, false));
        assertEquals("minecraft:gold_block", level.blocks.get("0,0,0"));
        assertEquals(0, player.main.count);

        player.off = new Stack("minecraft:coal", 1);
        assertFalse(WorldRecipes.click(level, player, 5, 0, 0, false, true), "needs two coal");
        player.off = new Stack("minecraft:coal", 2);
        assertFalse(WorldRecipes.click(level, player, 5, 0, 0, false, false), "main hand does not count");
        assertTrue(WorldRecipes.click(level, player, 5, 0, 0, false, true));
        assertFalse(level.blocks.containsKey("5,0,0"));
        assertEquals(2, player.off.count, "consume(0) keeps the item");
    }

    @Test
    void creativePlayersKeepTheirItemsAndChanceGatesTheResults() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemOnBlock('minecraft:stone', 'minecraft:flint').chance(0.5).drop('minecraft:gravel');
            });
            """);
        level.blocks.put("0,0,0", "minecraft:stone");
        player.creative = true;
        player.main = new Stack("minecraft:flint", 3);
        level.random = 0.9;
        assertTrue(WorldRecipes.click(level, player, 0, 0, 0, false, false));
        assertEquals(List.of(), level.spawned);
        assertEquals(3, player.main.count);
        level.random = 0.1;
        WorldRecipes.click(level, player, 0, 0, 0, false, false);
        assertEquals(1, level.spawned.size());
        player.creative = false;
        WorldRecipes.click(level, player, 0, 0, 0, false, false);
        assertEquals(2, player.main.count, "the item is used up even when the chance fails or succeeds");
    }

    @Test
    void blockStatesInTheMatcherAreChecked() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemOnBlock('minecraft:cauldron[level=3]', 'minecraft:sand').turnInto('minecraft:cauldron[level=0]').drop('minecraft:clay_ball');
            });
            """);
        level.blocks.put("0,0,0", "minecraft:cauldron[level=1]");
        player.main = new Stack("minecraft:sand", 1);
        assertFalse(WorldRecipes.click(level, player, 0, 0, 0, false, false));
        level.blocks.put("0,0,0", "minecraft:cauldron[level=3]");
        assertTrue(WorldRecipes.click(level, player, 0, 0, 0, false, false));
        assertEquals("minecraft:cauldron[level=0]", level.blocks.get("0,0,0"));
    }

    @Test
    void droppedItemsInAndOnABlockCombine() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemsInBlock('minecraft:water', ['minecraft:coal x2', { item: '#c:ingots' }]).drop('minecraft:diamond').drop('minecraft:flint', 3);
              event.itemsOnBlock('minecraft:gold_block', ['minecraft:redstone']).turnInto('minecraft:redstone_block');
            });
            """);
        level.blocks.put("3,60,3", "minecraft:water");
        Drop coalA = new Drop("minecraft:coal", 1, 3.4, 60.3, 3.6);
        Drop coalB = new Drop("minecraft:coal", 3, 3.5, 60.2, 3.5);
        Drop iron = new Drop("minecraft:iron_ingot", 1, 3.2, 60.5, 3.2);
        Drop elsewhere = new Drop("minecraft:coal", 9, 9.5, 60.2, 9.5);
        WorldRecipes.items(level, List.of(coalA, coalB, iron, elsewhere));
        assertEquals(List.of("minecraft:diamond x1 @3,60.50,3", "minecraft:flint x3 @3,60.50,3"), level.spawned);
    }

    @Test
    void dropsInWaterUseUpExactlyWhatTheRecipeNeedsOnce() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemsInBlock('minecraft:water', ['minecraft:coal x2', '#c:ingots']).drop('minecraft:diamond');
            });
            """);
        level.blocks.put("3,60,3", "minecraft:water");
        Drop coal = new Drop("minecraft:coal", 3, 3.5, 60.2, 3.5);
        Drop iron = new Drop("minecraft:gold_ingot", 1, 3.2, 60.5, 3.2);
        WorldRecipes.items(level, List.of(coal, iron));
        assertEquals(List.of("minecraft:diamond x1 @3,60.50,3"), level.spawned);
        assertEquals(1, coal.stack.count);
        assertEquals(0, iron.stack.count);
        assertTrue(coal.changed && iron.changed);
        // not enough left for a second craft
        WorldRecipes.items(level, List.of(coal, iron));
        assertEquals(1, level.spawned.size());
    }

    @Test
    void itemsOnTopOfABlockUseTheBlockBelowTheirFeet() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemsOnBlock('minecraft:gold_block', ['minecraft:redstone']).turnInto('minecraft:redstone_block');
            });
            """);
        level.blocks.put("2,64,2", "minecraft:gold_block");
        Drop resting = new Drop("minecraft:redstone", 1, 2.5, 65.0, 2.5);
        WorldRecipes.items(level, List.of(resting));
        assertEquals("minecraft:redstone_block", level.blocks.get("2,64,2"));
        assertEquals(0, resting.stack.count);
    }

    @Test
    void anvilLightningAndRandomTickChangeBlocks() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.anvil('minecraft:stone').turnInto('minecraft:gravel');
              event.lightning('minecraft:sand').turnInto('minecraft:glass');
              event.lightning('minecraft:iron_block').item('minecraft:redstone', 2).command('say zap').breakBlock();
              event.randomTick('minecraft:grass_block').chance(0.5).turnInto('minecraft:dirt');
            });
            """);
        level.blocks.put("0,0,0", "minecraft:stone");
        WorldRecipes.anvil(level, 0, 0, 0);
        assertEquals("minecraft:gravel", level.blocks.get("0,0,0"));

        level.blocks.put("1,5,1", "minecraft:sand");
        WorldRecipes.lightning(level, 1, 6, 1, List.of());
        assertEquals("minecraft:glass", level.blocks.get("1,5,1"));

        level.blocks.put("2,5,2", "minecraft:iron_block");
        WorldRecipes.lightning(level, 2, 6, 2, List.of());
        assertEquals("minecraft:iron_block", level.blocks.get("2,5,2"), "needs the redstone nearby");
        Drop redstone = new Drop("minecraft:redstone", 3, 2.0, 6.0, 2.0);
        WorldRecipes.lightning(level, 2, 6, 2, List.of(redstone));
        assertFalse(level.blocks.containsKey("2,5,2"));
        assertEquals(1, redstone.stack.count);
        assertEquals(List.of("say zap @2,5,2"), level.commands);

        level.blocks.put("4,4,4", "minecraft:grass_block");
        level.random = 0.9;
        WorldRecipes.randomTick(level, 4, 4, 4);
        assertEquals("minecraft:grass_block", level.blocks.get("4,4,4"));
        level.random = 0.1;
        WorldRecipes.randomTick(level, 4, 4, 4);
        assertEquals("minecraft:dirt", level.blocks.get("4,4,4"));
    }

    @Test
    void blockBreakAddsOrReplacesDrops() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.blockBreak('minecraft:stone').tool('#c:ingots').drop('minecraft:flint');
              event.blockBreak('minecraft:coal_ore').replaceDrops().drop('minecraft:diamond', 2).damage(2);
            });
            """);
        level.blocks.put("0,0,0", "minecraft:stone");
        player.main = new Stack("minecraft:wooden_pickaxe", 1);
        assertFalse(WorldRecipes.breakBlock(level, player, 0, 0, 0), "wrong tool");
        player.main = new Stack("minecraft:iron_ingot", 1);
        assertFalse(WorldRecipes.breakBlock(level, player, 0, 0, 0), "extra drops do not cancel the normal break");
        assertEquals(List.of("minecraft:flint x1 @0,0.50,0"), level.spawned);

        level.blocks.put("1,0,1", "minecraft:coal_ore");
        player.main = new Stack("minecraft:iron_pickaxe", 1);
        assertTrue(WorldRecipes.breakBlock(level, player, 1, 0, 1), "replaced drops cancel the normal break");
        assertFalse(level.blocks.containsKey("1,0,1"));
        assertEquals("minecraft:diamond x2 @1,0.50,1", level.spawned.get(1));
        assertEquals(2, player.main.damage);

        player.creative = true;
        level.blocks.put("1,0,1", "minecraft:coal_ore");
        assertFalse(WorldRecipes.breakBlock(level, player, 1, 0, 1));
    }

    @Test
    void leftClickRecipesUseTheLeftClickTrigger() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.leftClickBlock('minecraft:obsidian', 'minecraft:flint_and_steel').turnInto('minecraft:crying_obsidian').consume(0);
            });
            """);
        level.blocks.put("0,0,0", "minecraft:obsidian");
        player.main = new Stack("minecraft:flint_and_steel", 1);
        assertFalse(WorldRecipes.click(level, player, 0, 0, 0, false, false));
        assertTrue(WorldRecipes.click(level, player, 0, 0, 0, true, false));
        assertEquals("minecraft:crying_obsidian", level.blocks.get("0,0,0"));
        assertEquals(1, player.main.count);
    }

    @Test
    void badDefinitionsAreScriptErrors() throws IOException {
        Path file = root.resolve(ScriptType.SERVER.directory).resolve("bad.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            ServerEvents.worldRecipes(event => {
              event.itemsInBlock('minecraft:water', []);
            });
            """);
        ScriptManager.of(ScriptType.SERVER).reload();
        assertTrue(ScriptManager.of(ScriptType.SERVER).errors().toString().contains("needs at least one item"), ScriptManager.of(ScriptType.SERVER).errors().toString());
        assertTrue(WorldRecipes.all().isEmpty() || WorldRecipes.all().size() == 1);
    }

    @Test
    void recipeViewersGetASentencePerInvolvedItem() throws IOException {
        define("""
            ServerEvents.worldRecipes(event => {
              event.itemOnBlock('minecraft:stone', 'minecraft:flint').sneaking(true).turnInto('minecraft:cobblestone').drop('minecraft:gravel', 2).chance(0.5);
              event.anvil('#minecraft:logs').breakBlock().drop('minecraft:stick', 4);
            });
            """);
        Map<String, List<String>> info = WorldRecipes.viewerInformation();
        assertEquals(List.of("In the world: right-click Stone with Flint while sneaking: the block becomes Cobblestone, drops 2 Gravel (50% chance)."), info.get("minecraft:stone"));
        assertTrue(info.containsKey("minecraft:flint"));
        assertTrue(info.containsKey("minecraft:gravel"));
        assertTrue(info.containsKey("minecraft:cobblestone"));
        assertEquals(List.of("In the world: let an anvil fall on #minecraft:logs: the block is removed, drops 4 Stick."), info.get("#minecraft:logs"));
        assertTrue(new com.itmecasey.modjs.client.RecipeViewerEventJS.AddInformation().info().containsKey("minecraft:stone"));
    }
}
