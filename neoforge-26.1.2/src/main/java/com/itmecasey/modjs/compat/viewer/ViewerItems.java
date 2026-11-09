package com.itmecasey.modjs.compat.viewer;

import com.itmecasey.modjs.client.RecipeViewerEventJS;
import com.itmecasey.modjs.event.EventGroups;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import java.util.*;

/** Shared item selectors and script callbacks for optional recipe viewers. */
public final class ViewerItems {
    private ViewerItems() {}

    public static Set<Item> hiddenItems() {
        RecipeViewerEventJS.HideItems event = new RecipeViewerEventJS.HideItems();
        EventGroups.RECIPE_VIEWER.post("hideItems", event);
        Set<Item> hidden = new LinkedHashSet<>();
        event.selectors().forEach(selector -> hidden.addAll(resolve(selector)));
        com.itmecasey.modjs.unify.Unifier.current().groups().forEach(group ->
            group.members().forEach(member -> hidden.addAll(resolve(member))));
        return hidden;
    }

    public static Map<String, List<String>> information() {
        RecipeViewerEventJS.AddInformation event = new RecipeViewerEventJS.AddInformation();
        EventGroups.RECIPE_VIEWER.post("addInformation", event);
        return event.info();
    }

    public static Set<String> hiddenCategories() {
        RecipeViewerEventJS.HideCategories event = new RecipeViewerEventJS.HideCategories();
        EventGroups.RECIPE_VIEWER.post("hideCategories", event);
        return new LinkedHashSet<>(event.ids());
    }
    /** Items for an id, a {@code #tag}, or an {@code @mod} selector. */
    public static List<Item> resolve(String selector) {
        List<Item> out = new ArrayList<>();
        if (selector.startsWith("#")) {
            Identifier id = Identifier.tryParse(selector.substring(1));
            if (id != null) {
                BuiltInRegistries.ITEM.get(TagKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                    .ifPresent(tag -> tag.forEach(holder -> out.add(holder.value())));
            }
        } else if (selector.startsWith("@")) {
            String mod = selector.substring(1);
            BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
                if (entry.getKey().identifier().getNamespace().equals(mod)) {
                    out.add(entry.getValue());
                }
            });
        } else {
            Identifier id = Identifier.tryParse(selector);
            if (id != null) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(out::add);
            }
        }
        return out;
    }

}
