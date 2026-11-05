package com.itmecasey.modjs.network;

import com.google.gson.JsonElement;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.json.JsonUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One payload type that carries script data in both directions: a script channel name plus a JSON string.
 * The channel is optional, so players without ModJS can still join servers that use it.
 */
public final class ModJSNetwork {
    /** A message from a script. */
    public record DataMessage(String channel, String json) implements CustomPacketPayload {
        public static final Type<DataMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath("modjs", "data"));
        static final StreamCodec<FriendlyByteBuf, DataMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DataMessage::channel,
            ByteBufCodecs.STRING_UTF8, DataMessage::json,
            DataMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ModJSNetwork() {
    }

    public static void init(IEventBus modBus) {
        // 26.3 splits the handlers: the one given here runs on the server, the client one is registered on its own event.
        modBus.addListener((RegisterPayloadHandlersEvent event) ->
            event.registrar("1").optional().playBidirectional(DataMessage.TYPE, DataMessage.CODEC, ModJSNetwork::handle));
        modBus.addListener((net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent event) ->
            event.register(DataMessage.TYPE, ModJSNetwork::handle));
    }

    private static void handle(DataMessage message, IPayloadContext context) {
        ServerPlayer sender = context.player() instanceof ServerPlayer player ? player : null;
        EventGroups.NETWORK.post("dataReceived", message.channel(), new NetworkEventJS(message.channel(), message.json(), sender));
    }

    private static String encode(Object data) {
        JsonElement json = JsonUtil.toJson(data);
        return json.toString();
    }

    public static void sendToServer(String name, Object data) {
        ClientPacketDistributor.sendToServer(new DataMessage(name, encode(data)));
    }

    public static void sendToPlayer(ServerPlayer player, String name, Object data) {
        PacketDistributor.sendToPlayer(player, new DataMessage(name, encode(data)));
    }

    public static void sendToAll(String name, Object data) {
        PacketDistributor.sendToAllPlayers(new DataMessage(name, encode(data)));
    }
}
