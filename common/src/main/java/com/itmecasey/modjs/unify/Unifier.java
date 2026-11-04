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

    private static volatile Unifier current = new Unifier(List.of(), false, List.of());

    private final List<Group> groups;
    private final boolean convert;
    private final List<RecipeFilter> skipRecipes;
    private final Map<String, Group> byMember = new LinkedHashMap<>();
    private int inputsRewritten;
    private int outputsRewritten;

    private Unifier(List<Group> groups, boolean convert, List<RecipeFilter> skipRecipes) {
        this.groups = groups;
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
        current = new Unifier(List.of(), false, List.of());
    }

    // -- building ----------------------------------------------------------------------------------------------------

    public static Unifier build(TagIndex index, Predicate<String> itemExists) {
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
        return new Unifier(groups, UnifyConfig.convertRecipes(), UnifyConfig.skipRecipes());
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
        return !groups.isEmpty();
    }

    public List<Group> groups() {
        return groups;
    }

    /** The canonical item for a non-canonical member, or null if the item is not unified. */
    public String canonicalFor(String itemId) {
        Group group = byMember.get(itemId);
        return group == null ? null : group.canonical();
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
        if (groups.isEmpty() || recipeId.startsWith(CONVERSION_PREFIX)) {
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
        if (outputs) {
            outputsRewritten++;
        }
        if (inputs) {
            inputsRewritten++;
        }
        return outputs || inputs ? copy : json;
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
