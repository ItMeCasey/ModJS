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

        /** Adds an info page for the items. Each extra argument is a paragraph. */
        public void add(String items, String... paragraphs) {
            info.computeIfAbsent(items, k -> new ArrayList<>()).addAll(List.of(paragraphs));
        }

        public Map<String, List<String>> info() {
            return info;
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
