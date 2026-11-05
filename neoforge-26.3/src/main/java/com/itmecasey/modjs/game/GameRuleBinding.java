package com.itmecasey.modjs.game;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The {@code GameRule} global: {@code GameRule.get('keep_inventory')}, {@code GameRule.set('keep_inventory', true)}, {@code GameRule.list()}. */
public final class GameRuleBinding {
    private static String normalize(String name) {
        String n = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        return n.replace("_", "").toLowerCase();
    }

    private static Map<String, GameRule<?>> rules() {
        Map<String, GameRule<?>> rules = new LinkedHashMap<>();
        for (GameRule<?> rule : BuiltInRegistries.GAME_RULE) {
            rules.put(normalize(rule.id()), rule);
        }
        return rules;
    }

    private static MinecraftServer server() {
        MinecraftServer server = ServerState.get();
        if (server == null) {
            throw new IllegalStateException("GameRule can only be used while a world is loaded");
        }
        return server;
    }

    private static GameRule<?> rule(String name) {
        GameRule<?> rule = rules().get(normalize(name));
        if (rule == null) {
            throw new IllegalArgumentException("Unknown game rule '" + name + "'; GameRule.list() shows the names");
        }
        return rule;
    }

    /** Every game rule name. */
    public List<String> list() {
        List<String> names = new ArrayList<>();
        for (GameRule<?> rule : rules().values()) {
            names.add(rule.id());
        }
        return names;
    }

    /** The current value: a boolean or a number. */
    public Object get(String name) {
        return server().getGameRules().get(rule(name));
    }

    /** Sets a rule; booleans take true/false, integer rules take a number. */
    @SuppressWarnings("unchecked")
    public void set(String name, Object newValue) {
        MinecraftServer server = server();
        GameRule<Object> rule = (GameRule<Object>) rule(name);
        Object converted;
        if (((Class<?>) rule.valueClass()) == Boolean.class) {
            converted = newValue instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(newValue));
        } else if (((Class<?>) rule.valueClass()) == Integer.class) {
            converted = newValue instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(newValue));
        } else {
            throw new IllegalArgumentException("Game rule '" + name + "' has an unsupported type");
        }
        server.getGameRules().set(rule, converted, server);
    }
}
