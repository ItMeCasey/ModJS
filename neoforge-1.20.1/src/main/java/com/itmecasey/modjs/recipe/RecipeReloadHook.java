package com.itmecasey.modjs.recipe;

import com.google.gson.JsonElement;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link RecipeChanges} to the recipes the game just loaded. Listeners added through
 * {@link AddReloadListenerEvent} apply after the vanilla {@link RecipeManager}. Filters run on each recipe's
 * source JSON, read from the resource manager, so they work for every recipe type.
 */
public final class RecipeReloadHook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FileToIdConverter RECIPE_FILES = FileToIdConverter.json(com.itmecasey.modjs.format.DataFolders.recipe());

    private RecipeReloadHook() {
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Listener(event.getServerResources().getRecipeManager()));
    }

    private static Map<ResourceLocation, JsonObject> readSources(ResourceManager resources) {
        Map<ResourceLocation, JsonObject> sources = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : RECIPE_FILES.listMatchingResources(resources).entrySet()) {
            ResourceLocation id = RECIPE_FILES.fileToId(entry.getKey());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element.isJsonObject()) {
                    sources.put(id, element.getAsJsonObject());
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.debug("ModJS could not read recipe source {}", entry.getKey(), e);
            }
        }
        return sources;
    }

    private static final class Listener extends SimplePreparableReloadListener<Void> {
        private final RecipeManager manager;

        Listener(RecipeManager manager) {
            this.manager = manager;
        }

        @Override
        protected Void prepare(ResourceManager resources, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void prepared, ResourceManager resources, ProfilerFiller profiler) {
            RecipeChanges changes = RecipeChanges.current();
            com.itmecasey.modjs.unify.Unifier unifier = com.itmecasey.modjs.unify.UnifyReload.prepare(resources);
            if (changes.isEmpty() && !unifier.active()) {
                EventGroups.SERVER.post("afterRecipes", new EventJS());
                return;
            }
            Map<ResourceLocation, JsonObject> sources = readSources(resources);
            List<Recipe<?>> result = new ArrayList<>();
            int before = manager.getRecipes().size();
            int withoutSource = 0;
            for (Recipe<?> holder : manager.getRecipes()) {
                ResourceLocation id = holder.getId();
                JsonObject source = sources.get(id);
                if (source == null) {
                    withoutSource++;
                    if (changes.apply(id.toString(), new JsonObject()) != null) {
                        result.add(holder);
                    }
                    continue;
                }
                JsonObject changed = changes.apply(id.toString(), source);
                if (changed == null) {
                    continue;
                }
                changed = unifier.apply(id.toString(), changed);
                if (changed == source) {
                    result.add(holder);
                    continue;
                }
                try {
                    result.add(RecipeManager.fromJson(id, changed, net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY));
                } catch (RuntimeException e) {
                    LOGGER.error("ModJS could not rebuild recipe {}: {}", id, e.getMessage());
                    result.add(holder);
                }
            }
            for (Map.Entry<String, JsonObject> conversion : unifier.conversionRecipes().entrySet()) {
                try {
                    result.add(RecipeManager.fromJson(new ResourceLocation(conversion.getKey()), conversion.getValue(),
                        net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY));
                } catch (RuntimeException e) {
                    LOGGER.error("ModJS could not build conversion recipe {}: {}", conversion.getKey(), e.getMessage());
                }
            }
            manager.replaceRecipes(result);
            EventGroups.SERVER.post("afterRecipes", new EventJS());
            LOGGER.info("ModJS recipes: {} loaded, {} removed, {} rewritten, {} without a source file; unify rewrote {} outputs and {} inputs, added {} conversions",
                before, changes.removedCount(), changes.replacedCount(), withoutSource, unifier.outputsRewritten(), unifier.inputsRewritten(),
                unifier.conversionRecipes().size());
        }
    }
}
