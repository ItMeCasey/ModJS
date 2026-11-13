package com.itmecasey.modjs.villager;

import com.itmecasey.modjs.script.ScriptType;

import java.util.ArrayList;
import java.util.List;

/**
 * What scripts asked to change about trades, in the order they asked. The 26.x builds turn these into data pack files; the 1.20.1 and 1.21.1
 * builds apply them to the trade lists when a world starts. Operations of a script type are dropped when its scripts run again.
 */
public final class TradeOps {
    public enum Kind {
        /** Adds trades to the pool of a level. */
        ADD,
        /** Replaces the pool of a level with these trades. */
        REPLACE,
        /** Removes the trades that match a filter (from one level or from all). */
        REMOVE,
        /** Empties every level of a profession. */
        CLEAR
    }

    /** One request. {@code level} is 1 to 5 (the wandering trader has 1 common, 2 rare and, on 26.x, 3 buying); 0 means every level. */
    public record Op(Kind kind, String profession, int level, List<TradeSpec> trades, TradeFilter filter, ScriptType by) {
    }

    public static final String WANDERING_TRADER = "minecraft:wandering_trader";

    private static final List<Op> OPS = new ArrayList<>();
    /** The trades of registered professions: kept when scripts run again, and applied before the requests of scripts. */
    private static List<Op> PROFESSION_OPS = List.of();

    private TradeOps() {
    }

    public static synchronized void add(Op op) {
        OPS.add(op);
    }

    public static synchronized List<Op> snapshot() {
        List<Op> all = new ArrayList<>(PROFESSION_OPS);
        all.addAll(OPS);
        return all;
    }

    public static synchronized boolean any() {
        return !OPS.isEmpty() || !PROFESSION_OPS.isEmpty();
    }

    /** Sets the trades of the registered professions (replacing the ones of an earlier call). */
    public static synchronized void setProfessionOps(List<Op> ops) {
        PROFESSION_OPS = List.copyOf(ops);
    }

    /** A script type runs again: its requests are made again by its scripts. */
    public static synchronized void scriptsReloading(ScriptType type) {
        OPS.removeIf(op -> op.by() == type);
    }

    public static synchronized void clear() {
        OPS.clear();
        PROFESSION_OPS = List.of();
    }

    public static boolean isWandering(String profession) {
        return WANDERING_TRADER.equals(profession);
    }

    /** The highest level of a profession: 5 for villagers, 3 for the wandering trader. */
    public static int maxLevel(String profession) {
        return isWandering(profession) ? 3 : 5;
    }

    /** The name of a level in the trade tags of 26.x: {@code level_3} for villagers, {@code common}, {@code uncommon} and {@code buying} for the wandering trader. */
    public static String levelName(String profession, int level) {
        if (isWandering(profession)) {
            return switch (level) {
                case 1 -> "common";
                case 2 -> "uncommon";
                default -> "buying";
            };
        }
        return "level_" + level;
    }
}
