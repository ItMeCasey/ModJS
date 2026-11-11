package com.itmecasey.modjs.registry;

import com.google.gson.JsonObject;
import com.itmecasey.modjs.pack.GeneratedData;

/**
 * Writes the blockstate and block model JSON of a plain block (not a slab, stairs, wall or fence): one texture on all faces,
 * a texture per face, a column, an orientable block that turns to face the player, or a model file the script supplies.
 */
final class BlockModels {
    private static final String NS = RegistryManager.NAMESPACE;
    private static final String[][] FACING = {{"north", "0"}, {"east", "90"}, {"south", "180"}, {"west", "270"}};

    private BlockModels() {
    }

    /** The model id a block with a script-supplied model points at, with the default namespace filled in. */
    static String customModelId(BlockBuilderJS block) {
        String model = block.modelName();
        return model.contains(":") ? model : NS + ":" + model;
    }

    static void write(GeneratedData data, BlockBuilderJS block) {
        String id = block.id();
        String target = block.hasCustomModel() ? customModelId(block) : NS + ":block/" + id;
        JsonObject variants = new JsonObject();
        if (block.isOrientable()) {
            for (String[] facing : FACING) {
                variants.add("facing=" + facing[0], variant(target, Integer.parseInt(facing[1])));
            }
        } else {
            variants.add("", variant(target, 0));
        }
        JsonObject state = new JsonObject();
        state.add("variants", variants);
        data.putJson(NS, "blockstates/" + id, state);
        if (!block.hasCustomModel()) {
            data.putJson(NS, "models/block/" + id, model(block));
        }
    }

    private static JsonObject variant(String model, int yRotation) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", model);
        if (yRotation != 0) {
            variant.addProperty("y", yRotation);
        }
        return variant;
    }

    /** The model JSON for the block's {@link BlockBuilderJS#modelName()} (not for a script-supplied model). */
    static JsonObject model(BlockBuilderJS block) {
        JsonObject textures = new JsonObject();
        String parent;
        switch (block.modelName()) {
            case "cube" -> {
                parent = "minecraft:block/cube";
                textures.addProperty("particle", block.faceTexture("north"));
                textures.addProperty("up", block.faceTexture("up"));
                textures.addProperty("down", block.faceTexture("down"));
                textures.addProperty("north", block.faceTexture("north"));
                textures.addProperty("south", block.faceTexture("south"));
                textures.addProperty("east", block.faceTexture("east"));
                textures.addProperty("west", block.faceTexture("west"));
            }
            case "cube_column" -> {
                parent = "minecraft:block/cube_column";
                textures.addProperty("end", block.faceTexture("up"));
                textures.addProperty("side", block.faceTexture("north"));
            }
            case "cube_bottom_top" -> {
                parent = "minecraft:block/cube_bottom_top";
                textures.addProperty("top", block.faceTexture("up"));
                textures.addProperty("bottom", block.faceTexture("down"));
                textures.addProperty("side", block.faceTexture("east"));
            }
            case "orientable" -> {
                parent = "minecraft:block/orientable_with_bottom";
                textures.addProperty("top", block.faceTexture("up"));
                textures.addProperty("bottom", block.faceTexture("down"));
                textures.addProperty("front", block.faceTexture("north"));
                textures.addProperty("side", block.faceTexture("east"));
            }
            default -> {
                parent = "minecraft:block/cube_all";
                textures.addProperty("all", block.faceTexture("north"));
            }
        }
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        model.add("textures", textures);
        return model;
    }
}
