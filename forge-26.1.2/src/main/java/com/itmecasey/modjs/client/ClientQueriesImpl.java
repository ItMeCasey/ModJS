package com.itmecasey.modjs.client;

import com.itmecasey.modjs.worldcraft.WorldCraftAdapters;
import com.itmecasey.modjs.worldcraft.WorldHandles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.LinkedHashMap;
import java.util.Map;

/** Answers {@code Client.lookingAt()}, {@code Client.getWorld()} and {@code Client.isKeyHeld(id)} from the running game. */
final class ClientQueriesImpl implements ClientQueries.Provider {
    private ClientQueriesImpl() {
    }

    static void install() {
        ClientQueries.install(new ClientQueriesImpl());
    }

    @Override
    public WorldHandles.LevelHandle level() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : new WorldCraftAdapters.Level(mc.level);
    }

    @Override
    public Map<String, Object> lookingAt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", pos.getX());
        out.put("y", pos.getY());
        out.put("z", pos.getZ());
        out.put("id", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());
        out.put("face", hit.getDirection().getName());
        out.put("distance", mc.player.getEyePosition().distanceTo(hit.getLocation()));
        return out;
    }

    @Override
    public boolean keyHeld(String id) {
        KeyMapping mapping = ScriptedKeys.get(id);
        return mapping != null && mapping.isDown();
    }

    @Override
    public boolean keyKnown(String id) {
        return ScriptedKeys.get(id) != null;
    }
}
