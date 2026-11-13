package com.itmecasey.modjs.probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.format.DataFolders;
import com.itmecasey.modjs.game.GameRuleBinding;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reads the running game into a {@link DumpData}. The server-thread steps are small so the job can run them a few milliseconds
 * at a time; the data pack scans read files and run on the dump's own thread. Only classes that kept their names across the
 * supported versions are used, and keys are read through {@link Ids}, so one copy of this code serves every build.
 */
final class GameCollector {
    private static final int BATCH = 400;

    private GameCollector() {
    }

    /** Collects ids and tags of one registry, and block states when the registry is the block registry. */
    private static final class RegistryStep implements DumpJob.Step {
        private final String name;
        private final Iterator<? extends Holder.Reference<?>> holders;
        private final List<String> ids = new ArrayList<>();
        private final Map<String, TreeSet<String>> tags = new TreeMap<>();
        private final DumpData data;

        RegistryStep(String name, Registry<?> registry, DumpData data) {
            this.name = name;
            this.holders = DumpHooks.holders(registry).iterator();
            this.data = data;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean run() {
            int count = 0;
            while (holders.hasNext() && count++ < BATCH) {
                Holder.Reference<?> holder = holders.next();
                String id = Ids.location(holder.key());
                ids.add(id);
                holder.tags().forEach(tag -> tags.computeIfAbsent(Ids.location(tag), k -> new TreeSet<>()).add(id));
                if (name.equals("block") && holder.value() instanceof Block block) {
                    blockStates(id, block);
                }
            }
            if (holders.hasNext()) {
                return false;
            }
            ids.sort(null);
            data.registries.put(name, ids);
            if (!tags.isEmpty()) {
                Map<String, List<String>> sorted = new TreeMap<>();
                tags.forEach((tag, members) -> sorted.put(tag, new ArrayList<>(members)));
                data.tags.put(name, sorted);
            }
            return true;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void blockStates(String id, Block block) {
            Map<String, List<String>> properties = new LinkedHashMap<>();
            for (Property<?> property : block.getStateDefinition().getProperties()) {
                List<String> values = new ArrayList<>();
                for (Object value : property.getPossibleValues()) {
                    values.add(((Property) property).getName((Comparable) value));
                }
                properties.put(property.getName(), values);
            }
            if (!properties.isEmpty()) {
                data.blockStates.put(id, properties);
            }
        }
    }

    /** One step per registry: every static registry first, then the dynamic ones of the running server's registry access. */
    static List<DumpJob.Step> registrySteps(MinecraftServer server, DumpData data) {
        Map<String, Registry<?>> registries = new LinkedHashMap<>();
        server.registryAccess().registries().forEach(entry -> {
            Registry<?> registry = entry.value();
            registries.putIfAbsent(Ids.registryName(Ids.location(registry.key())), registry);
        });
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            registries.putIfAbsent(Ids.registryName(Ids.location(registry.key())), registry);
        }
        List<DumpJob.Step> steps = new ArrayList<>();
        registries.forEach((name, registry) -> steps.add(new RegistryStep(name, registry, data)));
        return steps;
    }

    static DumpJob.Step commandStep(MinecraftServer server, DumpData data) {
        return new DumpJob.Step() {
            @Override
            public String name() {
                return "commands";
            }

            @Override
            public boolean run() {
                int[] budget = {20000};
                data.commands = node(server.getCommands().getDispatcher().getRoot(), 0, budget);
                return true;
            }
        };
    }

    private static JsonObject node(CommandNode<?> node, int depth, int[] budget) {
        JsonObject out = new JsonObject();
        out.addProperty("n", node.getName());
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            out.addProperty("t", argument.getType().getClass().getSimpleName());
        }
        if (node.getCommand() != null) {
            out.addProperty("x", true);
        }
        if (node.getRedirect() != null) {
            out.addProperty("r", node.getRedirect().getName());
        } else if (depth < 12) {
            JsonArray children = new JsonArray();
            for (CommandNode<?> child : node.getChildren()) {
                if (budget[0]-- <= 0) {
                    break;
                }
                children.add(node(child, depth + 1, budget));
            }
            if (!children.isEmpty()) {
                out.add("c", children);
            }
        }
        return out;
    }

    static DumpJob.Step gameRuleStep(DumpData data) {
        return new DumpJob.Step() {
            @Override
            public String name() {
                return "game rules";
            }

            @Override
            public boolean run() {
                GameRuleBinding rules = new GameRuleBinding();
                for (String rule : rules.list()) {
                    String type = "integer";
                    try {
                        if (rules.get(rule) instanceof Boolean) {
                            type = "boolean";
                        }
                    } catch (RuntimeException ignored) {
                        // The value is not needed, only its type.
                    }
                    data.gameRules.put(rule, type);
                }
                return true;
            }
        };
    }

    /**
     * Recipes, loot table ids and advancement ids from the server's data packs (vanilla, mods, world and ModJS's generated pack).
     * Recipes are the pack JSON, so a recipe a script removes or replaces at run time still appears here.
     */
    static void dataPackScan(ResourceManager manager, DumpData data, List<String> warnings) {
        String recipeFolder = DataFolders.recipe();
        String lootFolder = DataFolders.lootTable();
        String advancementFolder = recipeFolder.endsWith("s") ? "advancements" : "advancement";
        scan(manager, recipeFolder, (id, json) -> data.recipes.put(id, json), warnings);
        data.registries.put("loot_table", idsIn(manager, lootFolder));
        data.registries.put("advancement", idsIn(manager, advancementFolder));
    }

    private static List<String> idsIn(ResourceManager manager, String folder) {
        List<String> ids = new ArrayList<>();
        for (var entry : manager.listResources(folder, location -> location.getPath().endsWith(".json")).entrySet()) {
            ids.add(stripId(entry.getKey().getNamespace(), entry.getKey().getPath(), folder));
        }
        ids.sort(null);
        return ids;
    }

    private interface JsonSink {
        void accept(String id, JsonElement json);
    }

    private static void scan(ResourceManager manager, String folder, JsonSink sink, List<String> warnings) {
        int failures = 0;
        for (var entry : manager.listResources(folder, location -> location.getPath().endsWith(".json")).entrySet()) {
            try (InputStream in = entry.getValue().open()) {
                JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                sink.accept(stripId(entry.getKey().getNamespace(), entry.getKey().getPath(), folder), json);
            } catch (Exception e) {
                failures++;
            }
        }
        if (failures > 0) {
            warnings.add(failures + " file(s) in " + folder + " could not be read");
        }
    }

    static String stripId(String namespace, String path, String folder) {
        String relative = path.startsWith(folder + "/") ? path.substring(folder.length() + 1) : path;
        if (relative.endsWith(".json")) {
            relative = relative.substring(0, relative.length() - ".json".length());
        }
        return namespace + ":" + relative;
    }
}
