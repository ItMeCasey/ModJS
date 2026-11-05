package com.itmecasey.modjs.recipe;

import com.google.gson.JsonElement;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.event.EventJS;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
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
 * {@link AddReloadListenerEvent} apply after the vanilla {@link RecipeManager}, and the derived recipe data
 * (stonecutter lists, recipe book) is built after every listener finished, so swapping the map here is safe.
 * <p>
 * Filters run on each recipe's source JSON, read from the resource manager. Re-encoding loaded recipes is not
 * possible while their tags are still unbound, and the source JSON also covers recipe types that cannot be encoded.
 */
public final class RecipeReloadHook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final FileToIdConverter RECIPE_FILES = FileToIdConverter.json("recipe");

    private RecipeReloadHook() {
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        LOGGER.debug("ModJS reload: adding the recipe listener");
        event.addListener(new Listener(event.getServerResources().getRecipeManager(), event.getServerResources().fullRegistries().lookup()));
    }

    private static Map<Identifier, JsonObject> readSources(ResourceManager resources) {
        Map<Identifier, JsonObject> sources = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : RECIPE_FILES.listMatchingResources(resources).entrySet()) {
            Identifier id = RECIPE_FILES.fileToId(entry.getKey());
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

    /** Builds a recipe map from a list; the map's constructor is private and its factories want a registry lookup. */
    private static RecipeMap recipeMap(List<RecipeHolder<?>> recipes) {
        com.google.common.collect.ImmutableMultimap.Builder<net.minecraft.world.item.crafting.RecipeType<?>, RecipeHolder<?>> byType = com.google.common.collect.ImmutableMultimap.builder();
        com.google.common.collect.ImmutableMap.Builder<net.minecraft.resources.ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = com.google.common.collect.ImmutableMap.builder();
        for (RecipeHolder<?> recipe : recipes) {
            byType.put(recipe.value().getType(), recipe);
            byKey.put(recipe.id(), recipe);
        }
        try {
            java.lang.reflect.Constructor<RecipeMap> constructor = RecipeMap.class.getDeclaredConstructor(com.google.common.collect.Multimap.class, Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(byType.build(), byKey.build());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build the recipe map", e);
        }
    }

    private static final class Listener extends SimplePreparableReloadListener<Void> {
        private final RecipeManager manager;
        private final net.minecraft.core.HolderLookup.Provider registries;

        Listener(RecipeManager manager, net.minecraft.core.HolderLookup.Provider registries) {
            this.manager = manager;
            this.registries = registries;
        }

        @Override
        protected Void prepare(ResourceManager resources, ProfilerFiller profiler) {
            LOGGER.debug("ModJS reload: prepare");
            return null;
        }

        @Override
        protected void apply(Void prepared, ResourceManager resources, ProfilerFiller profiler) {
            LOGGER.debug("ModJS reload: apply start");
            try {
                applyChanges(resources);
            } finally {
                LOGGER.debug("ModJS reload: apply end");
            }
        }

        private void applyChanges(ResourceManager resources) {
            RecipeChanges changes = RecipeChanges.current();
            com.itmecasey.modjs.unify.Unifier unifier = com.itmecasey.modjs.unify.UnifyReload.prepare(resources);
            if (changes.isEmpty() && !unifier.active()) {
                EventGroups.SERVER.post("afterRecipes", new EventJS());
                return;
            }
            Map<Identifier, JsonObject> sources = readSources(resources);
            RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
            List<RecipeHolder<?>> result = new ArrayList<>();
            int before = manager.getRecipes().size();
            int withoutSource = 0;
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                Identifier id = holder.id().identifier();
                JsonObject source = sources.get(id);
                if (source == null) {
                    // Recipes with no file (for example generated ones) can still be removed by id or mod.
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
                DataResult<Recipe<?>> decoded = Recipe.DIRECT_CODEC.parse(ops, changed);
                Recipe<?> recipe = decoded.result().orElse(null);
                if (recipe == null) {
                    LOGGER.error("ModJS could not rebuild recipe {}: {}", id, decoded.error().map(e -> e.message()).orElse("unknown error"));
                    result.add(holder);
                } else {
                    result.add(new RecipeHolder<>(holder.id(), recipe));
                }
            }
            for (Map.Entry<String, JsonObject> conversion : unifier.conversionRecipes().entrySet()) {
                Recipe<?> recipe = Recipe.DIRECT_CODEC.parse(ops, conversion.getValue()).result().orElse(null);
                if (recipe != null) {
                    result.add(new RecipeHolder<>(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                        Identifier.parse(conversion.getKey())), recipe));
                } else {
                    LOGGER.error("ModJS could not build conversion recipe {}", conversion.getKey());
                }
            }
            manager.recipes = recipeMap(result);
            EventGroups.SERVER.post("afterRecipes", new EventJS());
            LOGGER.info("ModJS recipes: {} loaded, {} removed, {} rewritten, {} without a source file; unify rewrote {} outputs and {} inputs, added {} conversions",
                before, changes.removedCount(), changes.replacedCount(), withoutSource, unifier.outputsRewritten(), unifier.inputsRewritten(),
                unifier.conversionRecipes().size());
        }
    }
}
