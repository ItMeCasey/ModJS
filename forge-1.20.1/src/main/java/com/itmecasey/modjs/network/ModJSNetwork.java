package com.itmecasey.modjs.network;

import com.google.gson.JsonElement;
import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.json.JsonUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * One Forge channel that carries script data in both directions: a script channel name plus a JSON string.
 * The channel is optional, so players without ModJS can still join servers that use it.
 */
public final class ModJSNetwork {
    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;

    /** A message from a script. */
    public record DataMessage(String channel, String json) {
        static void encode(DataMessage message, FriendlyByteBuf buffer) {
            buffer.writeUtf(message.channel());
            buffer.writeUtf(message.json(), 1 << 20);
        }

        static DataMessage decode(FriendlyByteBuf buffer) {
            return new DataMessage(buffer.readUtf(), buffer.readUtf(1 << 20));
        }
    }

    private ModJSNetwork() {
    }

    public static void init() {
        // Accept any version so a client or server without ModJS can still connect.
        channel = NetworkRegistry.ChannelBuilder.named(new ResourceLocation("modjs", "data"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(version -> true)
            .serverAcceptedVersions(version -> true)
            .simpleChannel();
        channel.registerMessage(0, DataMessage.class, DataMessage::encode, DataMessage::decode, ModJSNetwork::handle);
    }

    private static void handle(DataMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() ->
            EventGroups.NETWORK.post("dataReceived", message.channel(), new NetworkEventJS(message.channel(), message.json(), sender)));
        context.setPacketHandled(true);
    }

    private static String encode(Object data) {
        JsonElement json = JsonUtil.toJson(data);
        return json.toString();
    }

    public static void sendToServer(String name, Object data) {
        channel.sendToServer(new DataMessage(name, encode(data)));
    }

    public static void sendToPlayer(ServerPlayer player, String name, Object data) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), new DataMessage(name, encode(data)));
    }

    public static void sendToAll(String name, Object data) {
        channel.send(PacketDistributor.ALL.noArg(), new DataMessage(name, encode(data)));
    }
}
