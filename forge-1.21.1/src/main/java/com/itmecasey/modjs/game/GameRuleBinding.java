package com.itmecasey.modjs.game;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The {@code GameRule} global: {@code GameRule.get('keepInventory')}, {@code GameRule.set('keepInventory', true)}, {@code GameRule.list()}. */
public final class GameRuleBinding {
    private static String normalize(String name) {
        String n = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        return n.replace("_", "").toLowerCase();
    }

    private static Map<String, GameRules.Key<?>> keys() {
        Map<String, GameRules.Key<?>> keys = new LinkedHashMap<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                keys.put(normalize(key.getId()), key);
            }
        });
        return keys;
    }

    private static MinecraftServer server() {
        MinecraftServer server = ServerState.get();
        if (server == null) {
            throw new IllegalStateException("GameRule can only be used while a world is loaded");
        }
        return server;
    }

    private static GameRules.Key<?> key(String name) {
        GameRules.Key<?> key = keys().get(normalize(name));
        if (key == null) {
            throw new IllegalArgumentException("Unknown game rule '" + name + "'; GameRule.list() shows the names");
        }
        return key;
    }

    /** Every game rule name. */
    public List<String> list() {
        List<String> names = new ArrayList<>();
        for (GameRules.Key<?> key : keys().values()) {
            names.add(key.getId());
        }
        return names;
    }

    /** The current value: a boolean or a number. */
    public Object get(String name) {
        GameRules.Value<?> value = server().getGameRules().getRule(key(name));
        if (value instanceof GameRules.BooleanValue b) {
            return b.get();
        }
        if (value instanceof GameRules.IntegerValue i) {
            return i.get();
        }
        return value.serialize();
    }

    /** Sets a rule; booleans take true/false, integer rules take a number. */
    public void set(String name, Object newValue) {
        MinecraftServer server = server();
        GameRules.Value<?> value = server.getGameRules().getRule(key(name));
        if (value instanceof GameRules.BooleanValue b) {
            b.set(newValue instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(newValue)), server);
        } else if (value instanceof GameRules.IntegerValue i) {
            i.set(newValue instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(newValue)), server);
        } else {
            throw new IllegalArgumentException("Game rule '" + name + "' has an unsupported type");
        }
    }
}
