package com.itmecasey.modjs.registry;

import java.util.ArrayList;
import java.util.List;

/** Configuration for an item created by {@code StartupEvents.registry('item', ...)}. */
public final class ItemBuilderJS {
    private final String id;
    private final String namespace;
    private String displayName;
    private int maxStackSize = 64;
    private String texture;
    private String parentModel = "minecraft:item/generated";
    private String tab = "minecraft:ingredients";
    private String rarity;
    private boolean glow;
    private Integer nutrition;
    private float saturation;
    private boolean alwaysEdible;
    private String toolType;
    private String unifyTag;
    private String toolTier = "iron";
    private String armorSlot;
    private String armorTier = "iron";
    private final List<String> tooltips = new ArrayList<>();

    ItemBuilderJS(String rawId) {
        RegistryNames.Id parsed = RegistryNames.parse(rawId);
        this.namespace = parsed.namespace();
        this.id = parsed.path();
    }

    /** The path part of the id ({@code ruby} in {@code mypack:ruby}). */
    public String id() {
        return id;
    }

    /** The namespace it is registered in ({@code modjs} unless the script gave one). */
    public String namespace() {
        return namespace;
    }

    /** The full registry id, such as {@code mypack:ruby}. */
    public String fullId() {
        return namespace + ":" + id;
    }

    public String displayName() {
        return displayName != null ? displayName : RegistryNames.titleCase(id);
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public String texture() {
        return texture != null ? texture : namespace + ":item/" + id;
    }

    /** Tool kind: pickaxe, axe, shovel, hoe or sword; null for a plain item. */
    /** The tag this item is the canonical (unified) version of, or null. */
    public String unifyTag() {
        return unifyTag;
    }

    /**
     * Makes this the canonical item for a tag such as {@code "c:ingots/tin"}: it joins the tag and, once data loads,
     * replaces every other item in the tag in recipes, drops and inventories (see {@code Unify}).
     */
    public ItemBuilderJS unify(String tag) {
        String id = tag.startsWith("#") ? tag.substring(1) : tag;
        this.unifyTag = id.contains(":") ? id : "c:" + id;
        return this;
    }

    public String toolType() {
        return toolType;
    }

    /** Armor slot: helmet, chestplate, leggings or boots; null for a non-armor item. */
    public String armorSlot() {
        return armorSlot;
    }

    public String armorTier() {
        return armorTier;
    }

    /** Makes the item armor of a vanilla material: leather, chainmail, iron, gold, diamond or netherite. */
    public ItemBuilderJS armor(String slot, String tier) {
        String s = slot.toLowerCase();
        if (!java.util.List.of("helmet", "chestplate", "leggings", "boots").contains(s)) {
            throw new IllegalArgumentException("Unknown armor slot '" + slot + "'; use helmet, chestplate, leggings or boots");
        }
        String r = tier.toLowerCase();
        if (!java.util.List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite").contains(r) && !r.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")) {
            throw new IllegalArgumentException("Unknown armor tier '" + tier + "'; use leather, chainmail, iron, gold, diamond, netherite or a custom material id");
        }
        this.armorSlot = s;
        this.armorTier = r;
        this.maxStackSize = 1;
        return this;
    }

    public String toolTier() {
        return toolTier;
    }

    /** Makes the item a tool of a vanilla tier: wood, stone, iron, gold, diamond or netherite. */
    public ItemBuilderJS tool(String type, String tier) {
        String t = type.toLowerCase();
        if (!java.util.List.of("pickaxe", "axe", "shovel", "hoe", "sword").contains(t)) {
            throw new IllegalArgumentException("Unknown tool type '" + type + "'; use pickaxe, axe, shovel, hoe or sword");
        }
        String r = tier.toLowerCase();
        if (!java.util.List.of("wood", "stone", "iron", "gold", "diamond", "netherite").contains(r) && !r.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")) {
            throw new IllegalArgumentException("Unknown tool tier '" + tier + "'; use wood, stone, iron, gold, diamond, netherite or a custom tier id");
        }
        this.toolType = t;
        this.toolTier = r;
        this.parentModel = "minecraft:item/handheld";
        return this;
    }

    public String parentModel() {
        return parentModel;
    }

    public String tab() {
        return tab;
    }

    public String rarity() {
        return rarity;
    }

    public boolean glow() {
        return glow;
    }

    public Integer nutrition() {
        return nutrition;
    }

    public float saturation() {
        return saturation;
    }

    public boolean isAlwaysEdible() {
        return alwaysEdible;
    }

    public List<String> tooltips() {
        return tooltips;
    }

    public ItemBuilderJS displayName(String name) {
        this.displayName = name;
        return this;
    }

    public ItemBuilderJS maxStackSize(int size) {
        this.maxStackSize = Math.max(1, Math.min(99, size));
        return this;
    }

    public ItemBuilderJS unstackable() {
        return maxStackSize(1);
    }

    /** Texture id such as {@code "mymod:item/ruby"}; defaults to {@code <namespace>:item/<id>}. */
    public ItemBuilderJS texture(String texture) {
        this.texture = texture;
        return this;
    }

    public ItemBuilderJS parentModel(String model) {
        this.parentModel = model;
        return this;
    }

    /** Creative tab id, for example {@code "minecraft:tools_and_utilities"}. */
    public ItemBuilderJS tab(String tabId) {
        this.tab = tabId.contains(":") ? tabId : "minecraft:" + tabId;
        return this;
    }

    /** One of {@code common}, {@code uncommon}, {@code rare}, {@code epic}. */
    public ItemBuilderJS rarity(String rarity) {
        this.rarity = rarity;
        return this;
    }

    public ItemBuilderJS glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemBuilderJS food(int nutrition, double saturation) {
        this.nutrition = nutrition;
        this.saturation = (float) saturation;
        return this;
    }

    public ItemBuilderJS alwaysEdible() {
        this.alwaysEdible = true;
        return this;
    }

    public ItemBuilderJS tooltip(String line) {
        tooltips.add(line);
        return this;
    }
}
