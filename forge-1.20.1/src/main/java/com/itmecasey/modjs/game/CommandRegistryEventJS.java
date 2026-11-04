package com.itmecasey.modjs.game;

import com.itmecasey.modjs.event.EventJS;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;

/**
 * The event passed to {@code ServerEvents.commandRegistry}. Build commands with Brigadier through
 * {@code Java.loadClass('net.minecraft.commands.Commands')} and pass the literal to {@link #register}.
 */
public final class CommandRegistryEventJS extends EventJS {
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public CommandRegistryEventJS(CommandDispatcher<CommandSourceStack> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void register(LiteralArgumentBuilder<CommandSourceStack> command) {
        dispatcher.register(command);
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }
}
