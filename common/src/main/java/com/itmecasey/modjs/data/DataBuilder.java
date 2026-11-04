package com.itmecasey.modjs.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.game.Scheduler;
import com.itmecasey.modjs.loot.LootEventJS;
import com.itmecasey.modjs.pack.GeneratedData;
import com.itmecasey.modjs.persistent.PersistentBinding;
import com.itmecasey.modjs.recipe.RecipeChanges;
import com.itmecasey.modjs.recipe.RecipesEventJS;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.itmecasey.modjs.tags.TagChanges;
import com.itmecasey.modjs.tags.TagsEventJS;
import com.itmecasey.modjs.worldgen.WorldgenEventJS;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Re-runs scripts and fills the generated packs. Called whenever the game scans for packs. */
public final class DataBuilder {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("modjs");

    /** Extra writers (for example registered items and blocks) that contribute to the client assets. */
    private static final List<Consumer<GeneratedData>> CLIENT_CONTRIBUTORS = new ArrayList<>();
    private static final List<Consumer<GeneratedData>> SERVER_CONTRIBUTORS = new ArrayList<>();

    private DataBuilder() {
    }

    public static void addClientContributor(Consumer<GeneratedData> contributor) {
        CLIENT_CONTRIBUTORS.add(contributor);
    }

    public static void addServerContributor(Consumer<GeneratedData> contributor) {
        SERVER_CONTRIBUTORS.add(contributor);
    }

    public static void buildServerData(GeneratedData data) {
        data.clear();
        RecipeChanges.reset();
        TagChanges.reset();
        FolderImporter.importInto(ScriptManager.root().resolve("data"), data);
        Scheduler.reset();
        PersistentBinding.resetCache();
        com.itmecasey.modjs.unify.UnifyConfig.resetServer();
        ScriptManager.of(ScriptType.SERVER).reload();

        EventGroups.SERVER.post("highPriorityData", new DataEventJS(data));
        SERVER_CONTRIBUTORS.forEach(contributor -> contributor.accept(data));

        RecipesEventJS recipes = new RecipesEventJS();
        EventGroups.SERVER.post("recipes", recipes);
        recipes.writeTo(data);

        for (String registry : EventGroups.SERVER.extraIds("tags")) {
            TagsEventJS tags = new TagsEventJS(registry);
            EventGroups.SERVER.postExact("tags", registry, tags);
            tags.writeTo(data);
        }

        EventGroups.SERVER.post("blockLootTables", new LootEventJS("blocks", "minecraft:block", data));
        EventGroups.SERVER.post("entityLootTables", new LootEventJS("entities", "minecraft:entity", data));
        EventGroups.SERVER.post("chestLootTables", new LootEventJS("chests", "minecraft:chest", data));
        EventGroups.SERVER.post("genericLootTables", new LootEventJS("", "minecraft:generic", data));
        EventGroups.WORLDGEN.post("add", new WorldgenEventJS(data));
        EventGroups.DIMENSION.post("add", new com.itmecasey.modjs.worldgen.DimensionEventJS(data));

        EventGroups.SERVER.post("generateData", new DataEventJS(data));
        writePackMetadata(data);
        LOGGER.info("ModJS generated {} data file(s)", data.size());
    }

    public static void buildClientAssets(GeneratedData data) {
        data.clear();
        FolderImporter.importInto(ScriptManager.root().resolve("assets"), data);
        ScriptManager.of(ScriptType.CLIENT).reload();

        EventGroups.CLIENT.post("highPriorityAssets", new DataEventJS(data));
        CLIENT_CONTRIBUTORS.forEach(contributor -> contributor.accept(data));

        for (String language : EventGroups.CLIENT.extraIds("lang")) {
            LangEventJS lang = new LangEventJS(language);
            EventGroups.CLIENT.postExact("lang", language, lang);
            JsonObject json = existingLang(data, language);
            lang.entries().forEach(json::addProperty);
            data.putJson("modjs", "lang/" + language, json);
        }

        if (EventGroups.CLIENT.hasHandlers("splashes")) {
            com.itmecasey.modjs.client.SplashEventJS splashes = new com.itmecasey.modjs.client.SplashEventJS(com.itmecasey.modjs.client.SplashEventJS.readVanilla());
            EventGroups.CLIENT.post("splashes", splashes);
            data.putText("minecraft", "texts/splashes.txt", splashes.toFile());
        }
        com.itmecasey.modjs.client.TitleScreenConfig title = new com.itmecasey.modjs.client.TitleScreenConfig();
        EventGroups.CLIENT.post("titleScreen", title);
        com.itmecasey.modjs.client.TitleScreenConfig.set(EventGroups.CLIENT.hasHandlers("titleScreen") ? title : null);

        EventGroups.CLIENT.post("generateAssets", new DataEventJS(data));
        LOGGER.info("ModJS generated {} asset file(s)", data.size());
    }

    private static JsonObject existingLang(GeneratedData data, String language) {
        byte[] bytes = data.get("modjs", "lang/" + language + ".json");
        if (bytes == null) {
            return new JsonObject();
        }
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void writePackMetadata(GeneratedData data) {
        // Nothing to add yet; kept as the single place for future pack-level files.
    }
}
