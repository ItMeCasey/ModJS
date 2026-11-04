package com.itmecasey.modjs.registry;

/** A custom tool tier created by {@code StartupEvents.registry('tool_tier', ...)}; use it with {@code item.tool('pickaxe', 'modjs:ruby')}. */
public final class ToolTierBuilderJS {
    private final String id;
    private int uses = 250;
    private float speed = 6.0f;
    private float attackDamage = 2.0f;
    private String level = "iron";
    private int enchantability = 14;
    private String repair;

    ToolTierBuilderJS(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public int uses() {
        return uses;
    }

    public float speed() {
        return speed;
    }

    public float attackDamage() {
        return attackDamage;
    }

    public String level() {
        return level;
    }

    public int enchantability() {
        return enchantability;
    }

    public String repair() {
        return repair;
    }

    /** Durability. Iron is 250, diamond 1561. */
    public ToolTierBuilderJS uses(int uses) {
        this.uses = Math.max(1, uses);
        return this;
    }

    /** Mining speed. Iron is 6, diamond 8. */
    public ToolTierBuilderJS speed(double speed) {
        this.speed = (float) speed;
        return this;
    }

    /** Extra attack damage. Iron is 2, diamond 3. */
    public ToolTierBuilderJS attackDamage(double attackDamage) {
        this.attackDamage = (float) attackDamage;
        return this;
    }

    /** Which blocks drop when mined: wood, stone, iron, gold, diamond or netherite (the vanilla "incorrect for tool" tags). */
    public ToolTierBuilderJS level(String level) {
        String l = level.toLowerCase();
        if (!java.util.List.of("wood", "stone", "iron", "gold", "diamond", "netherite").contains(l)) {
            throw new IllegalArgumentException("Unknown mining level '" + level + "'; use wood, stone, iron, gold, diamond or netherite");
        }
        this.level = l;
        return this;
    }

    public ToolTierBuilderJS enchantability(int value) {
        this.enchantability = Math.max(0, value);
        return this;
    }

    /** Item id used to repair tools of this tier in an anvil. */
    public ToolTierBuilderJS repair(String itemId) {
        this.repair = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        return this;
    }
}
