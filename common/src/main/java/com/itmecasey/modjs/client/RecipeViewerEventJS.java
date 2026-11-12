package com.itmecasey.modjs.client;

import com.itmecasey.modjs.event.EventJS;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Events for recipe viewers (JEI today). Item selectors are an id ({@code minecraft:dirt}), a tag ({@code #minecraft:logs})
 * or a whole mod ({@code @create}).
 */
public final class RecipeViewerEventJS {
    private RecipeViewerEventJS() {
    }

    /** {@code RecipeViewerEvents.hideItems(event => event.hide('minecraft:dirt'))} */
    public static final class HideItems extends EventJS {
        private final List<String> selectors = new ArrayList<>();

        /** Hides items from the viewer's item list and from searches. */
        public void hide(String... items) {
            selectors.addAll(List.of(items));
        }

        public List<String> selectors() {
            return selectors;
        }
    }

    /** {@code RecipeViewerEvents.addInformation(event => event.add('minecraft:diamond', 'Found deep underground'))} */
    public static final class AddInformation extends EventJS {
        private final Map<String, List<String>> info = new LinkedHashMap<>();

        /** Starts with a page for every in-world recipe (see {@code ServerEvents.worldRecipes}) on the items it involves. */
        public AddInformation() {
            info.putAll(com.itmecasey.modjs.worldcraft.WorldRecipes.viewerInformation());
        }

        /** Adds an info page for the items. Each extra argument is a paragraph. */
        public void add(String items, String... paragraphs) {
            info.computeIfAbsent(items, k -> new ArrayList<>()).addAll(List.of(paragraphs));
        }

        public Map<String, List<String>> info() {
            return info;
        }
    }

    /** One named group of items a viewer shows together. */
    public record ItemGroup(String name, String icon, List<String> selectors) {
    }

    /**
     * {@code RecipeViewerEvents.groupItems(event => event.group('Gems', ['minecraft:diamond', '#c:gems'], {icon: 'minecraft:diamond'}))}.
     * For viewers that can fold several items into one entry (Barely Enough Items); viewers without grouping ignore it.
     */
    public static final class GroupItems extends EventJS {
        private final List<ItemGroup> groups = new ArrayList<>();

        /** Groups items under a name. {@code items} are ids, {@code #tags} or {@code @mods}. The optional icon is an item id shown for the group. */
        public void group(String name, Object items, Object options) {
            List<String> selectors = new ArrayList<>();
            for (Object entry : com.itmecasey.modjs.recipe.RecipeJson.asList(items)) {
                String text = com.itmecasey.modjs.json.JsonUtil.asString(entry);
                if (text != null) {
                    selectors.add(text);
                }
            }
            String icon = options == null ? null : com.itmecasey.modjs.json.JsonUtil.asString(com.itmecasey.modjs.json.JsonUtil.property(options, "icon"));
            groups.add(new ItemGroup(name, icon, selectors));
        }

        public void group(String name, Object items) {
            group(name, items, null);
        }

        public List<ItemGroup> groups() {
            return groups;
        }
    }

    /** {@code RecipeViewerEvents.hideCategories(event => event.hide('minecraft:crafting'))} */
    public static final class HideCategories extends EventJS {
        private final List<String> ids = new ArrayList<>();

        /** Hides whole recipe categories by id, for example {@code minecraft:crafting} or {@code minecraft:smelting}. */
        public void hide(String... categories) {
            ids.addAll(List.of(categories));
        }

        public List<String> ids() {
            return ids;
        }
    }
}
