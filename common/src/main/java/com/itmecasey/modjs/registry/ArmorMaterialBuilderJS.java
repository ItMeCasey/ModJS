package com.itmecasey.modjs.registry;

/** A custom armor material created by {@code StartupEvents.registry('armor_material', ...)}; use it with {@code item.armor('helmet', 'modjs:ruby')}. */
public final class ArmorMaterialBuilderJS {
    private final String id;
    private int durability = 15;
    private int helmet = 2;
    private int chestplate = 6;
    private int leggings = 5;
    private int boots = 2;
    private float toughness;
    private float knockbackResistance;
    private int enchantability = 9;
    private String repair;
    private int color = 0xB4B4B4;

    ArmorMaterialBuilderJS(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public int durability() {
        return durability;
    }

    public int defense(String slot) {
        return switch (slot) {
            case "helmet" -> helmet;
            case "chestplate" -> chestplate;
            case "leggings" -> leggings;
            default -> boots;
        };
    }

    public float toughness() {
        return toughness;
    }

    public float knockbackResistance() {
        return knockbackResistance;
    }

    public int enchantability() {
        return enchantability;
    }

    public String repair() {
        return repair;
    }

    public int color() {
        return color;
    }

    /** Durability multiplier: iron 15, diamond 33, netherite 37. */
    public ArmorMaterialBuilderJS durability(int multiplier) {
        this.durability = Math.max(1, multiplier);
        return this;
    }

    /** Armor points for each piece. Iron is 2, 6, 5, 2; diamond 3, 8, 6, 3. */
    public ArmorMaterialBuilderJS defense(int helmet, int chestplate, int leggings, int boots) {
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        return this;
    }

    public ArmorMaterialBuilderJS toughness(double toughness) {
        this.toughness = (float) toughness;
        return this;
    }

    public ArmorMaterialBuilderJS knockbackResistance(double value) {
        this.knockbackResistance = (float) value;
        return this;
    }

    public ArmorMaterialBuilderJS enchantability(int value) {
        this.enchantability = Math.max(0, value);
        return this;
    }

    /** Item id used to repair armor of this material in an anvil. */
    public ArmorMaterialBuilderJS repair(String itemId) {
        this.repair = itemId.contains(":") ? itemId : "minecraft:" + itemId;
        return this;
    }

    /** Tint for the generated worn-armor texture (the vanilla iron armor, recolored). Put your own textures in assets to replace it. */
    public ArmorMaterialBuilderJS color(int rgb) {
        this.color = rgb;
        return this;
    }
}
