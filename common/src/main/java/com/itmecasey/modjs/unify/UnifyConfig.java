package com.itmecasey.modjs.unify;

import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.recipe.RecipeFilter;
import com.itmecasey.modjs.recipe.RecipeJson;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything scripts say about unification. Startup scripts add materials and canonical items once; server scripts
 * (preferences, groups, skips) are cleared and re-read on every data reload.
 */
public final class UnifyConfig {
    /** How recipe inputs that name a non-canonical item are rewritten. */
    public enum InputMode {
        /** Replace with the canonical item (default). */
        CANONICAL,
        /** Replace with the group tag, so every member still works. */
        TAG
    }

    /** A group a script declared explicitly. */
    public record ExplicitGroup(String tag, String canonical, InputMode inputs) {
    }

    // -- startup state (kept for the life of the game) -------------------------------------------------------------

    private static final Map<String, String> CANONICAL_ITEMS = new LinkedHashMap<>();
    private static final Map<String, String> CANONICAL_FLUIDS = new LinkedHashMap<>();
    private static final List<UnifyMaterials.Spec> MATERIALS = new ArrayList<>();

    // -- server state (reset on reload) ----------------------------------------------------------------------------

    private static final List<String> PREFER = new ArrayList<>(List.of("modjs"));
    private static final List<ExplicitGroup> GROUPS = new ArrayList<>();
    private static final List<ExplicitGroup> FLUID_GROUPS = new ArrayList<>();
    private static boolean autoFluids;
    private static boolean oreWorldgen;
    private static boolean oreChunks;
    private static final Set<String> AUTO_CATEGORIES = new LinkedHashSet<>();
    private static final List<RecipeFilter> SKIP_RECIPES = new ArrayList<>();
    private static final Set<String> SKIP_ITEMS = new LinkedHashSet<>();
    private static InputMode inputMode = InputMode.CANONICAL;
    private static boolean swapInventories = true;
    private static boolean convertRecipes = true;

    private UnifyConfig() {
    }

    // -- engine access -----------------------------------------------------------------------------------------------

    public static synchronized void resetServer() {
        PREFER.clear();
        PREFER.add("modjs");
        GROUPS.clear();
        FLUID_GROUPS.clear();
        autoFluids = false;
        oreWorldgen = false;
        oreChunks = false;
        AUTO_CATEGORIES.clear();
        SKIP_RECIPES.clear();
        SKIP_ITEMS.clear();
        inputMode = InputMode.CANONICAL;
        swapInventories = true;
        convertRecipes = true;
    }

    public static synchronized void resetStartup() {
        CANONICAL_ITEMS.clear();
        CANONICAL_FLUIDS.clear();
        MATERIALS.clear();
    }

    static synchronized List<String> prefer() {
        return new ArrayList<>(PREFER);
    }

    static synchronized List<ExplicitGroup> groups() {
        return new ArrayList<>(GROUPS);
    }

    static synchronized List<ExplicitGroup> fluidGroups() {
        return new ArrayList<>(FLUID_GROUPS);
    }

    public static synchronized boolean oreWorldgen() {
        return oreWorldgen;
    }

    public static synchronized boolean oreChunks() {
        return oreChunks;
    }

    public static synchronized void setOres(boolean worldgen, boolean chunks) {
        oreWorldgen = worldgen;
        oreChunks = chunks;
    }

    static synchronized boolean autoFluids() {
        return autoFluids;
    }

    public static synchronized Map<String, String> canonicalFluids() {
        return new LinkedHashMap<>(CANONICAL_FLUIDS);
    }

    public static synchronized void addCanonicalFluid(String tag, String fluidId) {
        CANONICAL_FLUIDS.putIfAbsent(tag, fluidId);
    }

    static synchronized Set<String> autoCategories() {
        return new LinkedHashSet<>(AUTO_CATEGORIES);
    }

    static synchronized List<RecipeFilter> skipRecipes() {
        return new ArrayList<>(SKIP_RECIPES);
    }

    static synchronized Set<String> skipItems() {
        return new LinkedHashSet<>(SKIP_ITEMS);
    }

    static synchronized InputMode inputMode() {
        return inputMode;
    }

    public static synchronized boolean swapInventories() {
        return swapInventories;
    }

    static synchronized boolean convertRecipes() {
        return convertRecipes;
    }

    /** Canonical items registered through {@code ItemBuilder.unify(tag)} or {@code Unify.materials}, by tag id. */
    public static synchronized Map<String, String> canonicalItems() {
        return new LinkedHashMap<>(CANONICAL_ITEMS);
    }

    public static synchronized void addCanonicalItem(String tag, String itemId) {
        CANONICAL_ITEMS.putIfAbsent(tag, itemId);
    }

    public static synchronized List<UnifyMaterials.Spec> materialSpecs() {
        return new ArrayList<>(MATERIALS);
    }

    public static synchronized void addMaterials(List<String> materials, List<String> kinds, boolean includeVanilla) {
        MATERIALS.add(new UnifyMaterials.Spec(materials, kinds, includeVanilla));
    }

    public static synchronized void skipRecipe(RecipeFilter filter) {
        SKIP_RECIPES.add(filter);
    }

    /** Declares a group without going through a script; used by platform code and tests. */
    public static synchronized void group(String tag, String canonical) {
        GROUPS.add(new ExplicitGroup(tag, canonical, null));
    }

    // -- the script API (the Unify global) ---------------------------------------------------------------------------

    /** The {@code Unify} global. */
    public static final class Binding {
        /**
         * Startup: registers canonical items for materials. Options: {@code materials} (names such as {@code 'tin'}, or
         * leave out for the built-in list), {@code kinds} (any of ingot, nugget, dust, plate, gear, rod, wire, gem, coin,
         * raw, storage_block; leave out for all), {@code includeVanilla} (default false: skip materials Minecraft
         * already has, such as iron ingots).
         */
        public void materials(Object options) {
            List<String> materials = strings(JsonUtil.property(options, "materials"));
            List<String> kinds = strings(JsonUtil.property(options, "kinds"));
            Object vanilla = JsonUtil.property(options, "includeVanilla");
            synchronized (UnifyConfig.class) {
                MATERIALS.add(new UnifyMaterials.Spec(materials, kinds, Boolean.TRUE.equals(vanilla)));
            }
        }

        public void materials() {
            materials(null);
        }

        /**
         * Unifies fluids: {@code fluid('c:oil', {canonical: 'modjs:oil'})}. Fluid ids stored under {@code fluid} keys in recipes
         * are rewritten; a fluid built with {@code .unify(tag)} is canonical without naming it here.
         */
        public void fluid(String tag, Object options) {
            String id = tag.startsWith("#") ? tag.substring(1) : tag;
            String canonical = JsonUtil.asString(JsonUtil.property(options, "canonical"));
            synchronized (UnifyConfig.class) {
                FLUID_GROUPS.add(new ExplicitGroup(id.contains(":") ? id : "c:" + id, canonical, null));
            }
        }

        public void fluid(String tag) {
            fluid(tag, null);
        }

        /**
         * Forces the canonical ore block into the world, for groups in {@code c:ores/*}. {@code worldgen} (default true) swaps other
         * mods' ore features for the canonical block in new chunks; {@code chunks} (default true) also converts ore blocks already
         * in chunks as they load. Changes to worldgen apply when the world or server starts.
         */
        public void ores(Object options) {
            Object worldgen = options == null ? null : JsonUtil.property(options, "worldgen");
            Object chunks = options == null ? null : JsonUtil.property(options, "chunks");
            setOres(worldgen == null || Boolean.TRUE.equals(worldgen), chunks == null || Boolean.TRUE.equals(chunks));
        }

        public void ores() {
            ores(null);
        }

        /** Unifies every fluid tag in the {@code c} namespace that contains more than one fluid. */
        public void autoFluids(boolean enabled) {
            synchronized (UnifyConfig.class) {
                autoFluids = enabled;
            }
        }

        /** Mods whose item wins when no ModJS item exists for a group, in order. {@code modjs} is always first unless you list it. */
        public void prefer(Object mods) {
            synchronized (UnifyConfig.class) {
                PREFER.clear();
                PREFER.addAll(strings(mods));
            }
        }

        /** Declares one group: {@code group('c:ingots/tin', {canonical: 'create:tin_ingot', inputs: 'tag'})}. */
        public void group(String tag, Object options) {
            String id = tag.startsWith("#") ? tag.substring(1) : tag;
            if (!id.contains(":")) {
                throw Context.reportRuntimeError("Unify.group needs a namespaced tag like 'c:ingots/tin', got '" + tag + "'");
            }
            String canonical = JsonUtil.asString(JsonUtil.property(options, "canonical"));
            String inputs = JsonUtil.asString(JsonUtil.property(options, "inputs"));
            synchronized (UnifyConfig.class) {
                GROUPS.add(new ExplicitGroup(id, canonical, inputs == null ? null : mode(inputs)));
            }
        }

        public void group(String tag) {
            group(tag, null);
        }

        /** Unifies every {@code c:<category>/*} tag, for example {@code auto(['ingots', 'dusts'])}; {@code auto('all')} uses every built-in kind. */
        public void auto(Object categories) {
            List<String> list = strings(categories);
            synchronized (UnifyConfig.class) {
                if (list.size() == 1 && list.get(0).equals("all")) {
                    AUTO_CATEGORIES.addAll(UnifyMaterials.categories());
                } else {
                    AUTO_CATEGORIES.addAll(list);
                }
            }
        }

        /** Leaves matching recipes untouched: a filter like {@code {mod: 'create'}} or a recipe id. */
        public void skipRecipes(Object filter) {
            synchronized (UnifyConfig.class) {
                SKIP_RECIPES.add(RecipeFilter.of(filter));
            }
        }

        /** Never replaces these items (ids or {@code @mod}). */
        public void skipItems(Object items) {
            synchronized (UnifyConfig.class) {
                SKIP_ITEMS.addAll(strings(items));
            }
        }

        /** "canonical" (default) or "tag": what recipe inputs naming a non-canonical item become. */
        public void inputs(String mode) {
            synchronized (UnifyConfig.class) {
                inputMode = mode(mode);
            }
        }

        /** Replace non-canonical items in player inventories with the canonical one (default true). */
        public void swapInventories(boolean enabled) {
            synchronized (UnifyConfig.class) {
                swapInventories = enabled;
            }
        }

        /** Add one-to-one recipes that turn each non-canonical item into the canonical one (default true). */
        public void convertRecipes(boolean enabled) {
            synchronized (UnifyConfig.class) {
                convertRecipes = enabled;
            }
        }

        private static InputMode mode(String text) {
            return switch (text.toLowerCase()) {
                case "canonical" -> InputMode.CANONICAL;
                case "tag" -> InputMode.TAG;
                default -> throw Context.reportRuntimeError("Unify inputs must be 'canonical' or 'tag', got '" + text + "'");
            };
        }

        private static List<String> strings(Object raw) {
            List<String> out = new ArrayList<>();
            if (raw == null) {
                return out;
            }
            for (Object entry : RecipeJson.asList(raw)) {
                String text = JsonUtil.asString(entry);
                if (text != null) {
                    out.add(text);
                }
            }
            return out;
        }
    }
}
