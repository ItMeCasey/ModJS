package com.itmecasey.modjs.unify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.format.VersionFormat;
import com.itmecasey.modjs.recipe.RecipeChanges;
import com.itmecasey.modjs.recipe.RecipeFilter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The result of working out which items are the same material and which one is canonical. Built once after every data
 * reload (the platform module supplies the tag files and an "item exists" check), then used to rewrite recipes, add
 * conversion recipes, replace drops and swap items in inventories.
 */
public final class Unifier {
    /** One unified material. */
    public record Group(String tag, String canonical, UnifyConfig.InputMode inputs, List<String> members) {
    }

    /** Prefix of the recipe ids ModJS generates; these are never rewritten. */
    public static final String CONVERSION_PREFIX = "modjs:unify/";

    private static volatile Unifier current = new Unifier(List.of(), List.of(), false, List.of());

    private final List<Group> groups;
    private final boolean convert;
    private final List<RecipeFilter> skipRecipes;
    private final Map<String, Group> byMember = new LinkedHashMap<>();
    private final List<Group> fluidGroups;
    private final Map<String, String> fluidCanonical = new LinkedHashMap<>();
    private int fluidsRewritten;
    private int inputsRewritten;
    private int outputsRewritten;

    private Unifier(List<Group> groups, List<Group> fluidGroups, boolean convert, List<RecipeFilter> skipRecipes) {
        this.groups = groups;
        this.fluidGroups = fluidGroups;
        for (Group group : fluidGroups) {
            group.members().forEach(member -> fluidCanonical.putIfAbsent(member, group.canonical()));
        }
        this.convert = convert;
        this.skipRecipes = skipRecipes;
        for (Group group : groups) {
            for (String member : group.members()) {
                byMember.putIfAbsent(member, group);
            }
        }
    }

    public static Unifier current() {
        return current;
    }

    public static void set(Unifier unifier) {
        current = unifier;
    }

    public static void clear() {
        current = new Unifier(List.of(), List.of(), false, List.of());
    }

    // -- building ----------------------------------------------------------------------------------------------------

    public static Unifier build(TagIndex index, Predicate<String> itemExists) {
        return build(index, itemExists, new TagIndex(Map.of()), id -> false);
    }

    public static Unifier build(TagIndex index, Predicate<String> itemExists, TagIndex fluidIndex, Predicate<String> fluidExists) {
        Map<String, UnifyConfig.ExplicitGroup> wanted = new LinkedHashMap<>();
        for (UnifyConfig.ExplicitGroup group : UnifyConfig.groups()) {
            wanted.putIfAbsent(group.tag(), group);
        }
        for (String tag : UnifyConfig.canonicalItems().keySet()) {
            wanted.putIfAbsent(tag, new UnifyConfig.ExplicitGroup(tag, null, null));
        }
        for (String category : UnifyConfig.autoCategories()) {
            String prefix = "c:" + category + "/";
            for (String tag : index.tagIds()) {
                if (tag.startsWith(prefix)) {
                    wanted.putIfAbsent(tag, new UnifyConfig.ExplicitGroup(tag, null, null));
                }
            }
        }
        Set<String> skipItems = UnifyConfig.skipItems();
        Map<String, String> ours = UnifyConfig.canonicalItems();
        List<String> prefer = UnifyConfig.prefer();
        List<Group> groups = new ArrayList<>();
        Set<String> claimed = new LinkedHashSet<>();
        for (UnifyConfig.ExplicitGroup group : wanted.values()) {
            Set<String> members = new LinkedHashSet<>();
            for (String id : index.resolve(group.tag())) {
                if (itemExists.test(id)) {
                    members.add(id);
                }
            }
            String canonical = group.canonical() != null ? group.canonical() : ours.get(group.tag());
            if (canonical == null) {
                canonical = pick(members, prefer);
            }
            if (canonical == null || !itemExists.test(canonical)) {
                continue;
            }
            members.add(canonical);
            List<String> others = new ArrayList<>();
            for (String member : members) {
                if (!member.equals(canonical) && !skipped(member, skipItems) && !claimed.contains(member)) {
                    others.add(member);
                }
            }
            if (others.isEmpty()) {
                continue;
            }
            claimed.addAll(others);
            claimed.add(canonical);
            UnifyConfig.InputMode mode = group.inputs() != null ? group.inputs() : UnifyConfig.inputMode();
            groups.add(new Group(group.tag(), canonical, mode, others));
        }
        return new Unifier(groups, buildFluidGroups(fluidIndex, fluidExists, skipItems), UnifyConfig.convertRecipes(), UnifyConfig.skipRecipes());
    }

    private static List<Group> buildFluidGroups(TagIndex index, Predicate<String> exists, Set<String> skip) {
        Map<String, UnifyConfig.ExplicitGroup> wanted = new LinkedHashMap<>();
        for (UnifyConfig.ExplicitGroup group : UnifyConfig.fluidGroups()) {
            wanted.putIfAbsent(group.tag(), group);
        }
        for (String tag : UnifyConfig.canonicalFluids().keySet()) {
            wanted.putIfAbsent(tag, new UnifyConfig.ExplicitGroup(tag, null, null));
        }
        if (UnifyConfig.autoFluids()) {
            for (String tag : index.tagIds()) {
                if (tag.startsWith("c:")) {
                    wanted.putIfAbsent(tag, new UnifyConfig.ExplicitGroup(tag, null, null));
                }
            }
        }
        Map<String, String> ours = UnifyConfig.canonicalFluids();
        List<String> prefer = UnifyConfig.prefer();
        List<Group> out = new ArrayList<>();
        Set<String> claimed = new LinkedHashSet<>();
        for (UnifyConfig.ExplicitGroup group : wanted.values()) {
            Set<String> members = new LinkedHashSet<>();
            for (String id : index.resolve(group.tag())) {
                if (exists.test(id)) {
                    members.add(id);
                }
            }
            String canonical = group.canonical() != null ? group.canonical() : ours.get(group.tag());
            if (canonical == null) {
                canonical = pick(members, prefer);
            }
            if (canonical == null || !exists.test(canonical)) {
                continue;
            }
            members.add(canonical);
            List<String> others = new ArrayList<>();
            for (String member : members) {
                if (!member.equals(canonical) && !skipped(member, skip) && !claimed.contains(member)) {
                    others.add(member);
                }
            }
            if (!others.isEmpty()) {
                claimed.addAll(others);
                claimed.add(canonical);
                out.add(new Group(group.tag(), canonical, UnifyConfig.InputMode.CANONICAL, others));
            }
        }
        return out;
    }

    public List<Group> fluidGroups() {
        return fluidGroups;
    }

    public String fluidCanonicalFor(String fluidId) {
        return fluidCanonical.get(fluidId);
    }

    public int fluidsRewritten() {
        return fluidsRewritten;
    }

    private static String pick(Set<String> members, List<String> prefer) {
        for (String mod : prefer) {
            for (String member : members) {
                if (member.startsWith(mod + ":")) {
                    return member;
                }
            }
        }
        return null;
    }

    private static boolean skipped(String item, Set<String> skip) {
        for (String entry : skip) {
            if (entry.equals(item) || (entry.startsWith("@") && item.startsWith(entry.substring(1) + ":"))) {
                return true;
            }
        }
        return false;
    }

    // -- queries ------------------------------------------------------------------------------------------------------

    public boolean active() {
        return !groups.isEmpty() || !fluidGroups.isEmpty();
    }

    public List<Group> groups() {
        return groups;
    }

    /** The canonical item for a non-canonical member, or null if the item is not unified. */
    public String canonicalFor(String itemId) {
        Group group = byMember.get(itemId);
        return group == null ? null : group.canonical();
    }

    /** Non-canonical id to canonical id for the groups in {@code c:ores/*}; block ids equal item ids for block items. */
    public Map<String, String> oreReplacements() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Group group : groups) {
            if (group.tag().startsWith("c:ores/") || group.tag().equals("c:ores")) {
                for (String member : group.members()) {
                    out.put(member, group.canonical());
                }
            }
        }
        return out;
    }

    public int inputsRewritten() {
        return inputsRewritten;
    }

    public int outputsRewritten() {
        return outputsRewritten;
    }

    // -- recipes ------------------------------------------------------------------------------------------------------

    /** Returns the same instance when nothing changed, otherwise a rewritten copy. */
    public synchronized JsonObject apply(String recipeId, JsonObject json) {
        if (!active() || recipeId.startsWith(CONVERSION_PREFIX)) {
            return json;
        }
        for (RecipeFilter filter : skipRecipes) {
            if (filter.matches(recipeId, json)) {
                return json;
            }
        }
        JsonObject copy = json.deepCopy();
        boolean outputs = RecipeChanges.mapResult(copy, this::canonicalFor);
        boolean inputs = RecipeChanges.mapIngredients(copy, token -> {
            Group group = byMember.get(token);
            if (group == null) {
                return null;
            }
            return group.inputs() == UnifyConfig.InputMode.TAG ? "#" + group.tag() : group.canonical();
        });
        boolean fluids = !fluidCanonical.isEmpty() && RecipeChanges.mapFluids(copy, this::fluidCanonicalFor);
        if (fluids) {
            fluidsRewritten++;
        }
        if (outputs) {
            outputsRewritten++;
        }
        if (inputs) {
            inputsRewritten++;
        }
        return outputs || inputs || fluids ? copy : json;
    }

    /** One-to-one recipes that turn each non-canonical item into the canonical one, by recipe id. */
    public Map<String, JsonObject> conversionRecipes() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        if (!convert) {
            return out;
        }
        VersionFormat format = VersionFormat.current();
        for (Group group : groups) {
            String canonicalPath = group.canonical().replace(':', '_').replace('/', '_');
            for (String member : group.members()) {
                JsonObject recipe = new JsonObject();
                recipe.addProperty("type", "minecraft:crafting_shapeless");
                JsonArray ingredients = new JsonArray();
                ingredients.add(format.ingredient(member));
                recipe.add("ingredients", ingredients);
                recipe.add("result", format.result(group.canonical(), 1));
                String id = CONVERSION_PREFIX + canonicalPath + "_from_" + member.replace(':', '_').replace('/', '_');
                out.put(id, recipe);
            }
        }
        return out;
    }
}
