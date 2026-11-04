package com.itmecasey.modjs.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.pack.GeneratedData;

/** Writes the blockstate and model JSON for the shaped block types (slab, stairs, wall, fence). */
final class BlockShapes {
    private static final String NS = RegistryManager.NAMESPACE;

    private BlockShapes() {
    }

    /** The model the block item uses. */
    static String itemModel(BlockBuilderJS block) {
        return switch (block.type()) {
            case "wall", "fence" -> NS + ":block/" + block.id() + "_inventory";
            default -> NS + ":block/" + block.id();
        };
    }

    static void write(GeneratedData data, BlockBuilderJS block) {
        String id = block.id();
        String tex = block.texture();
        switch (block.type()) {
            case "slab" -> {
                model(data, id, "minecraft:block/slab", tex, "bottom", "top", "side");
                model(data, id + "_top", "minecraft:block/slab_top", tex, "bottom", "top", "side");
                model(data, id + "_double", "minecraft:block/cube_all", tex, "all");
                JsonObject variants = new JsonObject();
                variants.add("type=bottom", variant(NS + ":block/" + id, 0, 0, false));
                variants.add("type=top", variant(NS + ":block/" + id + "_top", 0, 0, false));
                variants.add("type=double", variant(NS + ":block/" + id + "_double", 0, 0, false));
                state(data, id, "variants", variants);
            }
            case "stairs" -> {
                model(data, id, "minecraft:block/stairs", tex, "bottom", "top", "side");
                model(data, id + "_inner", "minecraft:block/inner_stairs", tex, "bottom", "top", "side");
                model(data, id + "_outer", "minecraft:block/outer_stairs", tex, "bottom", "top", "side");
                JsonObject variants = new JsonObject();
                String[] facings = {"east", "south", "west", "north"};
                String[] shapes = {"straight", "outer_right", "outer_left", "inner_right", "inner_left"};
                for (String half : new String[] {"bottom", "top"}) {
                    for (int f = 0; f < 4; f++) {
                        for (String shape : shapes) {
                            int y = f * 90;
                            if (half.equals("bottom")) {
                                y += shape.endsWith("left") ? 270 : 0;
                            } else {
                                y += shape.endsWith("right") ? 90 : 0;
                            }
                            y %= 360;
                            String model = shape.startsWith("inner") ? id + "_inner" : shape.startsWith("outer") ? id + "_outer" : id;
                            int x = half.equals("top") ? 180 : 0;
                            variants.add("facing=" + facings[f] + ",half=" + half + ",shape=" + shape,
                                variant(NS + ":block/" + model, x, y, x != 0 || y != 0));
                        }
                    }
                }
                state(data, id, "variants", variants);
            }
            case "wall" -> {
                model(data, id + "_post", "minecraft:block/template_wall_post", tex, "wall");
                model(data, id + "_side", "minecraft:block/template_wall_side", tex, "wall");
                model(data, id + "_side_tall", "minecraft:block/template_wall_side_tall", tex, "wall");
                model(data, id + "_inventory", "minecraft:block/wall_inventory", tex, "wall");
                JsonArray parts = new JsonArray();
                JsonObject up = new JsonObject();
                up.addProperty("up", "true");
                parts.add(part(up, variant(NS + ":block/" + id + "_post", 0, 0, false)));
                String[] dirs = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    for (String height : new String[] {"low", "tall"}) {
                        JsonObject when = new JsonObject();
                        when.addProperty(dirs[i], height);
                        String side = height.equals("low") ? "_side" : "_side_tall";
                        parts.add(part(when, variant(NS + ":block/" + id + side, 0, i * 90, true)));
                    }
                }
                state(data, id, "multipart", parts);
            }
            case "fence" -> {
                model(data, id + "_post", "minecraft:block/fence_post", tex, "texture");
                model(data, id + "_side", "minecraft:block/fence_side", tex, "texture");
                model(data, id + "_inventory", "minecraft:block/fence_inventory", tex, "texture");
                JsonArray parts = new JsonArray();
                parts.add(part(null, variant(NS + ":block/" + id + "_post", 0, 0, false)));
                String[] dirs = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    JsonObject when = new JsonObject();
                    when.addProperty(dirs[i], "true");
                    parts.add(part(when, variant(NS + ":block/" + id + "_side", 0, i * 90, true)));
                }
                state(data, id, "multipart", parts);
            }
            default -> throw new IllegalStateException(block.type());
        }
    }

    private static void model(GeneratedData data, String name, String parent, String texture, String... keys) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        JsonObject textures = new JsonObject();
        for (String key : keys) {
            textures.addProperty(key, texture);
        }
        model.add("textures", textures);
        data.putJson(NS, "models/block/" + name, model);
    }

    private static JsonObject variant(String model, int x, int y, boolean uvlock) {
        JsonObject out = new JsonObject();
        out.addProperty("model", model);
        if (x != 0) {
            out.addProperty("x", x);
        }
        if (y != 0) {
            out.addProperty("y", y);
        }
        if (uvlock) {
            out.addProperty("uvlock", true);
        }
        return out;
    }

    private static JsonObject part(JsonObject when, JsonObject apply) {
        JsonObject out = new JsonObject();
        if (when != null) {
            out.add("when", when);
        }
        out.add("apply", apply);
        return out;
    }

    private static void state(GeneratedData data, String id, String key, JsonElement value) {
        JsonObject state = new JsonObject();
        state.add(key, value);
        data.putJson(NS, "blockstates/" + id, state);
    }
}
