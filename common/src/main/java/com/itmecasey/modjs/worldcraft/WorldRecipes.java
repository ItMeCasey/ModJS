package com.itmecasey.modjs.worldcraft;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.recipe.RecipeJson;
import com.itmecasey.modjs.worldcraft.WorldHandles.ItemEntityHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.ItemHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.LevelHandle;
import com.itmecasey.modjs.worldcraft.WorldHandles.PlayerHandle;
import com.itmecasey.modjs.worldcraft.WorldRecipe.Trigger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-world crafting (like the Lychee mod): recipes that happen to blocks and dropped items in the world. Scripts define them with
 * {@code ServerEvents.worldRecipes}; each Minecraft build calls the entry points below from its click, break, tick, lightning and
 * anvil hooks. The engine only talks to the game through {@link WorldHandles}.
 */
public final class WorldRecipes {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static volatile List<WorldRecipe> recipes = List.of();

    private WorldRecipes() {
    }

    // -- the script event ---------------------------------------------------------------------------------------------

    /** {@code ServerEvents.worldRecipes(event => {...})}: every method adds one recipe and returns it for chaining. */
    public static final class WorldRecipesEventJS extends EventJS {
        private final List<WorldRecipe> created = new ArrayList<>();

        private WorldRecipe add(Trigger trigger, String block) {
            if (block == null || block.isEmpty()) {
                throw Context.reportRuntimeError("worldRecipes: " + trigger.label() + " needs a block id such as 'minecraft:stone' or a '#tag'");
            }
            WorldRecipe recipe = new WorldRecipe(trigger, WorldRecipe.withNamespace(block));
            created.add(recipe);
            return recipe;
        }

        private WorldRecipe withItem(Trigger trigger, String block, Object item) {
            WorldRecipe recipe = add(trigger, block);
            if (item != null && !(item instanceof org.mozilla.javascript.Undefined)) {
                WorldRecipe.Input input = WorldRecipe.parseInput(item);
                recipe.inputs.add(input);
            }
            return recipe;
        }

        private WorldRecipe withItems(Trigger trigger, String block, Object items) {
            WorldRecipe recipe = add(trigger, block);
            for (Object entry : RecipeJson.asList(items)) {
                recipe.inputs.add(WorldRecipe.parseInput(entry));
            }
            if (recipe.inputs.isEmpty()) {
                throw Context.reportRuntimeError("worldRecipes: " + trigger.label() + " needs at least one item");
            }
            return recipe;
        }

        /** Right-click a block with an item: {@code itemOnBlock('minecraft:stone', 'minecraft:flint')}. */
        public WorldRecipe itemOnBlock(String block, Object item) {
            return withItem(Trigger.RIGHT_CLICK, block, item);
        }

        /** Right-click a block with anything (or an empty hand). */
        public WorldRecipe itemOnBlock(String block) {
            return withItem(Trigger.RIGHT_CLICK, block, null);
        }

        /** Left-click a block while holding an item. */
        public WorldRecipe leftClickBlock(String block, Object item) {
            return withItem(Trigger.LEFT_CLICK, block, item);
        }

        public WorldRecipe leftClickBlock(String block) {
            return withItem(Trigger.LEFT_CLICK, block, null);
        }

        /** Dropped items inside a block (water, lava, fire, a cauldron...): {@code itemsInBlock('minecraft:water', ['minecraft:coal', 'minecraft:sand'])}. */
        public WorldRecipe itemsInBlock(String block, Object items) {
            return withItems(Trigger.ITEMS_IN, block, items);
        }

        /** Dropped items resting on top of a block. */
        public WorldRecipe itemsOnBlock(String block, Object items) {
            return withItems(Trigger.ITEMS_ON, block, items);
        }

        /** A falling anvil lands on the block. */
        public WorldRecipe anvil(String block) {
            return add(Trigger.ANVIL, block);
        }

        /** Lightning strikes the block; add {@code .item(...)} to also need dropped items within three blocks. */
        public WorldRecipe lightning(String block) {
            return add(Trigger.LIGHTNING, block);
        }

        /** The block slowly changes by itself (checked on random blocks near players, about as often as vanilla's random ticks); set {@code .chance()} low. */
        public WorldRecipe randomTick(String block) {
            return add(Trigger.RANDOM_TICK, block).chance(0.01);
        }

        /** Breaking the block gives extra drops, or different ones with {@code .replaceDrops()}. */
        public WorldRecipe blockBreak(String block) {
            return add(Trigger.BLOCK_BREAK, block);
        }
    }

    /** Runs the {@code worldRecipes} handlers of the server scripts and keeps what they created. Call after server scripts (re)load. */
    public static void rebuild() {
        WorldRecipesEventJS event = new WorldRecipesEventJS();
        try {
            EventGroups.SERVER.post("worldRecipes", event);
        } catch (RhinoException e) {
            LOGGER.error("ServerEvents.worldRecipes failed: {}", e.getMessage());
        }
        recipes = List.copyOf(event.created);
        if (!recipes.isEmpty()) {
            LOGGER.info("ModJS in-world recipes: {}", recipes.size());
        }
    }

    public static List<WorldRecipe> all() {
        return recipes;
    }

    /** Replaces the recipes (tests). */
    public static void set(List<WorldRecipe> list) {
        recipes = List.copyOf(list);
    }

    public static boolean any(Trigger trigger) {
        for (WorldRecipe r : recipes) {
            if (r.trigger == trigger) {
                return true;
            }
        }
        return false;
    }

    /** True when some recipe needs the item-entity scan (items in or on blocks, lightning with items). */
    public static boolean needsItemScan() {
        return any(Trigger.ITEMS_IN) || any(Trigger.ITEMS_ON);
    }

    // -- clicks -------------------------------------------------------------------------------------------------------

    /** A right or left click on a block. Returns true when a recipe happened (the game should cancel the click). */
    public static boolean click(LevelHandle w, PlayerHandle p, int x, int y, int z, boolean left, boolean offHand) {
        Trigger wanted = left ? Trigger.LEFT_CLICK : Trigger.RIGHT_CLICK;
        for (WorldRecipe r : recipes) {
            if (r.trigger != wanted || !handOk(r, offHand) || (r.sneaking != null && r.sneaking != p.sneaking())) {
                continue;
            }
            ItemHandle held = p.held(offHand);
            WorldRecipe.Input input = r.inputs.isEmpty() ? null : r.inputs.get(0);
            if (input != null && (held == null || held.isEmpty() || !held.matches(input.matcher()) || held.count() < input.count())) {
                continue;
            }
            if (!w.blockMatches(x, y, z, r.block)) {
                continue;
            }
            if (!p.creative() && held != null) {
                int use = r.consume != null ? r.consume : (input != null ? input.count() : 0);
                if (use > 0) {
                    held.shrink(use);
                }
                if (r.damage > 0) {
                    held.damage(r.damage);
                }
            } else if (r.damage > 0 && held != null) {
                held.damage(r.damage);
            }
            if (w.random() < r.chance) {
                apply(w, r, x, y, z, p, false);
            }
            return true;
        }
        return false;
    }

    private static boolean handOk(WorldRecipe r, boolean offHand) {
        return r.hand.equals("any") || (r.hand.equals("off") == offHand);
    }

    // -- breaking blocks ----------------------------------------------------------------------------------------------

    /** A player is about to break a block. Returns true when the game must cancel the normal break (the recipe already dealt with the block). */
    public static boolean breakBlock(LevelHandle w, PlayerHandle p, int x, int y, int z) {
        if (p.creative()) {
            return false;
        }
        for (WorldRecipe r : recipes) {
            if (r.trigger != Trigger.BLOCK_BREAK || !w.blockMatches(x, y, z, r.block)) {
                continue;
            }
            ItemHandle held = p.held(false);
            if (r.tool != null && (held == null || held.isEmpty() || !held.matches(r.tool))) {
                continue;
            }
            if (r.sneaking != null && r.sneaking != p.sneaking()) {
                continue;
            }
            boolean cancel = r.turnInto != null || r.breakBlock || r.replaceDrops;
            if (w.random() < r.chance) {
                if (r.replaceDrops && r.turnInto == null) {
                    w.removeBlock(x, y, z);
                }
                apply(w, r, x, y, z, p, true);
            }
            if (cancel && r.damage > 0 && held != null) {
                held.damage(r.damage);
            }
            return cancel;
        }
        return false;
    }

    // -- block events ---------------------------------------------------------------------------------------------------

    /** An anvil landed on the block at the given position. */
    public static void anvil(LevelHandle w, int x, int y, int z) {
        for (WorldRecipe r : recipes) {
            if (r.trigger == Trigger.ANVIL && w.blockMatches(x, y, z, r.block)) {
                if (w.random() < r.chance) {
                    apply(w, r, x, y, z, null, false);
                }
                return;
            }
        }
    }

    /** Lightning struck at a position; {@code nearby} are the dropped items within three blocks. */
    public static void lightning(LevelHandle w, int x, int y, int z, List<ItemEntityHandle> nearby) {
        for (WorldRecipe r : recipes) {
            if (r.trigger != Trigger.LIGHTNING) {
                continue;
            }
            int cellY;
            if (w.blockMatches(x, y, z, r.block)) {
                cellY = y;
            } else if (w.blockMatches(x, y - 1, z, r.block)) {
                cellY = y - 1;
            } else {
                continue;
            }
            if (!r.inputs.isEmpty() && !takeItems(r, nearby)) {
                continue;
            }
            if (w.random() < r.chance) {
                apply(w, r, x, cellY, z, null, false);
            }
        }
    }

    /** A block picked at random near a player (see {@link WorldRecipesEventJS#randomTick}). */
    public static void randomTick(LevelHandle w, int x, int y, int z) {
        for (WorldRecipe r : recipes) {
            if (r.trigger == Trigger.RANDOM_TICK && w.blockMatches(x, y, z, r.block)) {
                if (w.random() < r.chance) {
                    apply(w, r, x, y, z, null, false);
                }
                return;
            }
        }
    }

    // -- dropped items --------------------------------------------------------------------------------------------------

    private record Cell(int x, int y, int z) {
    }

    /** Looks at the dropped items of a level and runs the items-in-block and items-on-block recipes. Call every few ticks. */
    public static void items(LevelHandle w, List<ItemEntityHandle> items) {
        if (items.isEmpty() || !needsItemScan()) {
            return;
        }
        Map<Cell, List<ItemEntityHandle>> inside = new HashMap<>();
        Map<Cell, List<ItemEntityHandle>> on = new HashMap<>();
        for (ItemEntityHandle item : items) {
            inside.computeIfAbsent(new Cell((int) Math.floor(item.x()), (int) Math.floor(item.y()), (int) Math.floor(item.z())), k -> new ArrayList<>()).add(item);
            on.computeIfAbsent(new Cell((int) Math.floor(item.x()), (int) Math.floor(item.y() - 0.01), (int) Math.floor(item.z())), k -> new ArrayList<>()).add(item);
        }
        for (WorldRecipe r : recipes) {
            if (r.trigger != Trigger.ITEMS_IN && r.trigger != Trigger.ITEMS_ON) {
                continue;
            }
            boolean in = r.trigger == Trigger.ITEMS_IN;
            for (Map.Entry<Cell, List<ItemEntityHandle>> entry : (in ? inside : on).entrySet()) {
                Cell c = entry.getKey();
                if (!w.blockMatches(c.x(), c.y(), c.z(), r.block)) {
                    continue;
                }
                for (int guard = 0; guard < 16 && takeItems(r, entry.getValue()); guard++) {
                    if (w.random() < r.chance) {
                        apply(w, r, c.x(), c.y(), c.z(), null, in);
                    }
                }
            }
        }
    }

    /** Uses the recipe's items up from the dropped items if all of them are there; false (and nothing used) otherwise. */
    private static boolean takeItems(WorldRecipe r, List<ItemEntityHandle> list) {
        int[] left = new int[list.size()];
        for (int i = 0; i < left.length; i++) {
            left[i] = list.get(i).stack().count();
        }
        int[][] takes = new int[r.inputs.size()][left.length];
        for (int k = 0; k < r.inputs.size(); k++) {
            WorldRecipe.Input input = r.inputs.get(k);
            int need = input.count();
            for (int i = 0; i < left.length && need > 0; i++) {
                if (left[i] > 0 && list.get(i).stack().matches(input.matcher())) {
                    int take = Math.min(left[i], need);
                    left[i] -= take;
                    need -= take;
                    takes[k][i] = take;
                }
            }
            if (need > 0) {
                return false;
            }
        }
        if (r.consume != null && r.consume == 0) {
            return true;
        }
        for (int i = 0; i < left.length; i++) {
            int total = 0;
            for (int k = 0; k < takes.length; k++) {
                total += takes[k][i];
            }
            if (total > 0) {
                list.get(i).stack().shrink(total);
                list.get(i).changed();
            }
        }
        return true;
    }

    // -- results --------------------------------------------------------------------------------------------------------

    /** Applies the results at a block. {@code inside} drops the items at the middle of the block instead of on top of it. */
    private static void apply(LevelHandle w, WorldRecipe r, int x, int y, int z, PlayerHandle p, boolean inside) {
        boolean gone = false;
        if (r.turnInto != null) {
            w.setBlock(x, y, z, r.turnInto);
            gone = r.turnInto.startsWith("minecraft:air");
        } else if (r.breakBlock) {
            w.removeBlock(x, y, z);
            gone = true;
        }
        double dropY = gone || inside ? y + 0.5 : y + 1.05;
        for (WorldRecipe.Drop drop : r.drops) {
            w.spawnItem(x + 0.5, dropY, z + 0.5, drop.itemId(), drop.count());
        }
        for (WorldRecipe.Drop give : r.gives) {
            if (p != null) {
                p.give(give.itemId(), give.count());
            } else {
                w.spawnItem(x + 0.5, dropY, z + 0.5, give.itemId(), give.count());
            }
        }
        if (r.command != null) {
            w.runCommand(x, y, z, r.command);
        }
    }

    // -- recipe viewers -------------------------------------------------------------------------------------------------

    private static String blockBase(String spec) {
        int bracket = spec.indexOf('[');
        return bracket < 0 ? spec : spec.substring(0, bracket);
    }

    private static String pretty(String idOrTag) {
        String base = blockBase(idOrTag);
        if (base.startsWith("#")) {
            return base;
        }
        String path = base.contains(":") ? base.substring(base.indexOf(':') + 1) : base;
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (!word.isEmpty()) {
                out.append(out.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private static String inputsText(WorldRecipe r) {
        List<String> parts = new ArrayList<>();
        for (WorldRecipe.Input input : r.inputs) {
            parts.add((input.count() > 1 ? input.count() + " " : "") + pretty(input.matcher()));
        }
        return String.join(" and ", parts);
    }

    /** A sentence describing the recipe, shown on recipe viewer pages. */
    public static String describe(WorldRecipe r) {
        String block = pretty(r.block);
        StringBuilder text = new StringBuilder("In the world: ");
        switch (r.trigger) {
            case RIGHT_CLICK -> text.append("right-click ").append(block).append(r.inputs.isEmpty() ? "" : " with " + inputsText(r));
            case LEFT_CLICK -> text.append("left-click ").append(block).append(r.inputs.isEmpty() ? "" : " with " + inputsText(r));
            case ITEMS_IN -> text.append("drop ").append(inputsText(r)).append(" into ").append(block);
            case ITEMS_ON -> text.append("drop ").append(inputsText(r)).append(" on ").append(block);
            case ANVIL -> text.append("let an anvil fall on ").append(block);
            case LIGHTNING -> text.append("lightning strikes ").append(block).append(r.inputs.isEmpty() ? "" : " with " + inputsText(r) + " lying nearby");
            case RANDOM_TICK -> text.append(block).append(" slowly changes by itself");
            case BLOCK_BREAK -> text.append("break ").append(block).append(r.tool == null ? "" : " with " + pretty(r.tool));
        }
        if (r.sneaking != null) {
            text.append(r.sneaking ? " while sneaking" : " without sneaking");
        }
        if (r.hand.equals("off")) {
            text.append(" (off hand)");
        }
        List<String> results = new ArrayList<>();
        if (r.turnInto != null) {
            results.add("the block becomes " + pretty(r.turnInto));
        } else if (r.breakBlock) {
            results.add("the block is removed");
        }
        for (WorldRecipe.Drop drop : r.drops) {
            results.add("drops " + (drop.count() > 1 ? drop.count() + " " : "") + pretty(drop.itemId()));
        }
        for (WorldRecipe.Drop give : r.gives) {
            results.add("gives " + (give.count() > 1 ? give.count() + " " : "") + pretty(give.itemId()));
        }
        if (r.command != null) {
            results.add("runs a command");
        }
        if (r.replaceDrops) {
            results.add("normal drops are replaced");
        }
        if (!results.isEmpty()) {
            text.append(": ").append(String.join(", ", results));
        }
        if (r.damage > 0) {
            text.append("; wears the tool by ").append(r.damage);
        }
        if (r.chance < 1.0 && r.trigger != Trigger.RANDOM_TICK) {
            text.append(" (").append(Math.round(r.chance * 100)).append("% chance)");
        }
        return text.append('.').toString();
    }

    /** Item selectors (ids and tags) of everything a recipe involves, so its sentence can be shown on those items' viewer pages. */
    public static Set<String> viewerItems(WorldRecipe r) {
        Set<String> out = new LinkedHashSet<>();
        out.add(blockBase(r.block));
        for (WorldRecipe.Input input : r.inputs) {
            out.add(input.matcher());
        }
        if (r.tool != null) {
            out.add(r.tool);
        }
        if (r.turnInto != null) {
            out.add(blockBase(r.turnInto));
        }
        for (WorldRecipe.Drop drop : r.drops) {
            out.add(drop.itemId());
        }
        for (WorldRecipe.Drop give : r.gives) {
            out.add(give.itemId());
        }
        out.remove("minecraft:air");
        return out;
    }

    /** Selector to paragraphs for every in-world recipe; recipe viewers add them as information pages. */
    public static Map<String, List<String>> viewerInformation() {
        Map<String, List<String>> info = new LinkedHashMap<>();
        for (WorldRecipe r : recipes) {
            String sentence = describe(r);
            for (String selector : viewerItems(r)) {
                List<String> list = info.computeIfAbsent(selector, k -> new ArrayList<>());
                if (!list.contains(sentence)) {
                    list.add(sentence);
                }
            }
        }
        return info;
    }
}
