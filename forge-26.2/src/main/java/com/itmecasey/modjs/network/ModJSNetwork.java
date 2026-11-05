package com.itmecasey.modjs.network;

import com.google.gson.JsonElement;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.json.JsonUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One Forge channel that carries script data in both directions: a script channel name plus a JSON string.
 * The channel is optional, so players without ModJS can still join servers that use it.
 */
public final class ModJSNetwork {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static SimpleChannel channel;

    /** A message from a script. */
    public record DataMessage(String channel, String json) {
        static final StreamCodec<RegistryFriendlyByteBuf, DataMessage> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DataMessage::channel,
            ByteBufCodecs.STRING_UTF8, DataMessage::json,
            DataMessage::new);
    }

    private ModJSNetwork() {
    }

    public static void init() {
        channel = ChannelBuilder.named(Identifier.fromNamespaceAndPath("modjs", "data"))
            .networkProtocolVersion(1)
            .optional()
            .simpleChannel();
        channel.play()
            .bidirectional()
            .addMain(DataMessage.class, DataMessage.CODEC, ModJSNetwork::handle)
            .build();
    }

    private static void handle(DataMessage message, CustomPayloadEvent.Context context) {
        ServerPlayer sender = context.getSender();
        EventGroups.NETWORK.post("dataReceived", message.channel(), new NetworkEventJS(message.channel(), message.json(), sender));
    }

    private static String encode(Object data) {
        JsonElement json = JsonUtil.toJson(data);
        return json.toString();
    }

    public static void sendToServer(String name, Object data) {
        channel.send(new DataMessage(name, encode(data)), PacketDistributor.SERVER.noArg());
    }

    public static void sendToPlayer(ServerPlayer player, String name, Object data) {
        channel.send(new DataMessage(name, encode(data)), PacketDistributor.PLAYER.with(player));
    }

    public static void sendToAll(String name, Object data) {
        channel.send(new DataMessage(name, encode(data)), PacketDistributor.ALL.noArg());
    }

    static Logger logger() {
        return LOGGER;
    }
}
