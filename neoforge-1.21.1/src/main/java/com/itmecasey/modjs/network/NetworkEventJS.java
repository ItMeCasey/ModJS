package com.itmecasey.modjs.network;

import com.itmecasey.modjs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.ScriptRuntime;

/** The event passed to {@code NetworkEvents.dataReceived('channel', event => ...)}. */
public final class NetworkEventJS extends EventJS {
    private static final Callable IDENTITY = (cx, scope, thisObj, args) -> args[1];

    private final String channel;
    private final String json;
    private final ServerPlayer sender;

    NetworkEventJS(String channel, String json, ServerPlayer sender) {
        this.channel = channel;
        this.json = json;
        this.sender = sender;
    }

    public String getChannel() {
        return channel;
    }

    /** The message payload as a script value. */
    public Object getData() {
        Context cx = Context.getCurrentContext();
        return NativeJSON.parse(cx, ScriptRuntime.getTopCallScope(cx), json, IDENTITY);
    }

    /** The player who sent the message, or null when the server sent it to the client. */
    public ServerPlayer getPlayer() {
        return sender;
    }
}
