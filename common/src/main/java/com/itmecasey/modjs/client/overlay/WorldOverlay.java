package com.itmecasey.modjs.client.overlay;

import com.itmecasey.modjs.block.ScriptBlockData;
import com.itmecasey.modjs.script.ScriptBlockCalls;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Overlays attached to block positions in the world ({@code Overlay.at(x, y, z)}). This class holds the elements, decides every
 * frame which ones are drawn (distance, loaded chunk, facing, element cap, nearest first) and runs the block templates; the
 * platform module calls {@link #draw} from its world render hook with a {@link WorldCanvas}.
 */
public final class WorldOverlay {
    /** Global limits, set from scripts with {@code Overlay.world.maxDistance = 16}. */
    public static volatile double maxDistance = 24.0;
    public static volatile int maxElements = 128;

    private static final Map<Long, At> POSITIONS = new LinkedHashMap<>();
    private static final Map<String, Function> TEMPLATES = new LinkedHashMap<>();
    private static final Map<Long, Tracked> TRACKED = new LinkedHashMap<>();
    private static final long TEMPLATE_REFRESH_MS = 1000;
    private static final long SCAN_GAP_MS = 50;
    private static long lastScanMs = Long.MIN_VALUE;

    private WorldOverlay() {
    }

    // -- keys ---------------------------------------------------------------------------------------------------------

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public static int keyX(long key) {
        return (int) (key >> 38);
    }

    public static int keyY(long key) {
        return (int) ((key & 0xFFFL) << 52 >> 52);
    }

    public static int keyZ(long key) {
        return (int) (((key >> 12) & 0x3FFFFFFL) << 38 >> 38);
    }

    // -- elements -----------------------------------------------------------------------------------------------------

    public static synchronized At at(int x, int y, int z) {
        return POSITIONS.computeIfAbsent(key(x, y, z), k -> new At(x, y, z));
    }

    public static synchronized void remove(int x, int y, int z) {
        POSITIONS.remove(key(x, y, z));
    }

    /** Removes every element and every template (client scripts do this by themselves whenever they reload). */
    public static synchronized void clear() {
        POSITIONS.clear();
        TEMPLATES.clear();
        cachedX = Double.NaN;
        lastScanMs = Long.MIN_VALUE;
        TRACKED.values().forEach(t -> t.ran = false);
    }

    public static synchronized int positionCount() {
        return POSITIONS.size();
    }

    // -- synced block data --------------------------------------------------------------------------------------------

    private static final class Tracked {
        final String blockId;
        final int x;
        final int y;
        final int z;
        String dataJson;
        ScriptBlockData data;
        boolean ran;
        String ranWith;
        long lastRun;

        Tracked(String blockId, int x, int y, int z) {
            this.blockId = blockId;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** The client's copy of a scripted block entity was created or updated: remember its block id and synced data. */
    public static synchronized void blockUpdated(String blockId, int x, int y, int z, String dataJson) {
        Tracked tracked = TRACKED.computeIfAbsent(key(x, y, z), k -> new Tracked(blockId, x, y, z));
        tracked.dataJson = dataJson == null ? "" : dataJson;
        tracked.data = ScriptBlockData.fromJson(tracked.dataJson);
    }

    /** The client's copy of a scripted block entity went away. */
    public static synchronized void blockRemoved(int x, int y, int z) {
        TRACKED.remove(key(x, y, z));
        POSITIONS.remove(key(x, y, z));
    }

    /** Forgets every tracked block (the client left a world). */
    public static synchronized void forgetBlocks() {
        TRACKED.clear();
        POSITIONS.clear();
    }

    /** The synced data of the scripted block at a position, or an empty map. */
    public static synchronized Map<String, Object> data(int x, int y, int z) {
        Tracked tracked = TRACKED.get(key(x, y, z));
        return tracked == null || tracked.data == null ? Map.of() : tracked.data.asMap();
    }

    // -- templates ----------------------------------------------------------------------------------------------------

    public static synchronized void forBlock(String blockId, Function fn) {
        TEMPLATES.put(blockId.contains(":") ? blockId : "modjs:" + blockId, fn);
    }

    /**
     * Runs the template of every tracked block near the camera whose synced data changed since its last run (at most once per
     * second per block), or that has not run yet. The handle is cleared first, so a template only describes the current state.
     */
    public static void refreshTemplates(double cx, double cy, double cz, long nowMs) {
        List<Tracked> due = new ArrayList<>();
        synchronized (WorldOverlay.class) {
            if (TEMPLATES.isEmpty() || (lastScanMs != Long.MIN_VALUE && nowMs >= lastScanMs && nowMs - lastScanMs < SCAN_GAP_MS)) {
                return;
            }
            lastScanMs = nowMs;
            double limit = maxDistance * maxDistance;
            for (Tracked tracked : TRACKED.values()) {
                if (!TEMPLATES.containsKey(tracked.blockId) || distanceSquared(tracked.x + 0.5, tracked.y + 0.5, tracked.z + 0.5, cx, cy, cz) > limit) {
                    continue;
                }
                boolean changed = tracked.ran && !tracked.dataJson.equals(tracked.ranWith);
                if (!tracked.ran || (changed && nowMs - tracked.lastRun >= TEMPLATE_REFRESH_MS)) {
                    due.add(tracked);
                }
            }
        }
        for (Tracked tracked : due) {
            Function fn;
            At at;
            Map<String, Object> data;
            synchronized (WorldOverlay.class) {
                fn = TEMPLATES.get(tracked.blockId);
                at = at(tracked.x, tracked.y, tracked.z);
                at.clear();
                data = tracked.data == null ? Map.of() : tracked.data.asMap();
                tracked.ran = true;
                tracked.ranWith = tracked.dataJson;
                tracked.lastRun = nowMs;
            }
            Map<String, Object> pos = new LinkedHashMap<>();
            pos.put("x", tracked.x);
            pos.put("y", tracked.y);
            pos.put("z", tracked.z);
            ScriptBlockCalls.invoke(ScriptType.CLIENT, "Overlay.forBlock(" + tracked.blockId + ")", fn, at, data, pos);
        }
    }

    // -- per frame ----------------------------------------------------------------------------------------------------

    /** One element that will be drawn this frame, with its absolute origin. */
    public record Draw(long position, WorldElement element, double x, double y, double z, String face) {
    }

    /** Which elements are drawn this frame: loaded, near enough, facing the camera, nearest positions first, capped. */
    public static synchronized List<Draw> collect(double cx, double cy, double cz, LongPredicate loaded) {
        List<At> ats = new ArrayList<>(POSITIONS.values());
        ats.sort((a, b) -> Double.compare(distanceSquared(a.x + 0.5, a.y + 0.5, a.z + 0.5, cx, cy, cz),
            distanceSquared(b.x + 0.5, b.y + 0.5, b.z + 0.5, cx, cy, cz)));
        List<Draw> draws = new ArrayList<>();
        long now = Overlay.now();
        int cap = Math.max(0, maxElements);
        for (At at : ats) {
            if (draws.size() >= cap) {
                break;
            }
            if (!loaded.test(key(at.x, at.y, at.z))) {
                continue;
            }
            List<WorldElement> elements = new ArrayList<>(at.elements.values());
            elements.sort((a, b) -> Integer.compare(a.layer, b.layer));
            for (WorldElement element : elements) {
                element.tick(now);
                if (!element.visible || element.alpha <= 0.004 || draws.size() >= cap) {
                    continue;
                }
                double[] o = origin(at, element);
                double limit = element.distance > 0 ? element.distance : maxDistance;
                if (distanceSquared(o[0], o[1], o[2], cx, cy, cz) > limit * limit) {
                    continue;
                }
                if (!facesCamera(element.face, at, o, cx, cy, cz)) {
                    continue;
                }
                draws.add(new Draw(key(at.x, at.y, at.z), element, o[0], o[1], o[2], element.face));
            }
        }
        return draws;
    }

    /** Draws what {@link #collect} chose. Called by the platform's world render hook, once per frame. */
    public static void draw(WorldCanvas canvas, double cx, double cy, double cz, LongPredicate loaded) {
        for (Draw draw : collect(cx, cy, cz, loaded)) {
            canvas.push(draw.x(), draw.y(), draw.z(), draw.face());
            try {
                draw.element().drawWorld(canvas, draw.element().alpha);
            } finally {
                canvas.pop();
            }
        }
    }

    // The cache lets loaders without a level-wide render hook (Forge 26.x draws from block entity renderers, one position at a
    // time) share one collect() per frame: it holds while the camera has not moved and the last collect was under 3 ms ago.
    private static List<Draw> cached = List.of();
    private static double cachedX = Double.NaN;
    private static double cachedY;
    private static double cachedZ;
    private static long cachedAt;

    private static synchronized List<Draw> collectCached(double cx, double cy, double cz, LongPredicate loaded) {
        long nanos = System.nanoTime();
        if (cx != cachedX || cy != cachedY || cz != cachedZ || nanos - cachedAt > 3_000_000L) {
            cached = collect(cx, cy, cz, loaded);
            cachedX = cx;
            cachedY = cy;
            cachedZ = cz;
            cachedAt = nanos;
        }
        return cached;
    }

    /** Draws only the elements of one block position (for a block entity renderer), with the same culling and cap as {@link #draw}. */
    public static void drawAt(WorldCanvas canvas, int bx, int by, int bz, double cx, double cy, double cz, LongPredicate loaded) {
        long position = key(bx, by, bz);
        for (Draw draw : collectCached(cx, cy, cz, loaded)) {
            if (draw.position() != position) {
                continue;
            }
            canvas.push(draw.x(), draw.y(), draw.z(), draw.face());
            try {
                draw.element().drawWorld(canvas, draw.element().alpha);
            } finally {
                canvas.pop();
            }
        }
    }

    public static boolean any() {
        synchronized (WorldOverlay.class) {
            return !POSITIONS.isEmpty() || !TEMPLATES.isEmpty();
        }
    }

    // -- geometry -----------------------------------------------------------------------------------------------------

    private static double distanceSquared(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Outward normal of a face name, or null for billboards. */
    static double[] normal(String face) {
        return switch (face) {
            case "up" -> new double[] {0, 1, 0};
            case "down" -> new double[] {0, -1, 0};
            case "north" -> new double[] {0, 0, -1};
            case "south" -> new double[] {0, 0, 1};
            case "east" -> new double[] {1, 0, 0};
            case "west" -> new double[] {-1, 0, 0};
            default -> null;
        };
    }

    /** The right and up directions of a flat element on a face (north is up on the top face). */
    private static double[][] axes(String face) {
        return switch (face) {
            case "up" -> new double[][] {{1, 0, 0}, {0, 0, -1}};
            case "down" -> new double[][] {{1, 0, 0}, {0, 0, 1}};
            case "north" -> new double[][] {{-1, 0, 0}, {0, 1, 0}};
            case "south" -> new double[][] {{1, 0, 0}, {0, 1, 0}};
            case "east" -> new double[][] {{0, 0, -1}, {0, 1, 0}};
            case "west" -> new double[][] {{0, 0, 1}, {0, 1, 0}};
            default -> null;
        };
    }

    static double[] origin(At at, WorldElement element) {
        double[] normal = normal(element.face);
        if (normal == null) {
            return new double[] {at.x + 0.5 + element.x, at.y + 1.0 + element.y, at.z + 0.5 + element.z};
        }
        double[][] axes = axes(element.face);
        double[] right = axes[0];
        double[] up = axes[1];
        // element.z pushes the element away from the face, element.x / y slide it along the face
        double out = 0.505 + element.z;
        return new double[] {
            at.x + 0.5 + normal[0] * out + right[0] * element.x + up[0] * element.y,
            at.y + 0.5 + normal[1] * out + right[1] * element.x + up[1] * element.y,
            at.z + 0.5 + normal[2] * out + right[2] * element.x + up[2] * element.y};
    }

    private static boolean facesCamera(String face, At at, double[] origin, double cx, double cy, double cz) {
        double[] normal = normal(face);
        if (normal == null) {
            return true;
        }
        return (cx - origin[0]) * normal[0] + (cy - origin[1]) * normal[1] + (cz - origin[2]) * normal[2] > 0;
    }

    // -- script handle ------------------------------------------------------------------------------------------------

    /** The overlay of one block position: {@code Overlay.at(x, y, z)}. */
    public static final class At {
        private final int x;
        private final int y;
        private final int z;
        private final Map<String, WorldElement> elements = new LinkedHashMap<>();

        At(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        private static String checkId(String id) {
            if (id == null || id.isEmpty()) {
                throw Context.reportRuntimeError("Overlay.at: every element needs an id, for example at.text('title', {...})");
            }
            return id;
        }

        private <T extends WorldElement> T put(T element, Object options) {
            element.apply(options);
            synchronized (WorldOverlay.class) {
                elements.put(element.id, element);
            }
            return element;
        }

        public WorldElement.Text text(String id, Object options) {
            return put(new WorldElement.Text(checkId(id)), options);
        }

        public WorldElement.Item item(String id, Object options) {
            return put(new WorldElement.Item(checkId(id)), options);
        }

        public WorldElement.Bar bar(String id, Object options) {
            return put(new WorldElement.Bar(checkId(id)), options);
        }

        public WorldElement.Texture texture(String id, Object options) {
            return put(new WorldElement.Texture(checkId(id)), options);
        }

        public WorldElement get(String id) {
            synchronized (WorldOverlay.class) {
                return elements.get(id);
            }
        }

        public boolean has(String id) {
            return get(id) != null;
        }

        /** Removes one element by id. */
        public void remove(String id) {
            synchronized (WorldOverlay.class) {
                elements.remove(id);
            }
        }

        /** Removes every element of this position. */
        public void clear() {
            synchronized (WorldOverlay.class) {
                elements.clear();
            }
        }

        /** Number of elements. */
        public int getSize() {
            synchronized (WorldOverlay.class) {
                return elements.size();
            }
        }

        /** The synced data of the scripted block here (an object; empty when the block has none). */
        public Object getData() {
            return ScriptBlockCalls.toClientObject(WorldOverlay.data(x, y, z));
        }
    }

    /** The {@code Overlay.world} object: global limits. */
    public static final class Limits {
        public double getMaxDistance() {
            return maxDistance;
        }

        public void setMaxDistance(double value) {
            maxDistance = Math.max(1.0, value);
        }

        public int getMaxElements() {
            return maxElements;
        }

        public void setMaxElements(int value) {
            maxElements = Math.max(0, value);
        }

        /** Number of block positions that have elements. */
        public int getPositions() {
            return positionCount();
        }
    }
}
