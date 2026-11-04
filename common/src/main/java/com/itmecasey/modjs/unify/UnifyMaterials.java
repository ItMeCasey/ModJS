package com.itmecasey.modjs.unify;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The built-in material and kind tables behind {@code Unify.materials}, including generated placeholder textures. */
public final class UnifyMaterials {
    /** One thing to register: an item or a block with its tag. */
    public record Entry(String id, String kind, String material, String tag, String displayName, int color, boolean block) {
    }

    /** What a {@code Unify.materials} call asked for. */
    public record Spec(List<String> materials, List<String> kinds, boolean includeVanilla) {
    }

    private record Kind(String name, String folder, String itemSuffix, String itemPrefix, String label, boolean block) {
        String id(String material) {
            return itemPrefix + material + itemSuffix;
        }
    }

    private static final Map<String, Kind> KINDS = new LinkedHashMap<>();
    private static final Map<String, Integer> COLORS = new LinkedHashMap<>();
    private static final List<String> METALS = List.of("iron", "gold", "copper", "tin", "zinc", "lead", "silver", "nickel", "aluminum",
        "steel", "bronze", "brass", "osmium", "uranium", "platinum", "cobalt", "titanium", "tungsten", "invar", "electrum",
        "constantan", "netherite");
    private static final List<String> GEMS = List.of("ruby", "sapphire", "topaz", "peridot", "diamond", "emerald", "lapis");
    private static final List<String> METAL_KINDS = List.of("ingot", "nugget", "dust", "plate", "gear", "rod", "wire", "coin", "raw", "storage_block");
    private static final List<String> GEM_KINDS = List.of("gem", "dust", "storage_block");

    /** Items Minecraft itself has, as {@code kind:material}; skipped unless {@code includeVanilla} is set. */
    private static final Set<String> VANILLA = Set.of(
        "ingot:iron", "nugget:iron", "raw:iron", "storage_block:iron",
        "ingot:gold", "nugget:gold", "raw:gold", "storage_block:gold",
        "ingot:copper", "raw:copper", "storage_block:copper",
        "ingot:netherite", "storage_block:netherite",
        "gem:diamond", "storage_block:diamond", "gem:emerald", "storage_block:emerald", "gem:lapis", "storage_block:lapis");

    static {
        kind("ingot", "ingots", "", "", "_ingot", "Ingot", false);
        kind("nugget", "nuggets", "", "", "_nugget", "Nugget", false);
        kind("dust", "dusts", "", "", "_dust", "Dust", false);
        kind("plate", "plates", "", "", "_plate", "Plate", false);
        kind("gear", "gears", "", "", "_gear", "Gear", false);
        kind("rod", "rods", "", "", "_rod", "Rod", false);
        kind("wire", "wires", "", "", "_wire", "Wire", false);
        kind("coin", "coins", "", "", "_coin", "Coin", false);
        kind("gem", "gems", "", "", "_gem", "Gem", false);
        kind("raw", "raw_materials", "", "raw_", "", "Raw", false);
        kind("storage_block", "storage_blocks", "", "", "_block", "Block", true);
        color("iron", 0xD8D8D8);
        color("gold", 0xFCEE4B);
        color("copper", 0xE77C56);
        color("tin", 0xC9D3D9);
        color("zinc", 0xB7C4C0);
        color("lead", 0x4B4F6B);
        color("silver", 0xC8D8E8);
        color("nickel", 0xB8B596);
        color("aluminum", 0xD7DDE3);
        color("steel", 0x6C7380);
        color("bronze", 0xCD8F46);
        color("brass", 0xD9B64A);
        color("osmium", 0x8FB8D6);
        color("uranium", 0x6BC04A);
        color("platinum", 0xE6E6F0);
        color("cobalt", 0x2D5DA8);
        color("titanium", 0x8C96A6);
        color("tungsten", 0x5D5F66);
        color("invar", 0xA9B2A5);
        color("electrum", 0xE8D883);
        color("constantan", 0xC98F6B);
        color("netherite", 0x4A3F46);
        color("ruby", 0xD81E3C);
        color("sapphire", 0x2F5BE0);
        color("topaz", 0xF2A93B);
        color("peridot", 0x9BD13D);
        color("diamond", 0x5FE8E0);
        color("emerald", 0x2FD36B);
        color("lapis", 0x2744B8);
    }

    private UnifyMaterials() {
    }

    private static void kind(String name, String folder, String ignored, String prefix, String suffix, String label, boolean block) {
        KINDS.put(name, new Kind(name, folder, suffix, prefix, label, block));
    }

    private static void color(String material, int rgb) {
        COLORS.put(material, rgb);
    }

    /** The {@code c:} tag folders the built-in kinds use, for {@code Unify.auto('all')}. */
    public static List<String> categories() {
        List<String> out = new ArrayList<>();
        KINDS.values().forEach(k -> out.add(k.folder()));
        out.add("ores");
        return out;
    }

    /** Everything a spec asks for, minus vanilla items unless allowed. */
    public static List<Entry> expand(Spec spec) {
        List<String> materials = spec.materials().isEmpty() ? defaultMaterials() : spec.materials();
        List<Entry> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : materials) {
            String material = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
            boolean gem = GEMS.contains(material);
            List<String> kinds = spec.kinds().isEmpty() ? (gem ? GEM_KINDS : METAL_KINDS) : spec.kinds();
            for (String kindName : kinds) {
                Kind kind = KINDS.get(kindName);
                if (kind == null) {
                    throw new IllegalArgumentException("Unknown material kind '" + kindName + "'; use one of " + KINDS.keySet());
                }
                if (!spec.includeVanilla() && VANILLA.contains(kindName + ":" + material)) {
                    continue;
                }
                String id = kind.id(material);
                if (seen.add(id)) {
                    String tag = "c:" + kind.folder() + "/" + material;
                    String name = title(material) + " " + (kind.label().equals("Raw") ? "(Raw)" : kind.label());
                    if (kind.label().equals("Raw")) {
                        name = "Raw " + title(material);
                    } else if (kind.name().equals("gem")) {
                        name = title(material);
                    }
                    out.add(new Entry(id, kindName, material, tag, name, COLORS.getOrDefault(material, hash(material)), kind.block()));
                }
            }
        }
        return out;
    }

    private static List<String> defaultMaterials() {
        List<String> all = new ArrayList<>(METALS);
        all.addAll(GEMS);
        return all;
    }

    private static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return out.toString().trim();
    }

    private static int hash(String material) {
        int h = material.hashCode();
        return 0x606060 | (h & 0x9F9F9F);
    }

    // -- generated placeholder art ---------------------------------------------------------------------------------

    /** A 16x16 PNG for the kind in the given color; replaced by any texture the pack provides. */
    public static byte[] texture(String kind, int rgb) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color base = new Color(rgb);
        Color light = base.brighter();
        Color dark = base.darker().darker();
        switch (kind) {
            case "ingot" -> {
                for (int y = 5; y <= 11; y++) {
                    int inset = y == 5 || y == 11 ? 2 : 0;
                    for (int x = 2 + inset; x <= 13 - inset; x++) {
                        image.setRGB(x, y, y <= 6 ? light.getRGB() : y >= 10 ? dark.getRGB() : base.getRGB());
                    }
                }
            }
            case "nugget" -> {
                circle(image, 8, 8, 3, base, light, dark);
            }
            case "dust" -> {
                for (int y = 6; y <= 12; y++) {
                    int half = (y - 5);
                    for (int x = 8 - half; x <= 7 + half; x++) {
                        if (x >= 1 && x <= 14) {
                            image.setRGB(x, y, ((x + y) % 3 == 0 ? light : (x + y) % 3 == 1 ? base : dark).getRGB());
                        }
                    }
                }
            }
            case "plate" -> {
                for (int y = 4; y <= 12; y++) {
                    for (int x = 3; x <= 12; x++) {
                        boolean edge = y == 4 || x == 3;
                        boolean shade = y == 12 || x == 12;
                        image.setRGB(x, y, (edge ? light : shade ? dark : base).getRGB());
                    }
                }
            }
            case "gear" -> {
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        double d = Math.hypot(x - 7.5, y - 7.5);
                        double angle = Math.atan2(y - 7.5, x - 7.5);
                        boolean tooth = d <= 7 && d >= 5 && Math.sin(angle * 4) > 0;
                        if ((d <= 5 && d >= 2.5) || tooth) {
                            image.setRGB(x, y, (d > 4.5 ? dark : base).getRGB());
                        }
                    }
                }
            }
            case "rod" -> {
                for (int i = 2; i <= 13; i++) {
                    image.setRGB(i, 15 - i, base.getRGB());
                    image.setRGB(i, 14 - i, light.getRGB());
                    image.setRGB(Math.min(15, i + 1), 15 - i, dark.getRGB());
                }
            }
            case "wire" -> {
                for (int x = 1; x <= 14; x++) {
                    int y = 8 + (int) Math.round(3 * Math.sin(x / 2.0));
                    image.setRGB(x, y, base.getRGB());
                    image.setRGB(x, y + 1, dark.getRGB());
                }
            }
            case "coin" -> circle(image, 8, 8, 5, base, light, dark);
            case "gem" -> {
                for (int y = 2; y <= 13; y++) {
                    int half = y <= 7 ? y - 2 : 13 - y;
                    for (int x = 8 - half - 1; x <= 8 + half; x++) {
                        image.setRGB(x, y, (x < 8 ? light : dark).getRGB());
                    }
                }
            }
            case "raw" -> {
                for (int y = 4; y <= 12; y++) {
                    for (int x = 3; x <= 12; x++) {
                        double d = Math.hypot(x - 7.5, y - 8);
                        if (d < 4.5 + ((x * 7 + y * 3) % 3) * 0.4) {
                            image.setRGB(x, y, (((x + y) % 4 == 0) ? light : ((x * y) % 5 == 0 ? dark : base)).getRGB());
                        }
                    }
                }
            }
            default -> { // storage_block
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        boolean border = x == 0 || y == 0 || x == 15 || y == 15;
                        Color c = border ? dark : ((x * 3 + y * 5) % 7 == 0 ? light : base);
                        image.setRGB(x, y, c.getRGB());
                    }
                }
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void circle(BufferedImage image, int cx, int cy, int radius, Color base, Color light, Color dark) {
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d <= radius) {
                    image.setRGB(x, y, (x + y < cx + cy - radius / 2 ? light : x + y > cx + cy + radius / 2 ? dark : base).getRGB());
                }
            }
        }
    }
}
