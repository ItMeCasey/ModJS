package com.itmecasey.modjs.registry;

import com.itmecasey.modjs.pack.GeneratedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/** The skin of a custom profession: a copy of a vanilla profession skin, unless the pack already has a texture for it. */
final class ProfessionAssets {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");

    private ProfessionAssets() {
    }

    private static byte[] read(String resource) {
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), ProfessionAssets.class.getClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try (InputStream in = loader.getResourceAsStream(resource)) {
                if (in != null) {
                    return in.readAllBytes();
                }
            } catch (IOException e) {
                LOGGER.warn("Could not read {}", resource, e);
            }
        }
        return null;
    }

    static void copyTexture(GeneratedData data, ProfessionBuilderJS profession) {
        String target = "textures/entity/villager/profession/" + profession.id() + ".png";
        if (data.has(profession.namespace(), target)) {
            return;
        }
        String base = profession.textureBase();
        int colon = base.indexOf(':');
        String source = "assets/" + base.substring(0, colon) + "/textures/entity/villager/profession/" + base.substring(colon + 1) + ".png";
        byte[] png = read(source);
        if (png == null) {
            LOGGER.warn("[villagers] the skin {} of {} was not found; the villager will show the missing texture", source, profession.fullId());
            return;
        }
        data.putBytes(profession.namespace(), target, png);
        byte[] meta = read(source + ".mcmeta");
        if (meta != null) {
            data.putBytes(profession.namespace(), target + ".mcmeta", meta);
        }
    }
}
