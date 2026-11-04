package com.itmecasey.modjs.unify;

import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds textures in every loaded mod's assets, for {@link ColorSampler}. */
public final class ModTextureFinder {
    private ModTextureFinder() {
    }

    public static ColorSampler.Finder create() {
        ColorSampler.Finder classPath = ColorSampler.classPath();
        return path -> {
            for (IModFileInfo info : ModList.get().getModFiles()) {
                Path root = info.getFile().getFilePath();
                for (var mod : info.getMods()) {
                    String entry = "assets/" + mod.getModId() + "/" + path;
                    try {
                        if (Files.isDirectory(root)) {
                            Path file = root.resolve(entry);
                            if (Files.isRegularFile(file)) {
                                try (InputStream in = Files.newInputStream(file)) {
                                    return javax.imageio.ImageIO.read(in);
                                }
                            }
                        } else if (Files.isRegularFile(root)) {
                            try (ZipFile zip = new ZipFile(root.toFile())) {
                                ZipEntry found = zip.getEntry(entry);
                                if (found != null) {
                                    try (InputStream in = zip.getInputStream(found)) {
                                        return javax.imageio.ImageIO.read(in);
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        // try the next mod
                    }
                }
            }
            return classPath.apply(path);
        };
    }
}
