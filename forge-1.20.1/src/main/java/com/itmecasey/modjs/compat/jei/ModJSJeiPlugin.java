package com.itmecasey.modjs.compat.jei;

import com.itmecasey.modjs.client.RecipeViewerEventJS;
import com.itmecasey.modjs.event.EventGroups;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Connects {@code RecipeViewerEvents} to JEI. JEI discovers this class by its annotation, so it only loads when JEI is
 * installed; nothing else in ModJS refers to it.
 */
@JeiPlugin
public final class ModJSJeiPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final ResourceLocation UID = new ResourceLocation("modjs", "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!EventGroups.RECIPE_VIEWER.hasHandlers("addInformation")) {
            return;
        }
        RecipeViewerEventJS.AddInformation event = new RecipeViewerEventJS.AddInformation();
        EventGroups.RECIPE_VIEWER.post("addInformation", event);
        for (Map.Entry<String, List<String>> entry : event.info().entrySet()) {
            List<Item> items = resolve(entry.getKey());
            if (items.isEmpty()) {
                LOGGER.warn("RecipeViewerEvents.addInformation: nothing matches '{}'", entry.getKey());
                continue;
            }
            Component[] text = entry.getValue().stream().map(Component::literal).toArray(Component[]::new);
            List<ItemStack> stacks = items.stream().map(ItemStack::new).toList();
            registration.addIngredientInfo(stacks, VanillaTypes.ITEM_STACK, text);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        com.itmecasey.modjs.unify.Unifier unifier = com.itmecasey.modjs.unify.Unifier.current();
        if (unifier.active()) {
            List<ItemStack> replaced = new ArrayList<>();
            unifier.groups().forEach(group -> group.members().forEach(member -> {
                ResourceLocation id = ResourceLocation.tryParse(member);
                if (id != null) {
                    BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> replaced.add(new ItemStack(item)));
                }
            }));
            if (!replaced.isEmpty()) {
                runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, replaced);
            }
        }
        if (EventGroups.RECIPE_VIEWER.hasHandlers("hideItems")) {
            RecipeViewerEventJS.HideItems event = new RecipeViewerEventJS.HideItems();
            EventGroups.RECIPE_VIEWER.post("hideItems", event);
            Set<Item> hidden = new LinkedHashSet<>();
            for (String selector : event.selectors()) {
                List<Item> matches = resolve(selector);
                if (matches.isEmpty()) {
                    LOGGER.warn("RecipeViewerEvents.hideItems: nothing matches '{}'", selector);
                }
                hidden.addAll(matches);
            }
            if (!hidden.isEmpty()) {
                List<ItemStack> stacks = hidden.stream().map(ItemStack::new).toList();
                runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
            }
        }
        if (EventGroups.RECIPE_VIEWER.hasHandlers("hideCategories")) {
            RecipeViewerEventJS.HideCategories event = new RecipeViewerEventJS.HideCategories();
            EventGroups.RECIPE_VIEWER.post("hideCategories", event);
            IRecipeManager manager = runtime.getRecipeManager();
            Set<String> wanted = new LinkedHashSet<>(event.ids());
            List<IRecipeCategory<?>> categories = manager.createRecipeCategoryLookup().includeHidden().get().toList();
            for (IRecipeCategory<?> category : categories) {
                if (wanted.remove(category.getRecipeType().getUid().toString())) {
                    manager.hideRecipeCategory(category.getRecipeType());
                }
            }
            wanted.forEach(id -> LOGGER.warn("RecipeViewerEvents.hideCategories: no recipe category '{}'", id));
        }
    }

    /** Items for an id, a {@code #tag}, or an {@code @mod} selector. */
    private static List<Item> resolve(String selector) {
        List<Item> out = new ArrayList<>();
        if (selector.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(selector.substring(1));
            if (id != null) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                    .ifPresent(tag -> tag.forEach(holder -> out.add(holder.value())));
            }
        } else if (selector.startsWith("@")) {
            String mod = selector.substring(1);
            BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
                if (entry.getKey().location().getNamespace().equals(mod)) {
                    out.add(entry.getValue());
                }
            });
        } else {
            ResourceLocation id = ResourceLocation.tryParse(selector);
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(out::add);
            }
        }
        return out;
    }
}
