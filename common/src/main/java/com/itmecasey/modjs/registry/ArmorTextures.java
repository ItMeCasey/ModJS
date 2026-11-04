package com.itmecasey.modjs.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.itmecasey.modjs.pack.GeneratedData;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Worn-armor art for custom materials: the vanilla iron armor, recolored, or a plain fill when the game assets are not on hand. */
final class ArmorTextures {
    private static final String NS = RegistryManager.NAMESPACE;

    private ArmorTextures() {
    }

    static void write(GeneratedData data, ArmorMaterialBuilderJS material, boolean modern) {
        String id = material.id();
        String outer = modern ? "textures/entity/equipment/humanoid/" + id + ".png" : "textures/models/armor/" + id + "_layer_1.png";
        String inner = modern ? "textures/entity/equipment/humanoid_leggings/" + id + ".png" : "textures/models/armor/" + id + "_layer_2.png";
        String vanillaOuter = modern ? "assets/minecraft/textures/entity/equipment/humanoid/iron.png" : "assets/minecraft/textures/models/armor/iron_layer_1.png";
        String vanillaInner = modern ? "assets/minecraft/textures/entity/equipment/humanoid_leggings/iron.png" : "assets/minecraft/textures/models/armor/iron_layer_2.png";
        if (!data.has(NS, outer)) {
            data.putBytes(NS, outer, tinted(vanillaOuter, material.color()));
        }
        if (!data.has(NS, inner)) {
            data.putBytes(NS, inner, tinted(vanillaInner, material.color()));
        }
        if (modern) {
            JsonObject layers = new JsonObject();
            for (String layer : new String[] {"humanoid", "humanoid_leggings"}) {
                JsonObject texture = new JsonObject();
                texture.addProperty("texture", NS + ":" + id);
                JsonArray list = new JsonArray();
                list.add(texture);
                layers.add(layer, list);
            }
            JsonObject root = new JsonObject();
            root.add("layers", layers);
            if (!data.has(NS, "equipment/" + id + ".json")) {
                data.putJson(NS, "equipment/" + id, root);
            }
        }
    }

    private static byte[] tinted(String resource, int rgb) {
        BufferedImage source = null;
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), ArmorTextures.class.getClassLoader()}) {
            if (loader == null || source != null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in != null) {
                    source = javax.imageio.ImageIO.read(in);
                }
            } catch (IOException e) {
                // fall back to a plain fill
            }
        }
        BufferedImage out = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int argb = source != null && x < source.getWidth() && y < source.getHeight() ? source.getRGB(x, y) : 0xFF000000 | rgb;
                int alpha = argb >>> 24;
                if (alpha == 0) {
                    continue;
                }
                // vanilla iron armor is roughly 0xB4B4B4 on average; scale each channel so that maps to the material color
                int r = Math.min(255, ((argb >> 16) & 0xFF) * ((rgb >> 16) & 0xFF) / 180);
                int g = Math.min(255, ((argb >> 8) & 0xFF) * ((rgb >> 8) & 0xFF) / 180);
                int bl = Math.min(255, (argb & 0xFF) * (rgb & 0xFF) / 180);
                out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | bl);
            }
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
