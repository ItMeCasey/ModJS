package com.itmecasey.modjs.command;

import com.itmecasey.modjs.event.EventGroups;
import com.itmecasey.modjs.script.ScriptManager;
import com.itmecasey.modjs.script.ScriptType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command editor on a plain Brigadier tree whose source is a String: "op" is level 4, anything else level 0. */
class CommandEditorTest {
    @TempDir
    Path root;

    private final List<String> replies = new ArrayList<>();

    private final CommandEditorEventJS.Env<String> env = new CommandEditorEventJS.Env<>() {
        @Override
        public boolean hasPermission(String source, int level) {
            return level <= 0 || source.equals("op");
        }

        @Override
        public Object player(String source) {
            return source;
        }

        @Override
        public void reply(String source, String text) {
            replies.add(text);
        }

        @Override
        public ArgumentType<?> argumentType(String type) {
            return null;
        }

        @Override
        public Object readArgument(CommandContext<String> context, String name, String type) {
            return null;
        }
    };

    public static final class TestEditor extends CommandEditorEventJS<String> {
        public TestEditor(CommandDispatcher<String> dispatcher, Env<String> env) {
            super(dispatcher, env);
        }
    }

    private CommandDispatcher<String> dispatcher;

    @BeforeEach
    void setUp() {
        ScriptManager.setRoot(root);
        for (ScriptType type : ScriptType.values()) {
            EventGroups.clear(type);
        }
        dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<String>literal("teleport").executes(c -> 1));
        dispatcher.register(LiteralArgumentBuilder.<String>literal("tp").redirect(dispatcher.getRoot().getChild("teleport")));
        dispatcher.register(LiteralArgumentBuilder.<String>literal("time")
            .then(LiteralArgumentBuilder.<String>literal("query").executes(c -> 1))
            .then(LiteralArgumentBuilder.<String>literal("set").executes(c -> 2)));
        dispatcher.register(LiteralArgumentBuilder.<String>literal("list").executes(c -> 3));
    }

    private void run(String script) throws IOException {
        Path file = root.resolve(ScriptType.SERVER.directory).resolve("commands.js");
        Files.createDirectories(file.getParent());
        Files.writeString(file, script);
        ScriptManager manager = ScriptManager.of(ScriptType.SERVER);
        manager.reload();
        assertTrue(manager.errors().isEmpty(), manager.errors().toString());
        EventGroups.SERVER.post("commandRegistry", new TestEditor(dispatcher, env));
    }

    @Test
    void addsACommandWithTypedArguments() throws Exception {
        run("ServerEvents.commandRegistry(e => e.add('say_twice <word:word> <n:integer:1:5>', ctx => { ctx.reply(ctx.arg('word') + ctx.arg('n')); return 4; }));");
        assertEquals(4, dispatcher.execute("say_twice hi 3", "anyone"));
        assertEquals(List.of("hi3"), replies);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("say_twice hi 9", "anyone"));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("say_twice hi", "anyone"));
    }

    @Test
    void addedCommandKeepsItsPermission() throws Exception {
        run("ServerEvents.commandRegistry(e => e.add('secret', { permission: 2, run: ctx => 1 }));");
        assertTrue(dispatcher.getRoot().getChild("secret").canUse("op"));
        assertFalse(dispatcher.getRoot().getChild("secret").canUse("someone"));
        assertEquals(1, dispatcher.execute("secret", "op"));
    }

    @Test
    void requiresFunctionDecidesWhoMayUseACommand() throws Exception {
        run("ServerEvents.commandRegistry(e => e.add('picky', { requires: player => player === 'friend', run: ctx => 1 }));");
        assertTrue(dispatcher.getRoot().getChild("picky").canUse("friend"));
        assertFalse(dispatcher.getRoot().getChild("picky").canUse("stranger"));
    }

    @Test
    void subCommandsShareOneRoot() throws Exception {
        run("ServerEvents.commandRegistry(e => { e.add('home set', ctx => 1); e.add('home go <name:word>', ctx => 2); });");
        assertEquals(1, dispatcher.execute("home set", "a"));
        assertEquals(2, dispatcher.execute("home go base", "a"));
    }

    @Test
    void removeDropsACommandAndItsAliases() throws Exception {
        run("ServerEvents.commandRegistry(e => e.remove('teleport'));");
        assertNull(dispatcher.getRoot().getChild("teleport"));
        assertNull(dispatcher.getRoot().getChild("tp"));
        assertNotNull(dispatcher.getRoot().getChild("time"));
    }

    @Test
    void removeDropsASubCommand() throws Exception {
        run("ServerEvents.commandRegistry(e => e.remove('time query'));");
        assertNull(dispatcher.getRoot().getChild("time").getChild("query"));
        assertEquals(2, dispatcher.execute("time set", "a"));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("time query", "a"));
    }

    @Test
    void removeReportsWhetherAnythingWasThere() throws Exception {
        run("ServerEvents.commandRegistry(e => { global.gone = e.remove('nope'); global.found = e.remove('list'); });");
        assertNull(dispatcher.getRoot().getChild("list"));
    }

    @Test
    void editChangesPermissionAndBehavior() throws Exception {
        run("ServerEvents.commandRegistry(e => { e.edit('list', { permission: 3 }); e.edit('time set', { run: ctx => 99 }); });");
        assertFalse(dispatcher.getRoot().getChild("list").canUse("someone"));
        assertTrue(dispatcher.getRoot().getChild("list").canUse("op"));
        assertEquals(99, dispatcher.execute("time set", "a"));
        assertEquals(1, dispatcher.execute("time query", "a"));
    }

    @Test
    void aliasAndRenameKeepTheCommandWorking() throws Exception {
        run("ServerEvents.commandRegistry(e => { e.alias('list', 'ls'); e.rename('time', 'clock'); });");
        assertEquals(3, dispatcher.execute("ls", "a"));
        assertNull(dispatcher.getRoot().getChild("time"));
        assertEquals(2, dispatcher.execute("clock set", "a"));
    }

    @Test
    void scriptFailuresBecomeCommandErrorsAndAreListed() throws Exception {
        run("ServerEvents.commandRegistry(e => e.add('boom', ctx => { throw new Error('bad'); }));");
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("boom", "a"));
        assertFalse(ScriptManager.of(ScriptType.SERVER).errors().isEmpty());
    }

    @Test
    void failEndsTheCommandWithAMessage() throws Exception {
        run("ServerEvents.commandRegistry(e => e.add('nope', ctx => ctx.fail('not allowed')));");
        CommandSyntaxException error = assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("nope", "a"));
        assertEquals("not allowed", error.getRawMessage().getString());
    }

    @Test
    void unknownCommandsAreClearErrors() {
        TestEditor editor = new TestEditor(dispatcher, env);
        assertThrows(IllegalArgumentException.class, () -> editor.edit("missing", null));
        assertThrows(IllegalArgumentException.class, () -> editor.alias("missing", "x"));
        assertTrue(editor.exists("time set"));
        assertFalse(editor.exists("time nothing"));
        assertTrue(editor.list().containsAll(List.of("teleport", "tp", "time", "list")));
    }
}
