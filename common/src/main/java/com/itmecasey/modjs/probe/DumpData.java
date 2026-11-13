package com.itmecasey.modjs.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Everything one dump collected, in plain collections so the generators and tests need no Minecraft classes. */
public final class DumpData {
    /** One loaded mod. */
    public record Mod(String id, String name, String version) {
    }

    public String minecraftVersion = "unknown";
    public String loader = "unknown";
    public String modjsVersion = "unknown";
    /** {@code server} when only the logical server was read, {@code client+server} when the client assets were read too. */
    public String side = "server";
    public String generatedAt = "";

    /** Registry name ({@code item}, {@code worldgen/biome}, {@code othermod:thing}) to its sorted ids. */
    public final Map<String, List<String>> registries = new TreeMap<>();
    /** Registry name to tag id to sorted member ids. */
    public final Map<String, Map<String, List<String>>> tags = new TreeMap<>();
    /** Block id to property name to its possible values. */
    public final Map<String, Map<String, List<String>>> blockStates = new TreeMap<>();
    /** Game rule name to {@code boolean} or {@code integer}. */
    public final Map<String, String> gameRules = new TreeMap<>();
    public final List<Mod> mods = new ArrayList<>();
    public JsonObject commands = new JsonObject();
    /** Recipe id to its JSON, as shipped by data packs and ModJS's generated pack. */
    public final Map<String, JsonElement> recipes = new TreeMap<>();
    /** Language key to English text (client). */
    public final Map<String, String> lang = new TreeMap<>();
    /** Asset kind ({@code texture}, {@code model}, {@code blockstate}, {@code font}, {@code particle}) to ids (client). */
    public final Map<String, List<String>> assets = new LinkedHashMap<>();
    /** Key mapping names (client). */
    public final List<String> keyMappings = new ArrayList<>();
    public boolean hasClientData;

    public int totalIds() {
        int total = 0;
        for (List<String> ids : registries.values()) {
            total += ids.size();
        }
        return total;
    }
}
