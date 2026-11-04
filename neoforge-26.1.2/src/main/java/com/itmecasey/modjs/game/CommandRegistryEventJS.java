package com.itmecasey.modjs.game;

import com.itmecasey.modjs.command.CommandEditorEventJS;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/**
 * The event passed to {@code ServerEvents.commandRegistry}. {@code add}, {@code remove}, {@code edit}, {@code alias} and
 * {@code rename} come from {@link CommandEditorEventJS}. Raw Brigadier is still possible: build a command with
 * {@code Java.loadClass('net.minecraft.commands.Commands')} and pass the literal to {@link #register}.
 */
public final class CommandRegistryEventJS extends CommandEditorEventJS<CommandSourceStack> {
    private static final Env<CommandSourceStack> ENV = new Env<>() {
        @Override
        public boolean hasPermission(CommandSourceStack source, int level) {
            if (level <= 0) {
                return true;
            }
            return source.permissions().hasPermission(switch (level) {
                case 1 -> net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR;
                case 2 -> net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER;
                case 3 -> net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN;
                default -> net.minecraft.server.permissions.Permissions.COMMANDS_OWNER;
            });
        }

        @Override
        public Object player(CommandSourceStack source) {
            return source.getPlayer();
        }

        @Override
        public void reply(CommandSourceStack source, String text) {
            source.sendSuccess(() -> Component.literal(text), false);
        }

        @Override
        public ArgumentType<?> argumentType(String type) {
            return switch (type) {
                case "player" -> EntityArgument.player();
                case "players" -> EntityArgument.players();
                case "entity" -> EntityArgument.entity();
                case "entities" -> EntityArgument.entities();
                default -> null;
            };
        }

        @Override
        public Object readArgument(CommandContext<CommandSourceStack> context, String name, String type) throws CommandSyntaxException {
            return switch (type) {
                case "player" -> EntityArgument.getPlayer(context, name);
                case "players" -> new ArrayList<>(EntityArgument.getPlayers(context, name));
                case "entity" -> EntityArgument.getEntity(context, name);
                case "entities" -> new ArrayList<>(EntityArgument.getEntities(context, name));
                default -> throw new IllegalArgumentException("Unknown argument type '" + type + "'");
            };
        }
    };

    public CommandRegistryEventJS(CommandDispatcher<CommandSourceStack> dispatcher) {
        super(dispatcher, ENV);
    }

    public void register(LiteralArgumentBuilder<CommandSourceStack> command) {
        dispatcher.register(command);
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() {
        return dispatcher;
    }
}
