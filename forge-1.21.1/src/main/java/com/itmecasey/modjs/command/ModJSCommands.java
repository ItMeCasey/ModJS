package com.itmecasey.modjs.command;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.game.CommandRegistryEventJS;
import com.itmecasey.modjs.recipe.RecipeChanges;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.function.Predicate;

/** {@code /modjs reload}, {@code errors}, {@code status} (operators) plus the item commands (everyone). */
public final class ModJSCommands {
    private ModJSCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("modjs");
        Predicate<CommandSourceStack> admin = source -> source.hasPermission(2);
        ItemListCommands.addTo(root);
        com.itmecasey.modjs.probe.DumpCommands.addTo(root, admin);

        root.then(Commands.literal("reload").requires(admin).executes(context -> {
            CommandSourceStack source = context.getSource();
            source.sendSuccess(() -> Component.literal("Reloading data packs and server scripts..."), true);
            source.getServer().getCommands().performPrefixedCommand(source, "reload");
            return 1;
        }));

        root.then(Commands.literal("reload").requires(admin).then(Commands.literal("config")
            .executes(context -> reloadConfigs(context.getSource(), null))
            .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(com.itmecasey.modjs.config.ConfigManager.names(), builder))
                .executes(context -> reloadConfigs(context.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name"))))));

        root.then(Commands.literal("errors").requires(admin).executes(context -> {
            CommandSourceStack source = context.getSource();
            int total = 0;
            for (ScriptType type : ScriptType.values()) {
                for (String error : ScriptManager.of(type).errors()) {
                    total++;
                    String line = "[" + type + "] " + error;
                    source.sendSuccess(() -> Component.literal(line), false);
                }
            }
            if (total == 0) {
                source.sendSuccess(() -> Component.literal("No script errors."), false);
            }
            return total;
        }));

        root.then(Commands.literal("trace").requires(admin)
            .then(Commands.literal("on").executes(context -> startTrace(context.getSource(), false))
                .then(Commands.literal("ticks").executes(context -> startTrace(context.getSource(), true))))
            .then(Commands.literal("off").executes(context -> {
                com.itmecasey.modjs.event.EventTrace.stop();
                context.getSource().sendSuccess(() -> Component.literal("Event trace off."), false);
                return 1;
            })));

        root.then(Commands.literal("unify").requires(admin).executes(context -> {
            CommandSourceStack source = context.getSource();
            com.itmecasey.modjs.unify.Unifier unifier = com.itmecasey.modjs.unify.Unifier.current();
            if (!unifier.active()) {
                source.sendSuccess(() -> Component.literal("No unified materials. Use Unify.materials(), Unify.group() or Unify.auto() in scripts."), false);
                return 0;
            }
            for (com.itmecasey.modjs.unify.Unifier.Group group : unifier.groups()) {
                String line = group.tag() + " -> " + group.canonical() + "  (" + group.members().size() + " replaced: " + String.join(", ", group.members()) + ")";
                source.sendSuccess(() -> Component.literal(line), false);
            }
            String totals = unifier.groups().size() + " group(s); recipes rewritten: " + unifier.outputsRewritten() + " outputs, " + unifier.inputsRewritten()
                + " inputs; conversion recipes: " + unifier.conversionRecipes().size();
            source.sendSuccess(() -> Component.literal(totals), false);
            return unifier.groups().size();
        }));

        root.then(Commands.literal("status").requires(admin).executes(context -> {
            CommandSourceStack source = context.getSource();
            for (ScriptType type : ScriptType.values()) {
                ScriptManager manager = ScriptManager.of(type);
                String line = type + ": " + manager.loadedCount() + " script(s), " + manager.errors().size() + " error(s)";
                source.sendSuccess(() -> Component.literal(line), false);
            }
            RecipeChanges changes = RecipeChanges.current();
            String recipes = "Recipes removed: " + changes.removedCount() + ", rewritten: " + changes.replacedCount();
            source.sendSuccess(() -> Component.literal(recipes), false);
            return 1;
        }));

        dispatcher.register(root);
        EventGroups.SERVER.post("commandRegistry", new CommandRegistryEventJS(dispatcher));
    }

    private static int startTrace(CommandSourceStack source, boolean ticks) {
        net.minecraft.server.level.ServerPlayer target = source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p ? p : null;
        com.itmecasey.modjs.event.EventTrace.start(line -> {
            org.slf4j.LoggerFactory.getLogger("modjs").info("TRACE {}", line);
            if (target != null && !target.hasDisconnected()) {
                target.sendSystemMessage(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }, ticks);
        source.sendSuccess(() -> Component.literal("Event trace on" + (ticks ? " (with ticks)" : "") + ". Use /modjs trace off to stop."), false);
        return 1;
    }

    /** {@code /modjs reload config [name]}: reads the config files of server and common configs again. */
    private static int reloadConfigs(CommandSourceStack source, String name) {
        java.util.List<com.itmecasey.modjs.config.ScriptConfig> targets = new java.util.ArrayList<>();
        if (name == null) {
            targets.addAll(com.itmecasey.modjs.config.ConfigManager.serverSide());
        } else {
            com.itmecasey.modjs.config.ScriptConfig config = com.itmecasey.modjs.config.ConfigManager.get(name);
            if (config == null) {
                source.sendFailure(Component.literal("No config named " + name));
                return 0;
            }
            if (config.side == com.itmecasey.modjs.config.ScriptConfig.Side.CLIENT) {
                source.sendFailure(Component.literal("Config " + name + " is a client config"));
                return 0;
            }
            targets.add(config);
        }
        int changed = 0;
        for (com.itmecasey.modjs.config.ScriptConfig config : targets) {
            if (config.reload()) {
                changed++;
            }
        }
        String text = "Reloaded " + targets.size() + " config(s), " + changed + " changed";
        source.sendSuccess(() -> Component.literal(text), false);
        return targets.size();
    }
}
