package com.itmecasey.modjs.probe;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

/** {@code /modjs dump}: writes the game's registries, tags and assets and the typings built from them into {@code modjs/probe/}. */
public final class DumpCommands {
    private DumpCommands() {
    }

    /** Operators can run it; so can anyone in a single-player world, where the host has no operator level unless cheats are on. */
    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root, Predicate<CommandSourceStack> admin) {
        Predicate<CommandSourceStack> allowed = source -> admin.test(source) || source.getServer().isSingleplayer();
        root.then(Commands.literal("dump").requires(allowed).executes(context -> {
            CommandSourceStack source = context.getSource();
            return DumpJob.start(source.getServer(), line -> source.sendSuccess(() -> Component.literal(line), false)) ? 1 : 0;
        }));
    }
}
