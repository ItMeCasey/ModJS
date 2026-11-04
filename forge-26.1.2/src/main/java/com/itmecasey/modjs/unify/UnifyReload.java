package com.itmecasey.modjs.unify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Works out the unified items from the item tag files of every loaded pack. Runs once per data reload. */
public final class UnifyReload {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final FileToIdConverter TAG_FILES = FileToIdConverter.json("tags/item");
    private static final FileToIdConverter FLUID_TAG_FILES = FileToIdConverter.json("tags/fluid");

    private UnifyReload() {
    }

    private static Map<String, List<JsonObject>> readTags(ResourceManager resources, FileToIdConverter converter) {
        Map<String, List<JsonObject>> files = new HashMap<>();
        for (Map.Entry<Identifier, List<Resource>> entry : converter.listMatchingResourceStacks(resources).entrySet()) {
            Identifier id = converter.fileToId(entry.getKey());
            List<JsonObject> list = new ArrayList<>();
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject()) {
                        list.add(element.getAsJsonObject());
                    }
                } catch (IOException | RuntimeException e) {
                    LOGGER.debug("ModJS could not read tag file {}", entry.getKey(), e);
                }
            }
            files.put(id.toString(), list);
        }
        return files;
    }

    /** Builds and installs the {@link Unifier} for the current data. */
    public static Unifier prepare(ResourceManager resources) {
        Unifier unifier = Unifier.build(new TagIndex(readTags(resources, TAG_FILES)), item -> {
            Identifier id = Identifier.tryParse(item);
            return id != null && BuiltInRegistries.ITEM.containsKey(id);
        }, new TagIndex(readTags(resources, FLUID_TAG_FILES)), fluid -> {
            Identifier id = Identifier.tryParse(fluid);
            return id != null && BuiltInRegistries.FLUID.containsKey(id);
        });
        Unifier.set(unifier);
        if (unifier.active()) {
            LOGGER.info("ModJS unified {} item group(s) and {} fluid group(s)", unifier.groups().size(), unifier.fluidGroups().size());
        }
        return unifier;
    }
}
