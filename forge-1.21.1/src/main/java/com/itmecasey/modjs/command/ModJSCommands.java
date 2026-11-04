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

/** {@code /modjs reload}, {@code /modjs errors}, {@code /modjs status}. */
public final class ModJSCommands {
    private ModJSCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("modjs")
            .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("reload").executes(context -> {
            CommandSourceStack source = context.getSource();
            source.sendSuccess(() -> Component.literal("Reloading data packs and server scripts..."), true);
            source.getServer().getCommands().performPrefixedCommand(source, "reload");
            return 1;
        }));

        root.then(Commands.literal("errors").executes(context -> {
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

        root.then(Commands.literal("status").executes(context -> {
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
}
