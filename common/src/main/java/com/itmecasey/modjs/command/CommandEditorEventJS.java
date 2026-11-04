package com.itmecasey.modjs.command;

import com.itmecasey.modjs.event.EventJS;
import com.itmecasey.modjs.json.JsonUtil;
import com.itmecasey.modjs.script.ModJSContextFactory;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.WrappedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The event passed to {@code ServerEvents.commandRegistry}: add commands from a usage string, remove them, change who may use
 * them or what they do, and give them other names. Brigadier cannot remove or change a node, so those edits reach into
 * {@link CommandNode} by reflection. Platform modules extend this with their command source type and the game's permission and
 * argument types.
 */
public abstract class CommandEditorEventJS<S> extends EventJS {
    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final Field CHILDREN = field("children");
    private static final Field LITERALS = field("literals");
    private static final Field ARGUMENTS = field("arguments");
    private static final Field REQUIREMENT = field("requirement");
    private static final Field COMMAND = field("command");

    /** What the editor needs from the game: permission levels, the sender, replies and the game's own argument types. */
    public interface Env<S> {
        /** True when the source has the given vanilla permission level (0 to 4). */
        boolean hasPermission(S source, int level);

        /** The player behind a source, or null for the console and command blocks. */
        Object player(S source);

        /** Sends a chat line to the sender. */
        void reply(S source, String text);

        /** A game argument type such as {@code player} or {@code players}, or null when unknown. */
        ArgumentType<?> argumentType(String type);

        /** Reads a value parsed by one of {@link #argumentType}'s types. */
        Object readArgument(CommandContext<S> context, String name, String type) throws CommandSyntaxException;
    }

    protected final CommandDispatcher<S> dispatcher;
    protected final Env<S> env;

    protected CommandEditorEventJS(CommandDispatcher<S> dispatcher, Env<S> env) {
        this.dispatcher = dispatcher;
        this.env = env;
    }

    private static Field field(String name) {
        try {
            Field field = CommandNode.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("ModJS cannot edit commands: Brigadier has no field '{}': {}", name, e.toString());
            return null;
        }
    }

    // ---- adding ----

    /**
     * Adds a command from a usage string. Words are literals and {@code <name:type>} is an argument. Types: word, string, text
     * (the rest of the line), integer, long, float, double, bool, player, players; numbers take limits as
     * {@code <n:integer:1:100>}. The second argument is a function, or an object with {@code run}, {@code permission}
     * (0 to 4) and {@code requires} (a function given the player, or null for the console).
     */
    public void add(String usage, Object options) {
        List<String> tokens = tokens(usage);
        if (tokens.isEmpty() || tokens.get(0).startsWith("<")) {
            throw new IllegalArgumentException("A command must start with a word, got '" + usage + "'");
        }
        Function run = options instanceof Function f ? f : asFunction(JsonUtil.property(options, "run"));
        if (run == null) {
            throw new IllegalArgumentException("Command '" + usage + "' needs a run function");
        }
        Predicate<S> requirement = requirement(options);
        Map<String, String> types = new LinkedHashMap<>();
        String name = tokens.get(0);
        ArgumentBuilder<S, ?> chain = null;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            ArgumentBuilder<S, ?> node;
            if (token.startsWith("<") && token.endsWith(">")) {
                String[] parts = token.substring(1, token.length() - 1).split(":");
                if (parts.length < 2) {
                    throw new IllegalArgumentException("Argument '" + token + "' needs a type, such as <name:word>");
                }
                types.put(parts[0], parts[1]);
                node = RequiredArgumentBuilder.<S, Object>argument(parts[0], castType(argumentType(parts)));
            } else {
                node = LiteralArgumentBuilder.<S>literal(token);
            }
            if (requirement != null) {
                node.requires(requirement);
            }
            if (chain == null) {
                node.executes(executor(name, run, types));
            } else {
                node.then(chain);
            }
            chain = node;
        }
        dispatcher.register((LiteralArgumentBuilder<S>) chain);
    }

    /** Adds a command made of one word. */
    public void add(String usage) {
        throw new IllegalArgumentException("Command '" + usage + "' needs a run function: add('" + usage + "', ctx => ...)");
    }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentType<T> castType(ArgumentType<?> type) {
        return (ArgumentType<T>) type;
    }

    private ArgumentType<?> argumentType(String[] parts) {
        String type = parts[1];
        try {
            switch (type) {
                case "word":
                    return StringArgumentType.word();
                case "string":
                    return StringArgumentType.string();
                case "text":
                    return StringArgumentType.greedyString();
                case "bool":
                    return BoolArgumentType.bool();
                case "integer":
                    return parts.length >= 4 ? IntegerArgumentType.integer(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))
                        : IntegerArgumentType.integer();
                case "long":
                    return parts.length >= 4 ? LongArgumentType.longArg(Long.parseLong(parts[2]), Long.parseLong(parts[3]))
                        : LongArgumentType.longArg();
                case "float":
                    return parts.length >= 4 ? FloatArgumentType.floatArg(Float.parseFloat(parts[2]), Float.parseFloat(parts[3]))
                        : FloatArgumentType.floatArg();
                case "double":
                    return parts.length >= 4 ? DoubleArgumentType.doubleArg(Double.parseDouble(parts[2]), Double.parseDouble(parts[3]))
                        : DoubleArgumentType.doubleArg();
                default:
                    ArgumentType<?> game = env.argumentType(type);
                    if (game == null) {
                        throw new IllegalArgumentException("Unknown argument type '" + type + "'");
                    }
                    return game;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad limits in argument type '" + String.join(":", parts) + "'");
        }
    }

    private static List<String> tokens(String usage) {
        List<String> out = new ArrayList<>();
        for (String part : usage.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    private Command<S> executor(String name, Function run, Map<String, String> types) {
        return context -> {
            CommandContextJS<S> js = new CommandContextJS<>(context, env, types);
            Object result = invoke(name, run, js);
            if (result instanceof Number number) {
                return number.intValue();
            }
            if (result instanceof Boolean flag) {
                return flag ? 1 : 0;
            }
            return 1;
        };
    }

    private static Object invoke(String name, Function fn, Object argument) throws CommandSyntaxException {
        try {
            return ModJSContextFactory.INSTANCE.call(cx -> fn.call(cx, fn.getParentScope(), fn.getParentScope(), new Object[] {argument}));
        } catch (WrappedException e) {
            if (e.getWrappedException() instanceof CommandSyntaxException syntax) {
                throw syntax;
            }
            throw scriptFailure(name, e);
        } catch (RhinoException e) {
            throw scriptFailure(name, e);
        }
    }

    private static CommandSyntaxException scriptFailure(String name, RhinoException e) {
        ScriptManager.of(ScriptType.SERVER).reportError("command /" + name, e);
        return new SimpleCommandExceptionType(new LiteralMessage("Script error in /" + name + ": " + e.details())).create();
    }

    private Predicate<S> requirement(Object options) {
        Object level = options instanceof Function ? null : JsonUtil.property(options, "permission");
        Function requires = options instanceof Function ? null : asFunction(JsonUtil.property(options, "requires"));
        if (level == null && requires == null) {
            return null;
        }
        int needed = level instanceof Number number ? number.intValue() : 0;
        return source -> {
            if (level != null && !env.hasPermission(source, needed)) {
                return false;
            }
            if (requires == null) {
                return true;
            }
            try {
                Object result = ModJSContextFactory.INSTANCE.call(
                    cx -> requires.call(cx, requires.getParentScope(), requires.getParentScope(), new Object[] {env.player(source)}));
                return result instanceof Boolean flag ? flag : result instanceof Number n ? n.doubleValue() != 0 : result != null;
            } catch (RhinoException e) {
                ScriptManager.of(ScriptType.SERVER).reportError("command requires", e);
                return false;
            }
        };
    }

    private static Function asFunction(Object value) {
        return value instanceof Function f ? f : null;
    }

    // ---- finding, removing, editing ----

    /** True when the command or sub-command at {@code path} (such as {@code "time set"}) exists. */
    public boolean exists(String path) {
        return find(path) != null;
    }

    /** The names of every top-level command. */
    public List<String> list() {
        List<String> names = new ArrayList<>();
        for (CommandNode<S> child : dispatcher.getRoot().getChildren()) {
            names.add(child.getName());
        }
        return names;
    }

    private CommandNode<S> find(String path) {
        CommandNode<S> node = dispatcher.getRoot();
        for (String token : tokens(path)) {
            node = node.getChild(token);
            if (node == null) {
                return null;
            }
        }
        return node == dispatcher.getRoot() ? null : node;
    }

    private CommandNode<S> require(String path) {
        CommandNode<S> node = find(path);
        if (node == null) {
            throw new IllegalArgumentException("No command '" + path + "'. Commands from other mods exist only if they registered before ModJS ran");
        }
        return node;
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<String, T> map(Field field, CommandNode<?> node) {
        try {
            return (Map<String, T>) field.get(node);
        } catch (IllegalAccessException | RuntimeException e) {
            throw new IllegalStateException("Brigadier's command tree could not be edited: " + e);
        }
    }

    private static void detach(CommandNode<?> parent, String name) {
        map(CHILDREN, parent).remove(name);
        map(LITERALS, parent).remove(name);
        map(ARGUMENTS, parent).remove(name);
    }

    /**
     * Removes a command, or a sub-command given as {@code "time set"}. Names that only redirect to a removed top-level command
     * (such as {@code tp} for {@code teleport}) go with it. Returns false when there was nothing to remove.
     */
    public boolean remove(String path) {
        List<String> tokens = tokens(path);
        CommandNode<S> node = find(path);
        if (node == null) {
            return false;
        }
        CommandNode<S> parent = dispatcher.getRoot();
        for (int i = 0; i < tokens.size() - 1; i++) {
            parent = parent.getChild(tokens.get(i));
        }
        if (parent == dispatcher.getRoot()) {
            for (CommandNode<S> child : new ArrayList<>(parent.getChildren())) {
                if (child.getRedirect() == node) {
                    detach(parent, child.getName());
                }
            }
        }
        detach(parent, node.getName());
        return true;
    }

    /**
     * Changes an existing command or sub-command: {@code permission} (0 to 4) and {@code requires} (a function) set who may use
     * it, and {@code run} replaces what it does. Anything left out stays as it was.
     */
    public void edit(String path, Object options) {
        CommandNode<S> node = require(path);
        Predicate<S> requirement = requirement(options);
        if (requirement != null) {
            try {
                REQUIREMENT.set(node, requirement);
            } catch (IllegalAccessException | RuntimeException e) {
                throw new IllegalStateException("Could not change who may use '" + path + "': " + e);
            }
        }
        Function run = asFunction(JsonUtil.property(options, "run"));
        if (run != null) {
            try {
                COMMAND.set(node, executor(tokens(path).get(0), run, new LinkedHashMap<>()));
            } catch (IllegalAccessException | RuntimeException e) {
                throw new IllegalStateException("Could not change what '" + path + "' does: " + e);
            }
        }
    }

    /** Makes {@code name} another way to type the command at {@code path}. */
    public void alias(String path, String name) {
        CommandNode<S> target = require(path);
        LiteralArgumentBuilder<S> alias = LiteralArgumentBuilder.<S>literal(name).requires(target.getRequirement()).redirect(target);
        if (target.getCommand() != null) {
            alias.executes(target.getCommand());
        }
        dispatcher.getRoot().addChild(alias.build());
    }

    /** Changes the name of a top-level command. Other names that pointed at it (aliases) are removed. */
    public void rename(String name, String newName) {
        CommandNode<S> target = require(name);
        if (!(target instanceof LiteralCommandNode)) {
            throw new IllegalArgumentException("'" + name + "' is not a command name");
        }
        remove(name);
        LiteralArgumentBuilder<S> renamed = LiteralArgumentBuilder.<S>literal(newName).requires(target.getRequirement()).redirect(target);
        if (target.getCommand() != null) {
            renamed.executes(target.getCommand());
        }
        dispatcher.getRoot().addChild(renamed.build());
    }
}
