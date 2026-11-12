package com.itmecasey.modjs.worldcraft;

import com.itmecasey.modjs.worldcraft.WorldHandles.LevelHandle;

import com.itmecasey.modjs.script.ScriptBlockCalls;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.TopLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What scripts get from {@code event.world} on gameplay events: read the blocks around a position, search an area, flood fill connected
 * blocks, break a block with its drops, and grow a plant like bone meal. Everything is written against {@link LevelHandle}, so one
 * implementation serves every Minecraft build. Block matchers are the ones world recipes use: {@code minecraft:oak_log},
 * {@code #minecraft:logs}, optionally with state values ({@code minecraft:wheat[age=7]}).
 */
public final class LevelJS {
    /** The most positions one search or fill returns, so a careless script cannot freeze the server. */
    public static final int MAX_RESULTS = 4096;
    /** The most positions one box search looks at. */
    public static final int MAX_VOLUME = 262_144;

    private final LevelHandle level;
    private final ScriptType scriptType;
    private final boolean readOnly;

    public LevelJS(LevelHandle level) {
        this(level, ScriptType.SERVER, false);
    }

    /** A view for scripts of one side; a read-only view (the client's world) refuses to break, place or grow blocks. */
    public LevelJS(LevelHandle level, ScriptType scriptType, boolean readOnly) {
        this.level = level;
        this.scriptType = scriptType;
        this.readOnly = readOnly;
    }

    private void writable(String what) {
        if (readOnly) {
            throw org.mozilla.javascript.Context.reportRuntimeError(what + " changes the world, so it needs the server: use it from a server script (client scripts can only read blocks)");
        }
    }

    /** The dimension id, such as {@code minecraft:overworld}. */
    public String getDimension() {
        return level.dimension();
    }

    /** The block at a position as {@code {id, properties}}, or {@code null} when that part of the world is not loaded. */
    public NativeObject getBlock(int x, int y, int z) {
        String id = level.blockId(x, y, z);
        if (id == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("properties", ScriptBlockCalls.toObject(level.describe(x, y, z), scope()));
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        return ScriptBlockCalls.toObject(out, scope());
    }

    /** The block id at a position, or {@code null} when unloaded. */
    public String getBlockId(int x, int y, int z) {
        return level.blockId(x, y, z);
    }

    /** Whether the block matches an id, a tag ({@code #minecraft:logs}) or either with state values in brackets. */
    public boolean matches(int x, int y, int z, String matcher) {
        return level.blockMatches(x, y, z, matcher);
    }

    /** True when the chunk at the position is loaded (reads of unloaded chunks answer {@code null} or {@code false}). */
    public boolean isLoaded(int x, int y, int z) {
        return level.blockId(x, y, z) != null;
    }

    /**
     * Positions in the box between two corners whose block matches. Results come back as {@code {x, y, z}} objects, nearest the first corner
     * first, at most {@value #MAX_RESULTS}; a box over {@value #MAX_VOLUME} blocks is an error.
     */
    public NativeArray findBlocks(int x1, int y1, int z1, int x2, int y2, int z2, String matcher) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException("findBlocks searched " + volume + " blocks; the limit is " + MAX_VOLUME + ". Use a smaller box.");
        }
        List<Object> found = new ArrayList<>();
        for (int y = minY; y <= maxY && found.size() < MAX_RESULTS; y++) {
            for (int x = minX; x <= maxX && found.size() < MAX_RESULTS; x++) {
                for (int z = minZ; z <= maxZ && found.size() < MAX_RESULTS; z++) {
                    if (level.blockMatches(x, y, z, matcher)) {
                        found.add(position(x, y, z));
                    }
                }
            }
        }
        return array(found);
    }

    /** Blocks within {@code radius} of a position (a cube) that match, as {@link #findBlocks} returns them. */
    public NativeArray findBlocksAround(int x, int y, int z, int radius, String matcher) {
        int r = Math.max(0, radius);
        return findBlocks(x - r, y - r, z - r, x + r, y + r, z + r, matcher);
    }

    /**
     * The blocks connected to a start position through shared faces (6 neighbors) that match. The start block itself is included when it
     * matches. {@code diagonal} adds edge and corner neighbors (26 neighbors), which is how veins and trees usually connect. {@code limit}
     * caps the result (at most {@value #MAX_RESULTS}); nearest blocks come first.
     */
    public NativeArray connected(int x, int y, int z, String matcher, boolean diagonal, int limit) {
        int cap = Math.max(1, Math.min(limit <= 0 ? MAX_RESULTS : limit, MAX_RESULTS));
        List<Object> found = new ArrayList<>();
        if (!level.blockMatches(x, y, z, matcher)) {
            return array(found);
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {x, y, z});
        seen.add(pack(x, y, z));
        while (!queue.isEmpty() && found.size() < cap) {
            int[] at = queue.poll();
            found.add(position(at[0], at[1], at[2]));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx == 0 && dy == 0 && dz == 0) || (!diagonal && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1)) {
                            continue;
                        }
                        int nx = at[0] + dx;
                        int ny = at[1] + dy;
                        int nz = at[2] + dz;
                        if (seen.add(pack(nx, ny, nz)) && level.blockMatches(nx, ny, nz, matcher)) {
                            queue.add(new int[] {nx, ny, nz});
                        }
                    }
                }
            }
        }
        return array(found);
    }

    /** The blocks connected to a start position through shared faces that are the same block as the start (a vein or a tree trunk). */
    public NativeArray connectedSame(int x, int y, int z, boolean diagonal, int limit) {
        String id = level.blockId(x, y, z);
        return id == null ? array(new ArrayList<>()) : connected(x, y, z, id, diagonal, limit);
    }

    /**
     * Breaks the block the way the world does: with its normal drops when {@code drop} is true (loot table, no tool), without when false.
     * Returns false when the position is not loaded or already air.
     */
    public boolean destroyBlock(int x, int y, int z, boolean drop) {
        writable("destroyBlock");
        return level.destroyBlock(x, y, z, drop);
    }

    /** Replaces the block with another (id and optional state values in brackets); false for an unknown block. */
    public boolean setBlock(int x, int y, int z, String block) {
        writable("setBlock");
        return level.setBlock(x, y, z, block);
    }

    /**
     * Applies the same growth as a bone meal item to the block (crops, saplings, grass, flowers, mushrooms, kelp, moss and so on). Returns
     * whether anything grew. No item is used up.
     */
    public boolean bonemeal(int x, int y, int z) {
        writable("bonemeal");
        return level.bonemeal(x, y, z);
    }

    private NativeObject position(int x, int y, int z) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", x);
        out.put("y", y);
        out.put("z", z);
        return ScriptBlockCalls.toObject(out, scope());
    }

    private Scriptable scope() {
        try {
            return ScriptManager.of(scriptType).scope();
        } catch (RuntimeException e) {
            return null; // no script engine (unit tests): bare objects are enough to read fields
        }
    }

    private NativeArray array(List<Object> items) {
        NativeArray array = new NativeArray(items.toArray());
        Scriptable scope = scope();
        if (scope != null) {
            ScriptRuntime.setBuiltinProtoAndParent(array, scope, TopLevel.Builtins.Array);
        }
        return array;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
