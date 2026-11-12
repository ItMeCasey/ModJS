package com.itmecasey.modjs.worldcraft;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.event.SneakTracker;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelJSTest {
    /** A level made of a map of block ids with a loaded area from 0 to 50 on every axis. */
    static final class Fake implements WorldHandles.LevelHandle {
        final Map<String, String> blocks = new HashMap<>();
        final List<String> destroyed = new ArrayList<>();
        final List<String> grown = new ArrayList<>();

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        private boolean loaded(int x, int y, int z) {
            return x >= 0 && x <= 50 && y >= 0 && y <= 50 && z >= 0 && z <= 50;
        }

        @Override
        public boolean blockMatches(int x, int y, int z, String matcher) {
            String id = blockId(x, y, z);
            if (id == null) {
                return false;
            }
            if (matcher.startsWith("#minecraft:logs")) {
                return id.endsWith("_log");
            }
            return id.equals(matcher);
        }

        @Override
        public String blockId(int x, int y, int z) {
            return loaded(x, y, z) ? blocks.getOrDefault(key(x, y, z), "minecraft:air") : null;
        }

        @Override
        public boolean setBlock(int x, int y, int z, String spec) {
            blocks.put(key(x, y, z), spec);
            return true;
        }

        @Override
        public void removeBlock(int x, int y, int z) {
            blocks.remove(key(x, y, z));
        }

        @Override
        public boolean destroyBlock(int x, int y, int z, boolean drop) {
            String id = blockId(x, y, z);
            if (id == null || id.equals("minecraft:air")) {
                return false;
            }
            destroyed.add(key(x, y, z) + (drop ? " drops" : " nodrops"));
            blocks.remove(key(x, y, z));
            return true;
        }

        @Override
        public boolean bonemeal(int x, int y, int z) {
            boolean crop = "minecraft:wheat".equals(blockId(x, y, z));
            if (crop) {
                grown.add(key(x, y, z));
            }
            return crop;
        }

        @Override
        public String dimension() {
            return "minecraft:overworld";
        }

        @Override
        public Map<String, String> describe(int x, int y, int z) {
            return "minecraft:wheat".equals(blockId(x, y, z)) ? Map.of("age", "3") : Map.of();
        }

        @Override
        public void spawnItem(double x, double y, double z, String itemId, int count) {
        }

        @Override
        public void runCommand(int x, int y, int z, String command) {
        }

        @Override
        public double random() {
            return 0;
        }
    }

    private static Object field(Object scriptObject, String name) {
        return ((NativeObject) scriptObject).get(name, (NativeObject) scriptObject);
    }

    @Test
    void readsABlockWithItsStateAndNullWhenUnloaded() {
        Fake fake = new Fake();
        fake.blocks.put("5,5,5", "minecraft:wheat");
        LevelJS level = new LevelJS(fake);
        NativeObject block = level.getBlock(5, 5, 5);
        assertEquals("minecraft:wheat", field(block, "id"));
        assertEquals("3", field(field(block, "properties"), "age"));
        assertEquals("minecraft:air", level.getBlockId(6, 5, 5));
        assertNull(level.getBlock(99, 5, 5));
        assertFalse(level.isLoaded(99, 5, 5));
        assertTrue(level.isLoaded(1, 1, 1));
        assertEquals("minecraft:overworld", level.getDimension());
    }

    @Test
    void findsBlocksInABoxAndAroundAPosition() {
        Fake fake = new Fake();
        fake.blocks.put("1,1,1", "minecraft:oak_log");
        fake.blocks.put("3,1,1", "minecraft:oak_log");
        fake.blocks.put("20,1,1", "minecraft:birch_log");
        LevelJS level = new LevelJS(fake);
        assertEquals(2, level.findBlocks(0, 0, 0, 5, 5, 5, "#minecraft:logs").getLength());
        assertEquals(1, level.findBlocksAround(1, 1, 1, 1, "minecraft:oak_log").getLength());
        assertEquals(3, level.findBlocksAround(5, 1, 1, 20, "#minecraft:logs").getLength());
        NativeArray one = level.findBlocks(1, 1, 1, 1, 1, 1, "minecraft:oak_log");
        assertEquals(1, ((Number) field(one.get(0), "x")).intValue());
        assertThrows(IllegalArgumentException.class, () -> level.findBlocks(0, 0, 0, 200, 200, 200, "minecraft:stone"));
    }

    @Test
    void floodFillFollowsFacesOrAlsoDiagonalsAndHonorsTheLimit() {
        Fake fake = new Fake();
        for (int y = 1; y <= 4; y++) {
            fake.blocks.put("10," + y + ",10", "minecraft:oak_log");
        }
        fake.blocks.put("11,5,11", "minecraft:oak_log"); // touches the trunk's top only at a corner
        fake.blocks.put("12,1,10", "minecraft:oak_log"); // two blocks away: never connected
        LevelJS level = new LevelJS(fake);
        assertEquals(4, level.connected(10, 1, 10, "minecraft:oak_log", false, 0).getLength());
        assertEquals(5, level.connected(10, 1, 10, "#minecraft:logs", true, 0).getLength());
        assertEquals(2, level.connected(10, 1, 10, "minecraft:oak_log", false, 2).getLength());
        assertEquals(0, level.connected(10, 10, 10, "minecraft:oak_log", false, 0).getLength());
        assertEquals(5, level.connectedSame(10, 4, 10, true, 0).getLength());
        NativeArray first = level.connected(10, 1, 10, "minecraft:oak_log", false, 0);
        assertEquals(1, ((Number) field(first.get(0), "y")).intValue());
    }

    @Test
    void destroyAndBonemealGoThroughTheGame() {
        Fake fake = new Fake();
        fake.blocks.put("2,2,2", "minecraft:wheat");
        fake.blocks.put("3,2,2", "minecraft:stone");
        LevelJS level = new LevelJS(fake);
        assertTrue(level.bonemeal(2, 2, 2));
        assertFalse(level.bonemeal(3, 2, 2));
        assertTrue(level.destroyBlock(3, 2, 2, true));
        assertFalse(level.destroyBlock(3, 2, 2, true));
        assertEquals(List.of("3,2,2 drops"), fake.destroyed);
        assertEquals(List.of("2,2,2"), fake.grown);
    }

    public static final class Marks extends EventJS {
        public final List<String> calls = new ArrayList<>();

        public void mark(String text) {
            calls.add(text);
        }
    }

    @TempDir
    Path root;

    @Test
    void blockEventsReachIdTagAndPlainHandlersOnceEach() throws IOException {
        ScriptManager.setRoot(root);
        EventGroups.clear(ScriptType.SERVER);
        Path file = root.resolve("server_scripts/tags.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            BlockEvents.broken('minecraft:oak_log', e => e.mark('id'));
            BlockEvents.broken('#minecraft:logs', e => e.mark('tag'));
            BlockEvents.broken('#minecraft:leaves', e => e.mark('other tag'));
            BlockEvents.broken(e => e.mark('all'));
            """);
        ScriptManager.of(ScriptType.SERVER).reload();
        assertEquals(List.of(), ScriptManager.of(ScriptType.SERVER).errors());
        assertTrue(EventGroups.BLOCK.hasTagHandlers("broken"));
        assertFalse(EventGroups.BLOCK.hasTagHandlers("placed"));
        Marks event = new Marks();
        EventGroups.BLOCK.post("broken", List.of("minecraft:oak_log", "#minecraft:logs", "#minecraft:overworld_natural_logs"), event);
        assertEquals(Set.of("id", "tag", "all"), Set.copyOf(event.calls));
        assertEquals(3, event.calls.size());
        EventGroups.clear(ScriptType.SERVER);
    }

    @Test
    void sneakStartsAndStopsOncePerChange() {
        UUID player = UUID.randomUUID();
        SneakTracker.clear();
        assertEquals(0, SneakTracker.update(player, false));
        assertEquals(1, SneakTracker.update(player, true));
        assertEquals(0, SneakTracker.update(player, true));
        assertEquals(-1, SneakTracker.update(player, false));
        assertEquals(0, SneakTracker.update(player, false));
    }
}
