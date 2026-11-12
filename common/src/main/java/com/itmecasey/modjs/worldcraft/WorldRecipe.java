package com.itmecasey.modjs.worldcraft;

import com.itmecasey.modjs.json.JsonUtil;
import org.mozilla.javascript.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * One in-world recipe made with {@code ServerEvents.worldRecipes}. The methods return the recipe so they chain:
 * {@code event.itemOnBlock('minecraft:stone', 'minecraft:flint').turnInto('minecraft:cobblestone').drop('minecraft:gravel', 2)}.
 */
public final class WorldRecipe {
    /** What starts a recipe. */
    public enum Trigger {
        RIGHT_CLICK("item on block"), LEFT_CLICK("left click"), ITEMS_IN("items in block"), ITEMS_ON("items on block"),
        ANVIL("anvil"), LIGHTNING("lightning"), RANDOM_TICK("random tick"), BLOCK_BREAK("block break");

        final String label;

        Trigger(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** An item the recipe needs: an item id or a {@code #tag}, and how many. */
    public record Input(String matcher, int count) {
    }

    /** An item the recipe produces. */
    public record Drop(String itemId, int count) {
    }

    final Trigger trigger;
    final String block;
    final List<Input> inputs = new ArrayList<>();
    String recipeId;
    Boolean sneaking;
    String hand = "main";
    Integer consume;
    int damage;
    double chance = 1.0;
    String turnInto;
    boolean breakBlock;
    boolean replaceDrops;
    String tool;
    String command;
    final List<Drop> drops = new ArrayList<>();
    final List<Drop> gives = new ArrayList<>();

    WorldRecipe(Trigger trigger, String block) {
        this.trigger = trigger;
        this.block = block;
    }

    // -- script API -------------------------------------------------------------------------------------------------

    /** A name for the recipe, shown in logs and recipe viewer pages. */
    public WorldRecipe id(String id) {
        this.recipeId = id;
        return this;
    }

    /** {@code true} needs the player to sneak, {@code false} not to sneak. Default: either. */
    public WorldRecipe sneaking(boolean sneaking) {
        this.sneaking = sneaking;
        return this;
    }

    /** Which hand holds the item for a click: {@code main} (default), {@code off} or {@code any}. */
    public WorldRecipe hand(String hand) {
        if (!List.of("main", "off", "any").contains(hand)) {
            throw Context.reportRuntimeError("worldRecipes: hand must be main, off or any, not '" + hand + "'");
        }
        this.hand = hand;
        return this;
    }

    /** How many of the held item are used up (default: as many as the recipe's item needs); 0 keeps it, also for dropped items. Creative players keep their items. */
    public WorldRecipe consume(int count) {
        this.consume = Math.max(0, count);
        return this;
    }

    /** Wears the held tool down by this much. */
    public WorldRecipe damage(int amount) {
        this.damage = Math.max(0, amount);
        return this;
    }

    /** Chance (0 to 1) that the results happen; the items are used up either way. */
    public WorldRecipe chance(double chance) {
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        return this;
    }

    /** Replaces the block with another block, for example {@code 'minecraft:oak_stairs[facing=north]'}. */
    public WorldRecipe turnInto(String block) {
        this.turnInto = block;
        return this;
    }

    /** Removes the block without drops. */
    public WorldRecipe breakBlock() {
        this.breakBlock = true;
        return this;
    }

    /** Drops an item at the block. */
    public WorldRecipe drop(String itemId) {
        return drop(itemId, 1);
    }

    public WorldRecipe drop(String itemId, int count) {
        drops.add(new Drop(withNamespace(itemId), Math.max(1, count)));
        return this;
    }

    /** Gives an item to the player (it drops at their feet when the inventory is full); drops it at the block without a player. */
    public WorldRecipe give(String itemId) {
        return give(itemId, 1);
    }

    public WorldRecipe give(String itemId, int count) {
        gives.add(new Drop(withNamespace(itemId), Math.max(1, count)));
        return this;
    }

    /** Runs a server command at the block, for example {@code 'particle minecraft:flame ~ ~1 ~'}. */
    public WorldRecipe command(String command) {
        this.command = command;
        return this;
    }

    /** For {@code blockBreak}: the tool the player must hold (an item id or {@code #tag}). */
    public WorldRecipe tool(String matcher) {
        this.tool = matcher;
        return this;
    }

    /** For {@code blockBreak}: the block drops only what this recipe says (and {@code turnInto}/{@code breakBlock} apply), not its normal drops. */
    public WorldRecipe replaceDrops() {
        this.replaceDrops = true;
        return this;
    }

    /** An item the lightning or the item-based recipe needs besides the block: {@code .item('minecraft:sand', 2)}. */
    public WorldRecipe item(String matcher, int count) {
        inputs.add(new Input(withNamespace(matcher), Math.max(1, count)));
        return this;
    }

    public WorldRecipe item(String matcher) {
        return item(matcher, 1);
    }

    // -- reading ----------------------------------------------------------------------------------------------------

    public Trigger trigger() {
        return trigger;
    }

    public String block() {
        return block;
    }

    public List<Input> inputs() {
        return inputs;
    }

    public String recipeId() {
        return recipeId;
    }

    public String turnIntoBlock() {
        return turnInto;
    }

    public List<Drop> drops() {
        return drops;
    }

    public List<Drop> gives() {
        return gives;
    }

    static String withNamespace(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("#")) {
            return id.contains(":") ? id : "#minecraft:" + id.substring(1);
        }
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** Reads {@code 'minecraft:flint'}, {@code '#c:ingots'}, {@code { item: 'minecraft:flint', count: 2 }} or {@code 'minecraft:flint x2'}. */
    static Input parseInput(Object raw) {
        Object value = JsonUtil.unwrap(raw);
        String text = JsonUtil.asString(value);
        int count = 1;
        if (text == null) {
            Object item = JsonUtil.property(value, "item");
            if (item == null) {
                item = JsonUtil.property(value, "tag");
            }
            text = JsonUtil.asString(item);
            Object c = JsonUtil.property(value, "count");
            if (c instanceof Number n) {
                count = n.intValue();
            }
        } else {
            int x = text.lastIndexOf(" x");
            if (x > 0) {
                try {
                    count = Integer.parseInt(text.substring(x + 2).trim());
                    text = text.substring(0, x);
                } catch (NumberFormatException ignored) {
                    // the id itself contains " x"; leave it
                }
            }
        }
        if (text == null || text.isEmpty()) {
            throw Context.reportRuntimeError("worldRecipes: an item must be an id such as 'minecraft:flint', a '#tag', or { item, count }");
        }
        return new Input(withNamespace(text), Math.max(1, count));
    }
}
