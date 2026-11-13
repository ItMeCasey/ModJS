package com.itmecasey.modjs.villager;

import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The {@code Villagers} global (startup and server scripts): add, replace, remove and clear the trades of professions and of the wandering
 * trader. See {@link TradeOps} for when the changes take effect.
 */
public final class VillagerBinding {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static ScriptType type(String where) {
        ScriptType type = ScriptManager.currentType();
        if (type == ScriptType.CLIENT) {
            throw Context.reportRuntimeError(where + ": trades are changed from startup and server scripts, not client scripts");
        }
        return type == null ? ScriptType.SERVER : type;
    }

    private static String profession(String text, String where) {
        String full = text == null ? "" : text.indexOf(':') < 0 ? "minecraft:" + text : text;
        if (!ID.matcher(full).matches()) {
            throw Context.reportRuntimeError(where + ": '" + text + "' is not a profession id such as minecraft:librarian (the wandering trader is minecraft:wandering_trader)");
        }
        return full;
    }

    private static int level(String profession, Object raw, String where) {
        if (!(JsonUtil.unwrap(raw) instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            throw Context.reportRuntimeError(where + ": the level is a whole number");
        }
        int level = n.intValue();
        int max = TradeOps.maxLevel(profession);
        if (level < 1 || level > max) {
            throw Context.reportRuntimeError(where + ": the level of " + profession + " is 1 to " + max
                + (TradeOps.isWandering(profession) ? " (1 common, 2 rare, 3 buying; 3 exists on 26.x only)" : "") + ", not " + level);
        }
        return level;
    }

    private static TradeSpec trade(Object raw, String where) {
        try {
            return TradeSpec.parse(raw);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(where + ": " + e.getMessage());
        }
    }

    private static List<TradeSpec> trades(Object raw, String where) {
        if (!(JsonUtil.unwrap(raw) instanceof List<?> items)) {
            throw Context.reportRuntimeError(where + ": a list of trades is needed: [{ give: ..., want: ... }]");
        }
        List<TradeSpec> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            out.add(trade(items.get(i), where + " trade " + (i + 1)));
        }
        return out;
    }

    /** Adds a trade to the pool of a level: {@code Villagers.addTrade('minecraft:librarian', 2, { give: 'minecraft:diamond', want: ['8x minecraft:emerald'] })}. */
    public void addTrade(String profession, Object level, Object trade) {
        String where = "Villagers.addTrade";
        ScriptType by = type(where);
        String id = profession(profession, where);
        TradeOps.add(new TradeOps.Op(TradeOps.Kind.ADD, id, level(id, level, where), List.of(trade(trade, where)), null, by));
    }

    public void addTrades(String profession, Object level, Object list) {
        String where = "Villagers.addTrades";
        ScriptType by = type(where);
        String id = profession(profession, where);
        TradeOps.add(new TradeOps.Op(TradeOps.Kind.ADD, id, level(id, level, where), trades(list, where), null, by));
    }

    /** Replaces the pool of a level with these trades (an empty list empties the level). */
    public void replaceTrades(String profession, Object level, Object list) {
        String where = "Villagers.replaceTrades";
        ScriptType by = type(where);
        String id = profession(profession, where);
        TradeOps.add(new TradeOps.Op(TradeOps.Kind.REPLACE, id, level(id, level, where), trades(list, where), null, by));
    }

    /** Removes the trades of every level that match a filter (a function {@code trade => boolean} or {@code { level, give, want, id }}). */
    public void removeTrades(String profession, Object filter) {
        String where = "Villagers.removeTrades";
        ScriptType by = type(where);
        String id = profession(profession, where);
        TradeOps.add(new TradeOps.Op(TradeOps.Kind.REMOVE, id, 0, List.of(), filter(filter, by, where), by));
    }

    /** Removes the matching trades of one level. */
    public void removeTrades(String profession, Object level, Object filter) {
        String where = "Villagers.removeTrades";
        ScriptType by = type(where);
        String id = profession(profession, where);
        TradeOps.add(new TradeOps.Op(TradeOps.Kind.REMOVE, id, level(id, level, where), List.of(), filter(filter, by, where), by));
    }

    /** Removes every trade of a profession (all levels). */
    public void clearTrades(String profession) {
        String where = "Villagers.clearTrades";
        ScriptType by = type(where);
        String id = profession(profession, where);
        TradeOps.add(new TradeOps.Op(TradeOps.Kind.CLEAR, id, 0, List.of(), null, by));
    }

    private static TradeFilter filter(Object raw, ScriptType by, String where) {
        try {
            return TradeFilter.parse(raw, by);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(where + ": " + e.getMessage());
        }
    }

    /** The number of changes scripts asked for since the scripts last ran. */
    public int getPending() {
        return TradeOps.snapshot().size();
    }

    /**
     * Registers a villager profession (startup scripts):
     * {@code Villagers.profession('mypack:smith', { jobSite: 'minecraft:smithing_table', workSound: 'minecraft:entity.villager.work_toolsmith', trades: { 1: [...] } })}.
     * The same builder is available as {@code StartupEvents.registry('villager_profession', event => event.create('mypack:smith'))}.
     */
    public void profession(String id, Object options) {
        String where = "Villagers.profession";
        if (type(where) != ScriptType.STARTUP) {
            throw Context.reportRuntimeError(where + ": professions are registered from startup scripts");
        }
        com.itmecasey.modjs.registry.ProfessionBuilderJS builder;
        try {
            builder = com.itmecasey.modjs.registry.ProfessionBuilderJS.of(id);
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError(where + ": " + e.getMessage());
        }
        Object plain = JsonUtil.unwrap(options);
        builder.jobSite(strings(plain, "jobSite", where));
        builder.secondarySite(strings(plain, "secondarySite", where));
        builder.requests(strings(plain, "requests", where));
        String sound = JsonUtil.asString(JsonUtil.property(plain, "workSound"));
        if (sound != null) {
            builder.workSound(sound);
        }
        String name = JsonUtil.asString(JsonUtil.property(plain, "name"));
        if (name != null) {
            builder.name(name);
        }
        String texture = JsonUtil.asString(JsonUtil.property(plain, "texture"));
        if (texture != null) {
            builder.texture(texture);
        }
        Object trades = JsonUtil.property(plain, "trades");
        if (trades != null) {
            for (int level = 1; level <= 5; level++) {
                // keys such as 1 are array-style indexes in script objects, so they are read by number first
                Object list = trades instanceof org.mozilla.javascript.Scriptable object ? object.get(level, object) : null;
                if (list == null || list == org.mozilla.javascript.Scriptable.NOT_FOUND) {
                    list = JsonUtil.property(trades, String.valueOf(level));
                }
                if (list != null) {
                    builder.trades(level, list);
                }
            }
        }
        com.itmecasey.modjs.registry.RegistryManager.addProfession(builder);
    }

    private static String[] strings(Object options, String key, String where) {
        Object raw = JsonUtil.property(options, key);
        if (raw == null) {
            return new String[0];
        }
        Object plain = JsonUtil.unwrap(raw);
        List<?> items = plain instanceof List<?> list ? list : List.of(plain);
        String[] out = new String[items.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = JsonUtil.asString(items.get(i));
            if (out[i] == null) {
                throw Context.reportRuntimeError(where + ": '" + key + "' takes ids as text");
            }
        }
        return out;
    }
}
