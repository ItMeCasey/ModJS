package com.itmecasey.modjs.villager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Function;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** One trade a script describes: costs, result, limits and conditions. Checked when it is parsed, so mistakes name the field. */
public final class TradeSpec {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public final TradeItem give;
    public final TradeItem want;
    public final TradeItem want2;
    public final int maxUses;
    public final int xp;
    public final float priceMultiplier;
    public final int demand;
    public final List<String> biomes;
    public final List<String> villagerTypes;
    /** A script function {@code (trade, villager) => boolean}; only the 1.20.1 and 1.21.1 builds can run it (26.x trades are data). */
    public final Function predicate;

    TradeSpec(TradeItem give, TradeItem want, TradeItem want2, int maxUses, int xp, float priceMultiplier, int demand, List<String> biomes, List<String> villagerTypes,
        Function predicate) {
        this.give = give;
        this.want = want;
        this.want2 = want2;
        this.maxUses = maxUses;
        this.xp = xp;
        this.priceMultiplier = priceMultiplier;
        this.demand = demand;
        this.biomes = List.copyOf(biomes);
        this.villagerTypes = List.copyOf(villagerTypes);
        this.predicate = predicate;
    }

    /**
     * Reads a trade object: {@code give} (the result), {@code want} (a cost, or a list of one or two costs), and optionally {@code maxUses}
     * (default 12), {@code xp} (1), {@code priceMultiplier} (0.05), {@code demand} (0), {@code biome} / {@code villagerType} (an id or a list),
     * and {@code when} (a function).
     */
    public static TradeSpec parse(Object raw) {
        Object value = JsonUtil.unwrap(raw);
        Object give = value == null ? null : JsonUtil.property(value, "give");
        if (give == null && value != null) {
            give = JsonUtil.property(value, "gives");
        }
        if (give == null) {
            throw new IllegalArgumentException("a trade needs 'give' (the result) and 'want' (the cost): { give: 'minecraft:diamond', want: '8x minecraft:emerald' }");
        }
        Object want = JsonUtil.property(value, "want");
        if (want == null) {
            want = JsonUtil.property(value, "wants");
        }
        if (want == null) {
            throw new IllegalArgumentException("a trade needs 'want' (one or two costs)");
        }
        TradeItem first;
        TradeItem second = null;
        if (JsonUtil.unwrap(want) instanceof List<?> costs) {
            if (costs.isEmpty() || costs.size() > 2) {
                throw new IllegalArgumentException("'want' takes one or two costs, not " + costs.size());
            }
            first = TradeItem.parse(costs.get(0), "want[0]");
            if (costs.size() == 2) {
                second = TradeItem.parse(costs.get(1), "want[1]");
            }
        } else {
            first = TradeItem.parse(want, "want");
        }
        TradeItem result = TradeItem.parse(give, "give");
        int maxUses = integer(value, "maxUses", 12, 1, 9999);
        int xp = integer(value, "xp", 1, 0, 9999);
        int demand = integer(value, "demand", 0, -9999, 9999);
        Object multiplier = JsonUtil.property(value, "priceMultiplier");
        float priceMultiplier = 0.05f;
        if (multiplier != null) {
            if (!(JsonUtil.unwrap(multiplier) instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 10) {
                throw new IllegalArgumentException("'priceMultiplier' must be a number from 0 to 10");
            }
            priceMultiplier = n.floatValue();
        }
        List<String> biomes = ids(value, "biome", "biomes");
        List<String> types = ids(value, "villagerType", "villagerTypes");
        Function predicate = null;
        Object when = JsonUtil.property(value, "when");
        if (when != null) {
            if (!(when instanceof Function fn)) {
                throw new IllegalArgumentException("'when' must be a function: when: (trade, villager) => true");
            }
            predicate = fn;
        }
        return new TradeSpec(result, first, second, maxUses, xp, priceMultiplier, demand, biomes, types, predicate);
    }

    private static int integer(Object value, String key, int fallback, int min, int max) {
        Object raw = JsonUtil.property(value, key);
        if (raw == null) {
            return fallback;
        }
        if (!(JsonUtil.unwrap(raw) instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            throw new IllegalArgumentException("'" + key + "' must be a whole number");
        }
        int v = n.intValue();
        if (v < min || v > max) {
            throw new IllegalArgumentException("'" + key + "' must be " + min + " to " + max + ", not " + v);
        }
        return v;
    }

    private static List<String> ids(Object value, String single, String plural) {
        Object raw = JsonUtil.property(value, single);
        if (raw == null) {
            raw = JsonUtil.property(value, plural);
        }
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        Object plain = JsonUtil.unwrap(raw);
        List<?> items = plain instanceof List<?> list ? list : List.of(plain);
        for (Object item : items) {
            String id = JsonUtil.asString(item);
            if (id == null) {
                throw new IllegalArgumentException("'" + single + "' must be an id such as minecraft:plains or a list of ids");
            }
            String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
            if (!ID.matcher(full).matches()) {
                throw new IllegalArgumentException("'" + id + "' is not an id");
            }
            out.add(full);
        }
        return out;
    }

    /** What filters and predicates see: {@code give}, {@code want} (a list), {@code maxUses}, {@code xp}. */
    public Map<String, Object> view(String profession, int level, String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profession", profession);
        out.put("level", level);
        out.put("id", id == null ? "" : id);
        out.put("give", item(give));
        List<Object> wants = new ArrayList<>();
        wants.add(item(want));
        if (want2 != null) {
            wants.add(item(want2));
        }
        out.put("want", wants);
        out.put("maxUses", maxUses);
        out.put("xp", xp);
        return out;
    }

    static Map<String, Object> item(TradeItem item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", item.id());
        out.put("count", item.count());
        return out;
    }

    /** How a build spells loot conditions in trade files: 26.1.2 {@code condition} and {@code predicates}, 26.2 {@code condition} and {@code minecraft:predicates}, 26.3 {@code type} and {@code minecraft:predicates}. */
    public enum Style {
        V26_1("condition", "predicates"), V26_2("condition", "minecraft:predicates"), V26_3("type", "minecraft:predicates");

        final String conditionKey;
        final String predicatesKey;

        Style(String conditionKey, String predicatesKey) {
            this.conditionKey = conditionKey;
            this.predicatesKey = predicatesKey;
        }

        /** The style of the running build. */
        public static Style current() {
            if (com.itmecasey.modjs.script.PlatformBinding.atLeast(26, 3)) {
                return V26_3;
            }
            return com.itmecasey.modjs.script.PlatformBinding.atLeast(26, 2) ? V26_2 : V26_1;
        }
    }

    public JsonObject toJson() {
        return toJson(Style.V26_3);
    }

    /** The trade file of a 26.x build: gives, wants, additional_wants, max_uses, xp, reputation_discount, merchant_predicate. */
    public JsonObject toJson(Style style) {
        JsonObject out = new JsonObject();
        out.add("gives", give.toJson(true));
        out.add("wants", want.toJson(false));
        if (want2 != null) {
            out.add("additional_wants", want2.toJson(false));
        }
        out.addProperty("max_uses", maxUses);
        out.addProperty("xp", xp);
        out.addProperty("reputation_discount", priceMultiplier);
        List<JsonObject> conditions = new ArrayList<>();
        if (!villagerTypes.isEmpty()) {
            JsonObject variants = new JsonObject();
            JsonArray list = new JsonArray();
            villagerTypes.forEach(list::add);
            variants.add("minecraft:villager/variant", list);
            JsonObject predicates = new JsonObject();
            predicates.add(style.predicatesKey, variants);
            conditions.add(entity(predicates, style));
        }
        if (!biomes.isEmpty()) {
            JsonObject location = new JsonObject();
            JsonArray list = new JsonArray();
            biomes.forEach(list::add);
            location.add("biomes", list);
            JsonObject predicate = new JsonObject();
            predicate.add("location", location);
            conditions.add(entity(predicate, style));
        }
        if (conditions.size() == 1) {
            out.add("merchant_predicate", conditions.get(0));
        } else if (conditions.size() == 2) {
            JsonObject all = new JsonObject();
            all.addProperty(style.conditionKey, "minecraft:all_of");
            JsonArray terms = new JsonArray();
            conditions.forEach(terms::add);
            all.add("terms", terms);
            out.add("merchant_predicate", all);
        }
        return out;
    }

    private static JsonObject entity(JsonObject predicate, Style style) {
        JsonObject out = new JsonObject();
        out.addProperty(style.conditionKey, "minecraft:entity_properties");
        out.addProperty("entity", "this");
        out.add("predicate", predicate);
        return out;
    }
}
