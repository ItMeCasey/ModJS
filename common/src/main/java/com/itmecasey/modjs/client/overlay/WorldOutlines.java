package com.itmecasey.modjs.client.overlay;

import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.worldcraft.Positions;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Outlines and tints around whole blocks, for any block in the world: {@code Overlay.outline('veins', positions, { color: '#ffd24a' })}.
 * This class keeps the sets and decides every frame which boxes are drawn; the platform module calls {@link #draw} from its
 * world render hook with a {@link Canvas}.
 */
public final class WorldOutlines {
    /** The most blocks one outline set holds; more positions are cut off. */
    public static final int MAX_BLOCKS = 4096;
    /** The most boxes drawn in one frame over all sets. */
    public static final int MAX_DRAWN = 8192;
    /** Boxes farther from the camera than this many blocks are skipped. */
    public static volatile double maxDistance = 128.0;

    private static final Map<String, Outline> OUTLINES = new LinkedHashMap<>();

    private WorldOutlines() {
    }

    /** What the platform draws with: one box per call, in absolute world coordinates (the adapter subtracts the camera). */
    public interface Canvas {
        /** A box with line edges ({@code lineArgb}, skipped when its alpha is 0), an optional translucent fill and a line width in pixels. */
        void box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int lineArgb, float width, int fillArgb, boolean throughWalls);

        /** One straight line in absolute world coordinates (an edge of a merged outline). */
        void segment(double x0, double y0, double z0, double x1, double y1, double z1, int lineArgb, float width, boolean throughWalls);
    }

    private static int counter;

    /** An id for an outline that scripts created without one. */
    static synchronized String nextId() {
        return "outline#" + (++counter);
    }

    public static synchronized Outline put(String id, Object positions, Object options) {
        if (id == null || id.isEmpty()) {
            throw Context.reportRuntimeError("Overlay.outline: every outline needs an id, for example Overlay.outline('veins', positions, {...})");
        }
        Outline outline = OUTLINES.computeIfAbsent(id, Outline::new);
        outline.apply(options);
        outline.setPositions(positions);
        return outline;
    }

    public static synchronized Outline get(String id) {
        return OUTLINES.get(id);
    }

    public static synchronized boolean remove(String id) {
        return OUTLINES.remove(id) != null;
    }

    public static synchronized void clear() {
        OUTLINES.clear();
    }

    public static synchronized boolean any() {
        return !OUTLINES.isEmpty();
    }

    public static synchronized int count() {
        return OUTLINES.size();
    }

    /** Draws every visible outline set. Called by the platform's world render hook, once per frame. */
    public static void draw(Canvas canvas, double cx, double cy, double cz) {
        List<Outline> sets;
        synchronized (WorldOutlines.class) {
            sets = new ArrayList<>(OUTLINES.values());
        }
        double limit = maxDistance * maxDistance;
        int drawn = 0;
        // blocks seen through walls are drawn first by the adapters, so the order of sets only matters inside one pass
        for (Outline set : sets) {
            if (!set.visible || set.alpha <= 0.004) {
                continue;
            }
            int line = Colors.withAlpha(Colors.parse(set.color, 0xFFFFFFFF), set.alpha);
            int fill = set.fill == null ? 0 : Colors.withAlpha(Colors.parse(set.fill, 0x55FFFFFF), set.alpha);
            float width = (float) Math.max(0.5, set.width);
            for (long key : set.snapshot()) {
                if (drawn >= MAX_DRAWN) {
                    return;
                }
                int x = WorldOverlay.keyX(key);
                int y = WorldOverlay.keyY(key);
                int z = WorldOverlay.keyZ(key);
                double dx = x + 0.5 - cx;
                double dy = y + 0.5 - cy;
                double dz = z + 0.5 - cz;
                if (dx * dx + dy * dy + dz * dz > limit) {
                    continue;
                }
                // a connected set draws only the tint per block; its edges come from the merged silhouette below
                canvas.box(x - 0.002, y - 0.002, z - 0.002, x + 1.002, y + 1.002, z + 1.002, set.connected ? 0 : line, width, fill, set.throughWalls);
                drawn++;
            }
            if (set.connected && (line >>> 24) != 0) {
                for (BlockEdges.Edge e : set.silhouette()) {
                    if (drawn >= MAX_DRAWN) {
                        return;
                    }
                    double mx = (e.x0() + e.x1()) / 2.0 - cx;
                    double my = (e.y0() + e.y1()) / 2.0 - cy;
                    double mz = (e.z0() + e.z1()) / 2.0 - cz;
                    if (mx * mx + my * my + mz * mz > limit) {
                        continue;
                    }
                    canvas.segment(e.x0(), e.y0(), e.z0(), e.x1(), e.y1(), e.z1(), line, width, set.throughWalls);
                    drawn++;
                }
            }
        }
    }

    /** One set of outlined blocks: the object {@code Overlay.outline(...)} returns. */
    public static final class Outline {
        public final String id;
        /** Edge color ({@code '#ffd24a'}, {@code 0xAARRGGBB}); an alpha of 0 draws no edges. */
        public Object color = 0xFFFFFFFF;
        /** Edge width in pixels (honored on 26.x; the older versions draw the game's standard line width). */
        public double width = 2.0;
        /** Draw over blocks in front (built for 1.20.1 and 1.21.1; the 26.x builds draw with the game's depth bias instead). */
        public boolean throughWalls;
        /** True: one merged outline around the whole group (only the outer edges, none between touching blocks); false: every block on its own. */
        public boolean connected;
        /** Tint color for the faces of every block, or null for none. */
        public Object fill;
        public boolean visible = true;
        /** 0 to 1; multiplies the alpha of every color of the set. */
        public double alpha = 1.0;

        private Set<Long> positions = new LinkedHashSet<>();
        private List<BlockEdges.Edge> edges;

        Outline(String id) {
            this.id = id;
        }

        /** The merged outer edges of the current blocks (computed once per change of the blocks). */
        private synchronized List<BlockEdges.Edge> silhouette() {
            if (edges == null) {
                List<int[]> blocks = new ArrayList<>();
                for (long key : positions) {
                    blocks.add(new int[] {WorldOverlay.keyX(key), WorldOverlay.keyY(key), WorldOverlay.keyZ(key)});
                }
                edges = BlockEdges.silhouette(blocks);
            }
            return edges;
        }

        private synchronized long[] snapshot() {
            long[] keys = new long[positions.size()];
            int i = 0;
            for (long key : positions) {
                keys[i++] = key;
            }
            return keys;
        }

        /** Number of outlined blocks. */
        public synchronized int getCount() {
            return positions.size();
        }

        /** Replaces the outlined blocks with {@code [{x, y, z}, ...]} or {@code [[x, y, z], ...]}, the shape that {@code world.connected} returns. */
        public void setPositions(Object list) {
            Set<Long> next = parse(list);
            synchronized (this) {
                positions = next;
                edges = null;
            }
        }

        /** Adds blocks to the set. */
        public void add(Object list) {
            Set<Long> more = parse(list);
            synchronized (this) {
                for (long key : more) {
                    if (positions.size() >= MAX_BLOCKS) {
                        break;
                    }
                    positions.add(key);
                }
                edges = null;
            }
        }

        /** Removes every outlined block but keeps the set. */
        public synchronized void clear() {
            positions.clear();
            edges = null;
        }

        /** Removes this set from the world. */
        public void remove() {
            WorldOutlines.remove(id);
        }

        void apply(Object options) {
            if (options == null) {
                return;
            }
            Object c = JsonUtil.property(options, "color");
            if (c != null) {
                color = JsonUtil.unwrap(c);
            }
            Object w = JsonUtil.unwrap(JsonUtil.property(options, "width"));
            if (w instanceof Number n) {
                width = n.doubleValue();
            }
            Object t = JsonUtil.unwrap(JsonUtil.property(options, "throughWalls"));
            if (t instanceof Boolean b) {
                throughWalls = b;
            }
            Object cn = JsonUtil.unwrap(JsonUtil.property(options, "connected"));
            if (cn instanceof Boolean b) {
                connected = b;
            }
            Object f = JsonUtil.property(options, "fill");
            if (f != null) {
                fill = JsonUtil.unwrap(f);
            }
            Object a = JsonUtil.unwrap(JsonUtil.property(options, "alpha"));
            if (a instanceof Number n) {
                alpha = n.doubleValue();
            }
            Object v = JsonUtil.unwrap(JsonUtil.property(options, "visible"));
            if (v instanceof Boolean b) {
                visible = b;
            }
        }
    }

    /** Reads a list of positions into packed keys (at most {@link #MAX_BLOCKS}). */
    static Set<Long> parse(Object list) {
        Set<Long> keys = new LinkedHashSet<>();
        for (int[] p : Positions.parse(list, MAX_BLOCKS, "Overlay.outline")) {
            keys.add(WorldOverlay.key(p[0], p[1], p[2]));
        }
        return keys;
    }
}
