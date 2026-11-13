package com.itmecasey.modjs.game;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.worldgen.DimensionEventJS;
import com.mojang.serialization.JsonOps;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The {@code Dimensions} global: creates and removes dimensions while the server runs. The dimension is built from the
 * same JSON the data pack format uses, then added to the server's level map the way {@code MinecraftServer.createLevels}
 * does. Created dimensions are saved in {@code modjs_dimensions.json} in the world folder and replayed on the next start,
 * so the chunk data in the dimension folder is found again.
 */
public final class DimensionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final String FILE = "modjs_dimensions.json";

    private static final Map<String, JsonObject> CREATED = new LinkedHashMap<>();

    // -- reflection into the server -----------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<ResourceKey<Level>, ServerLevel> levels(MinecraftServer server) {
        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType()) && field.getGenericType() instanceof ParameterizedType type
                && type.getActualTypeArguments().length == 2 && type.getActualTypeArguments()[1] == ServerLevel.class) {
                try {
                    field.setAccessible(true);
                    return (Map<ResourceKey<Level>, ServerLevel>) field.get(server);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("Could not find the server level map");
    }

    private static LevelStorageSource.LevelStorageAccess storage(MinecraftServer server) {
        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (field.getType() == LevelStorageSource.LevelStorageAccess.class) {
                try {
                    field.setAccessible(true);
                    return (LevelStorageSource.LevelStorageAccess) field.get(server);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalStateException("Could not find the world storage");
    }

    private static MinecraftServer server() {
        MinecraftServer server = ServerState.get();
        if (server == null) {
            throw new IllegalStateException("Dimensions can only be used while a world is loaded");
        }
        return server;
    }

    private static ResourceKey<Level> key(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id.contains(":") ? id : "modjs:" + id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid dimension id '" + id + "'");
        }
        return ResourceKey.create(Registries.DIMENSION, location);
    }

    // -- script API ---------------------------------------------------------------------------------------------

    /** True when the dimension is loaded right now (vanilla, data pack, or created at run time). */
    public boolean exists(String id) {
        return levels(server()).containsKey(key(id));
    }

    /** Ids of every loaded dimension. */
    public List<String> list() {
        List<String> out = new ArrayList<>();
        levels(server()).keySet().forEach(k -> out.add(k.location().toString()));
        return out;
    }

    /** Ids of the dimensions created through this API. */
    public List<String> created() {
        return new ArrayList<>(CREATED.keySet());
    }

    /**
     * Creates a dimension now, with the same options as {@code DimensionEvents} ({@code type}, {@code generator},
     * {@code layers}, {@code biome}). Returns false if it already exists. The dimension is remembered and reloaded
     * on the next server start.
     */
    public boolean create(String id, Object options) {
        MinecraftServer server = server();
        ResourceKey<Level> key = key(id);
        if (levels(server).containsKey(key)) {
            return false;
        }
        JsonObject stem = DimensionEventJS.stem(options);
        load(server, key, stem);
        CREATED.put(key.location().toString(), stem);
        save(server);
        return true;
    }

    /**
     * Unloads a dimension that was created through this API and deletes its files. Players inside are sent to the
     * overworld spawn first. Returns false if there was no such dimension.
     */
    public boolean delete(String id) {
        MinecraftServer server = server();
        ResourceKey<Level> key = key(id);
        String name = key.location().toString();
        if (!CREATED.containsKey(name)) {
            return false;
        }
        ServerLevel level = levels(server).get(key);
        if (level != null) {
            ServerLevel overworld = server.overworld();
            for (ServerPlayer player : new ArrayList<>(level.players())) {
                var spawn = overworld.getSharedSpawnPos();
                player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0f, 0.0f);
            }
            MinecraftForge.EVENT_BUS.post(new LevelEvent.Unload(level));
            levels(server).remove(key);
            try {
                level.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close dimension {}", name, e);
            }
            deleteFolder(DimensionType.getStorageFolder(key, storage(server).getLevelPath(LevelResource.ROOT)));
        }
        CREATED.remove(name);
        save(server);
        return true;
    }

    /** Moves a player into a dimension. Returns false if the dimension does not exist. */
    public boolean teleport(ServerPlayer player, String id, double x, double y, double z) {
        ServerLevel level = levels(server()).get(key(id));
        if (level == null) {
            return false;
        }
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        return true;
    }

    // -- internals ----------------------------------------------------------------------------------------------

    private static void load(MinecraftServer server, ResourceKey<Level> key, JsonObject stemJson) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        LevelStem stem = LevelStem.CODEC.parse(ops, stemJson).getOrThrow(false, error -> {
            throw new IllegalArgumentException("Invalid dimension: " + error);
        });
        ServerLevel overworld = server.overworld();
        DerivedLevelData data = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
        long seed = server.getWorldData().worldGenOptions().seed();
        ServerLevel level = new ServerLevel(server, Util.backgroundExecutor(), storage(server), data, key, stem,
            new NoProgress(), false, BiomeManager.obfuscateSeed(seed), ImmutableList.of(), false, overworld.getRandomSequences());
        overworld.getWorldBorder().addListener(new net.minecraft.world.level.border.BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder()));
        levels(server).put(key, level);
        MinecraftForge.EVENT_BUS.post(new LevelEvent.Load(level));
        LOGGER.info("ModJS created dimension {}", key.location());
    }

    /** Recreates the dimensions saved for this world; call after the server has started. */
    public static void restore(MinecraftServer server) {
        CREATED.clear();
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
                ResourceKey<Level> key = key(entry.getKey());
                if (!levels(server).containsKey(key)) {
                    try {
                        load(server, key, entry.getValue().getAsJsonObject());
                        CREATED.put(entry.getKey(), entry.getValue().getAsJsonObject());
                    } catch (RuntimeException e) {
                        LOGGER.error("Could not restore dimension {}", entry.getKey(), e);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not read {}", file, e);
        }
    }

    public static void clear() {
        CREATED.clear();
    }

    private static void save(MinecraftServer server) {
        JsonObject out = new JsonObject();
        CREATED.forEach(out::add);
        try {
            com.itmecasey.modjs.script.SafeFiles.writeAtomic(server.getWorldPath(LevelResource.ROOT).resolve(FILE), out.toString());
        } catch (IOException e) {
            LOGGER.error("Could not save {}", FILE, e);
        }
    }

    /** Deletes the save folder of a dimension a script created. Refuses anything that is not a folder inside a {@code dimensions} folder of the world. */
    private static void deleteFolder(Path folder) {
        Path normalized = folder.toAbsolutePath().normalize();
        boolean inside = false;
        for (Path parent = normalized.getParent(); parent != null; parent = parent.getParent()) {
            if (parent.getFileName() != null && parent.getFileName().toString().equals("dimensions")) {
                inside = true;
                break;
            }
        }
        if (!inside || normalized.getFileName() == null || normalized.getFileName().toString().equals("dimensions")) {
            LOGGER.warn("Not deleting {}: it is not a dimension folder", folder);
            return;
        }
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Could not delete {}", folder, e);
        }
    }

    /** Chunk progress listener that ignores everything; dimensions created at run time have no loading screen. */
    private static final class NoProgress implements ChunkProgressListener {
        @Override
        public void updateSpawnPos(ChunkPos center) {
        }

        @Override
        public void onStatusChange(ChunkPos chunkPos, ChunkStatus chunkStatus) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }
}
