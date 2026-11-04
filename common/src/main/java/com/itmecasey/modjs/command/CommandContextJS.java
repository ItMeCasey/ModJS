package com.itmecasey.modjs.command;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import java.util.Map;

/** What a script command receives: the sender, the typed arguments and replies. */
public final class CommandContextJS<S> {
    private final CommandContext<S> context;
    private final CommandEditorEventJS.Env<S> env;
    private final Map<String, String> types;

    CommandContextJS(CommandContext<S> context, CommandEditorEventJS.Env<S> env, Map<String, String> types) {
        this.context = context;
        this.env = env;
        this.types = types;
    }

    /** The game's command source. */
    public S getSource() {
        return context.getSource();
    }

    /** The player who ran the command, or null for the console and command blocks. */
    public Object getPlayer() {
        return env.player(context.getSource());
    }

    /** The command line as typed, without the slash. */
    public String getInput() {
        return context.getInput();
    }

    /** True when the argument was given. */
    public boolean has(String name) {
        try {
            context.getArgument(name, Object.class);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The value of an argument: text, a number, a boolean, or a list of players for {@code players}. */
    public Object arg(String name) throws CommandSyntaxException {
        String type = types.get(name);
        if (type == null) {
            // A command changed with edit() has no usage string; hand back whatever the game parsed.
            return context.getArgument(name, Object.class);
        }
        switch (type) {
            case "word":
            case "string":
            case "text":
                return StringArgumentType.getString(context, name);
            case "integer":
                return IntegerArgumentType.getInteger(context, name);
            case "long":
                return LongArgumentType.getLong(context, name);
            case "float":
                return (double) FloatArgumentType.getFloat(context, name);
            case "double":
                return DoubleArgumentType.getDouble(context, name);
            case "bool":
                return BoolArgumentType.getBool(context, name);
            default:
                return env.readArgument(context, name, type);
        }
    }

    /** Sends a chat line back to whoever ran the command. */
    public void reply(String text) {
        env.reply(context.getSource(), text);
    }

    /** Ends the command with an error message shown in red. */
    public void fail(String text) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(new LiteralMessage(text)).create();
    }
}
