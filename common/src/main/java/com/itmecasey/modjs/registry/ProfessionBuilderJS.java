package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.villager.TradeSpec;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A villager profession a startup script declares: {@code StartupEvents.registry('villager_profession', event => event.create('mypack:smith')...)}
 * or {@code Villagers.profession('mypack:smith', {...})}. The platform modules register the profession and its job site point of interest;
 * the trades of its levels use the same trade pools as every profession.
 */
public final class ProfessionBuilderJS {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final String fullId;
    private final String namespace;
    private final String path;
    private String displayName;
    private final List<String> jobSites = new ArrayList<>();
    private final List<String> secondarySites = new ArrayList<>();
    private final List<String> requests = new ArrayList<>();
    private String workSound;
    private String textureBase = "minecraft:nitwit";
    private final Map<Integer, List<TradeSpec>> trades = new LinkedHashMap<>();

    ProfessionBuilderJS(String fullId) {
        this.fullId = fullId;
        int colon = fullId.indexOf(':');
        this.namespace = fullId.substring(0, colon);
        this.path = fullId.substring(colon + 1);
        this.displayName = RegistryNames.titleCase(path);
    }

    /** A builder for an id such as {@code mypack:smith} (a plain id goes to {@code modjs}). */
    public static ProfessionBuilderJS of(String id) {
        RegistryNames.Id parsed = RegistryNames.parse(id);
        return new ProfessionBuilderJS(parsed.full());
    }

    private static String checked(String id, String what) {
        String full = id == null ? "" : id.indexOf(':') < 0 ? "minecraft:" + id : id;
        if (!ID.matcher(full).matches()) {
            throw Context.reportRuntimeError("villager profession: '" + id + "' is not " + what + " (for example minecraft:smithing_table)");
        }
        return full;
    }

    // -- script API -----------------------------------------------------------------------------------------------------------

    /** The block or blocks a villager works at: any of their states makes the job site. */
    public ProfessionBuilderJS jobSite(String... blocks) {
        for (String block : blocks) {
            jobSites.add(checked(block, "a block id"));
        }
        return this;
    }

    /** Blocks that count as part of the job (a composter for a farmer): the villager can use them but they are not the job site. */
    public ProfessionBuilderJS secondarySite(String... blocks) {
        for (String block : blocks) {
            secondarySites.add(checked(block, "a block id"));
        }
        return this;
    }

    /** Items the villager picks up from the ground (the farmer takes wheat seeds). */
    public ProfessionBuilderJS requests(String... items) {
        for (String item : items) {
            requests.add(checked(item, "an item id"));
        }
        return this;
    }

    /** The sound played at the job site, a sound event id such as {@code minecraft:entity.villager.work_smith}. */
    public ProfessionBuilderJS workSound(String sound) {
        this.workSound = checked(sound, "a sound event id");
        return this;
    }

    /** The name shown for the profession (default: the id, capitalized). */
    public ProfessionBuilderJS name(String name) {
        this.displayName = name;
        return this;
    }

    /** The vanilla profession skin the villager wears, copied once: {@code minecraft:farmer}. A texture you put in your assets folder wins. */
    public ProfessionBuilderJS texture(String base) {
        this.textureBase = checked(base, "a profession id");
        return this;
    }

    /** Adds one trade to the pool of a level (1 to 5), the same trade object as {@code Villagers.addTrade}. */
    public ProfessionBuilderJS trade(Object level, Object trade) {
        int n = level(level);
        try {
            trades.computeIfAbsent(n, k -> new ArrayList<>()).add(TradeSpec.parse(trade));
        } catch (IllegalArgumentException e) {
            throw Context.reportRuntimeError("villager profession " + fullId + ": " + e.getMessage());
        }
        return this;
    }

    /** Adds a list of trades to the pool of a level. */
    public ProfessionBuilderJS trades(Object level, Object list) {
        if (!(JsonUtil.unwrap(list) instanceof List<?> items)) {
            throw Context.reportRuntimeError("villager profession " + fullId + ": trades needs a list: trades(1, [{ give: ..., want: ... }])");
        }
        items.forEach(item -> trade(level, item));
        return this;
    }

    private int level(Object raw) {
        if (!(JsonUtil.unwrap(raw) instanceof Number n) || n.intValue() < 1 || n.intValue() > 5 || n.doubleValue() != Math.rint(n.doubleValue())) {
            throw Context.reportRuntimeError("villager profession " + fullId + ": the level of a trade is 1 to 5");
        }
        return n.intValue();
    }

    // -- engine access ---------------------------------------------------------------------------------------------------------

    public String fullId() {
        return fullId;
    }

    public String namespace() {
        return namespace;
    }

    public String id() {
        return path;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> jobSites() {
        return List.copyOf(jobSites);
    }

    public List<String> secondarySites() {
        return List.copyOf(secondarySites);
    }

    public List<String> requestedItems() {
        return List.copyOf(requests);
    }

    /** The work sound id, or null for the default sound. */
    public String workSoundId() {
        return workSound;
    }

    public String textureBase() {
        return textureBase;
    }

    public Map<Integer, List<TradeSpec>> tradesByLevel() {
        return trades;
    }

    /** Problems that make the profession unusable, or null. */
    public String problem() {
        return jobSites.isEmpty() ? "villager profession " + fullId + " needs a job site: .jobSite('minecraft:smithing_table')" : null;
    }
}
