package com.itmecasey.modjs.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.format.DataFolders;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.tags.TagSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Trade changes as data pack files, for the 26.x builds where villager trades are data: each trade is a {@code villager_trade} file, and
 * the pool of a level is the tag {@code <profession>/level_N} (for the wandering trader {@code wandering_trader/common}, {@code uncommon},
 * {@code buying}) that the profession's trade set reads. A level that scripts changed gets a tag that replaces the original; its list starts
 * from the entries of the original tag (read from the game and mod jars), so removing and adding keep everything else.
 */
public final class TradeData {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private TradeData() {
    }

    /** One tag being rebuilt: its entries in order. */
    private static final class Pool {
        final List<String> entries = new ArrayList<>();
    }

    /** Reads the file of a trade ({@code namespace:path}) from the game and mod jars, or null. */
    static JsonObject vanillaTrade(String id) {
        int colon = id.indexOf(':');
        String resource = "data/" + id.substring(0, colon) + "/villager_trade/" + id.substring(colon + 1) + ".json";
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), TradeData.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in != null) {
                    JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not read trade {}", id, e);
            }
        }
        return null;
    }

    /** A trade file as the view filters see it. */
    static Map<String, Object> view(JsonObject trade, String profession, int level, String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profession", profession);
        out.put("level", level);
        out.put("id", id);
        out.put("give", itemView(trade.has("gives") ? trade.getAsJsonObject("gives") : new JsonObject()));
        List<Object> wants = new ArrayList<>();
        if (trade.has("wants")) {
            wants.add(itemView(trade.getAsJsonObject("wants")));
        }
        if (trade.has("additional_wants")) {
            wants.add(itemView(trade.getAsJsonObject("additional_wants")));
        }
        out.put("want", wants);
        out.put("maxUses", trade.has("max_uses") ? trade.get("max_uses").getAsInt() : 4);
        out.put("xp", trade.has("xp") ? trade.get("xp").getAsInt() : 1);
        return out;
    }

    private static Map<String, Object> itemView(JsonObject item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.has("id") ? item.get("id").getAsString() : "minecraft:air");
        out.put("count", item.has("count") ? item.get("count").getAsInt() : 1);
        return out;
    }

    private static String namespace(String id) {
        return id.substring(0, id.indexOf(':'));
    }

    private static String path(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    /** Writes the trade files and tags for every request scripts made. Does nothing when there are none. */
    public static void writeTo(GeneratedData data) {
        writeTo(data, TradeOps.snapshot(), TradeData::originalPool);
    }

    /** The entries of the original tag of a level, from the jars. */
    private static List<String> originalPool(String profession, int level) {
        List<String> out = new ArrayList<>();
        for (JsonElement entry : TagSources.mergedValues("villager_trade", namespace(profession), path(profession) + "/" + TradeOps.levelName(profession, level))) {
            out.add(TagSources.entryId(entry));
        }
        return out;
    }

    /** The same with the original pools given, so tests do not need the game jars. */
    static void writeTo(GeneratedData data, List<TradeOps.Op> ops, java.util.function.BiFunction<String, Integer, List<String>> original) {
        if (ops.isEmpty()) {
            return;
        }
        Map<String, Pool> pools = new LinkedHashMap<>();
        Map<String, JsonObject> generated = new LinkedHashMap<>();
        Map<String, Integer> counters = new LinkedHashMap<>();
        Function<String, Pool> pool = key -> pools.computeIfAbsent(key, k -> {
            Pool p = new Pool();
            int slash = k.lastIndexOf('#');
            String profession = k.substring(0, slash);
            int level = Integer.parseInt(k.substring(slash + 1));
            p.entries.addAll(original.apply(profession, level));
            return p;
        });
        for (TradeOps.Op op : ops) {
            int from = op.level() == 0 ? 1 : op.level();
            int to = op.level() == 0 ? TradeOps.maxLevel(op.profession()) : op.level();
            for (int level = from; level <= to; level++) {
                Pool p = pool.apply(op.profession() + "#" + level);
                switch (op.kind()) {
                    case CLEAR -> p.entries.clear();
                    case REPLACE -> {
                        p.entries.clear();
                        addAll(p, op, level, generated, counters);
                    }
                    case ADD -> addAll(p, op, level, generated, counters);
                    case REMOVE -> {
                        final int levelNow = level;
                        p.entries.removeIf(entry -> {
                            if (entry.startsWith("#")) {
                                return false;
                            }
                            JsonObject trade = generated.containsKey(entry) ? generated.get(entry) : vanillaTrade(entry);
                            return trade != null && op.filter().matches(view(trade, op.profession(), levelNow, entry));
                        });
                    }
                }
            }
        }
        generated.forEach((id, json) -> data.putJson(namespace(id), "villager_trade/" + path(id), json));
        String folder = DataFolders.tags("villager_trade");
        pools.forEach((key, p) -> {
            int hash = key.lastIndexOf('#');
            String profession = key.substring(0, hash);
            int level = Integer.parseInt(key.substring(hash + 1));
            JsonObject tag = new JsonObject();
            tag.addProperty("replace", true);
            JsonArray values = new JsonArray();
            new LinkedHashSet<>(p.entries).forEach(values::add);
            tag.add("values", values);
            data.putJson(namespace(profession), folder + "/" + path(profession) + "/" + TradeOps.levelName(profession, level), tag);
        });
    }

    private static void addAll(Pool pool, TradeOps.Op op, int level, Map<String, JsonObject> generated, Map<String, Integer> counters) {
        for (TradeSpec spec : op.trades()) {
            String base = "modjs:villagers/" + namespace(op.profession()) + "/" + path(op.profession()) + "/" + TradeOps.levelName(op.profession(), level);
            int n = counters.merge(base, 1, Integer::sum);
            String id = base + "/" + n;
            generated.put(id, spec.toJson());
            pool.entries.add(id);
        }
    }
}
