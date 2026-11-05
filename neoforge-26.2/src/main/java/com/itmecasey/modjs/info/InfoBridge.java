package com.itmecasey.modjs.info;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.itmecasey.modjs.game.BlockEntityJS;
import com.itmecasey.modjs.json.JsonUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * Turns what the game shows into {@link InfoEventJS} events and what scripts answer back into game text, and carries the
 * lines from the server to the client for Jade. Shared by the Show Me What This Is and Jade plugins.
 */
public final class InfoBridge {
    private static final String KEY = "modjs_info";

    private InfoBridge() {
    }

    public static InfoEventJS blockEvent(Level level, BlockPos pos, BlockState state, BlockEntity entity, String side, boolean detailed) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        InfoEventJS event = new InfoEventJS("block", id, side, detailed, pos.getX(), pos.getY(), pos.getZ());
        event.state = state;
        BlockEntityJS wrapper = entity == null ? null : BlockEntityJS.at(level, pos);
        event.blockEntity = wrapper;
        if (wrapper != null) {
            event.withNbt(wrapper::getNbt);
        }
        return event;
    }

    public static InfoEventJS entityEvent(Entity entity, String side, boolean detailed) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        InfoEventJS event = new InfoEventJS("entity", id, side, detailed, entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
        event.entity = entity;
        return event;
    }

    /** A line's text as game text: a component a script built with {@code Text} is kept, anything else becomes plain text. */
    public static Component text(InfoLine line) {
        Object raw = JsonUtil.unwrap(line.text());
        MutableComponent component = raw instanceof Component given ? given.copy() : Component.literal(String.valueOf(raw));
        int color = line.textColor();
        return color < 0 ? component : component.withStyle(style -> style.withColor(color));
    }

    /** Writes lines and hidden Jade tags into a Jade server data tag, as one JSON string (NBT list access differs by version). */
    public static void write(CompoundTag data, InfoEventJS event) {
        if (event.lines().isEmpty() && event.hiddenTags().isEmpty()) {
            return;
        }
        JsonObject json = new JsonObject();
        JsonArray list = new JsonArray();
        for (InfoLine line : event.lines()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("t", text(line).getString());
            entry.addProperty("c", line.textColor());
            entry.addProperty("f", line.fraction());
            entry.addProperty("b", line.barColor());
            entry.addProperty("i", line.item());
            entry.addProperty("n", line.count());
            entry.addProperty("p", line.position());
            list.add(entry);
        }
        json.add("lines", list);
        JsonArray hidden = new JsonArray();
        event.hiddenTags().forEach(hidden::add);
        json.add("hidden", hidden);
        data.putString(KEY, json.toString());
    }

    /** Reads what {@link #write} stored, adding to the given collections. */
    public static void read(CompoundTag data, List<InfoLine> lines, Set<String> hidden) {
        if (data == null || !data.contains(KEY)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(data.getStringOr(KEY, "")).getAsJsonObject();
            for (JsonElement raw : json.getAsJsonArray("lines")) {
                JsonObject entry = raw.getAsJsonObject();
                lines.add(new InfoLine(entry.get("t").getAsString(), entry.get("c").getAsInt(), entry.get("f").getAsFloat(),
                    entry.get("b").getAsInt(), entry.get("i").getAsString(), Math.max(1, entry.get("n").getAsInt()), entry.get("p").getAsString()));
            }
            for (JsonElement raw : json.getAsJsonArray("hidden")) {
                hidden.add(raw.getAsString());
            }
        } catch (RuntimeException e) {
            // A malformed answer from the server shows nothing rather than breaking the overlay.
        }
    }
}
