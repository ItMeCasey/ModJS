package com.itmecasey.modjs.unify;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Finds textures in every loaded mod's assets, for {@link ColorSampler}. */
public final class ModTextureFinder {
    private ModTextureFinder() {
    }

    public static ColorSampler.Finder create() {
        ColorSampler.Finder classPath = ColorSampler.classPath();
        return path -> {
            String[] parts = path.split("/");
            for (IModFileInfo info : ModList.getModFiles()) {
                for (IModInfo mod : info.getMods()) {
                    String[] full = new String[parts.length + 2];
                    full[0] = "assets";
                    full[1] = mod.getModId();
                    System.arraycopy(parts, 0, full, 2, parts.length);
                    Path file = info.getFile().findResource(full);
                    if (Files.isRegularFile(file)) {
                        try (InputStream in = Files.newInputStream(file)) {
                            return javax.imageio.ImageIO.read(in);
                        } catch (IOException e) {
                            // try the next mod
                        }
                    }
                }
            }
            return classPath.apply(path);
        };
    }
}
