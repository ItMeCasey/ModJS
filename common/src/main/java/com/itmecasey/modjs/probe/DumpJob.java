package com.itmecasey.modjs.probe;

import com.itmecasey.modjs.ModJSInfo;
import com.itmecasey.modjs.script.PlatformBinding;
import com.itmecasey.modjs.script.ScriptManager;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * One dump run. The steps that read live game objects run on the server thread in slices of a few milliseconds, with a pause
 * between slices so the game keeps ticking; file scans, typings and writing run on the dump's own thread.
 */
public final class DumpJob {
    /** A piece of work; {@link #run()} returns true when it is finished and may be called again until then. */
    interface Step {
        String name();

        boolean run();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("modjs");
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final Timer TIMER = new Timer("ModJS dump pacing", true);
    private static final long SLICE_NANOS = 8_000_000L;
    private static final long PAUSE_MILLIS = 40L;
    private static final long REPORT_NANOS = 1_500_000_000L;

    private final MinecraftServer server;
    private final Consumer<String> say;
    private final DumpData data = new DumpData();
    private final List<String> warnings = new ArrayList<>();
    private final Deque<Step> steps = new ArrayDeque<>();
    private int total;
    private long lastReport;
    private final long startedAt = System.nanoTime();

    private DumpJob(MinecraftServer server, Consumer<String> say) {
        this.server = server;
        this.say = say;
    }

    /** Starts a dump; {@code say} is called on the server thread with plain progress lines. Returns false when one is already running. */
    public static boolean start(MinecraftServer server, Consumer<String> say) {
        if (!RUNNING.compareAndSet(false, true)) {
            say.accept("A dump is already running.");
            return false;
        }
        try {
            new DumpJob(server, say).begin();
            return true;
        } catch (RuntimeException e) {
            RUNNING.set(false);
            LOGGER.error("Could not start the dump", e);
            say.accept("The dump could not start: " + e);
            return false;
        }
    }

    private void begin() {
        PlatformBinding platform = new PlatformBinding();
        data.minecraftVersion = platform.getMcVersion();
        data.loader = platform.getLoader();
        data.generatedAt = Instant.now().toString();
        data.mods.addAll(DumpHooks.mods());
        for (DumpData.Mod mod : data.mods) {
            if (mod.id().equals(ModJSInfo.MOD_ID)) {
                data.modjsVersion = mod.version();
            }
        }
        steps.addAll(GameCollector.registrySteps(server, data));
        steps.add(GameCollector.commandStep(server, data));
        steps.add(GameCollector.gameRuleStep(data));
        total = steps.size();
        say.accept("Dump started: " + total + " steps.");
        lastReport = System.nanoTime();
        slice();
    }

    private void slice() {
        long begin = System.nanoTime();
        try {
            while (!steps.isEmpty() && System.nanoTime() - begin < SLICE_NANOS) {
                Step step = steps.peek();
                boolean done;
                try {
                    done = step.run();
                } catch (RuntimeException | LinkageError e) {
                    LOGGER.warn("Dump step {} failed", step.name(), e);
                    warnings.add(step.name() + ": " + e);
                    done = true;
                }
                if (done) {
                    steps.poll();
                }
            }
            if (steps.isEmpty()) {
                startWriter();
                return;
            }
            long now = System.nanoTime();
            if (now - lastReport > REPORT_NANOS) {
                lastReport = now;
                say.accept("Dump: " + (total - steps.size()) + " of " + total + " steps.");
            }
            TIMER.schedule(new TimerTask() {
                @Override
                public void run() {
                    server.execute(DumpJob.this::slice);
                }
            }, PAUSE_MILLIS);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void startWriter() {
        say.accept("Dump: reading data packs and resources, then writing files.");
        Thread thread = new Thread(this::writeAll, "ModJS dump");
        thread.setDaemon(true);
        thread.start();
    }

    private void writeAll() {
        try {
            GameCollector.dataPackScan(server.getResourceManager(), data, warnings);
            data.side = "server";
            if (new PlatformBinding().isClient()) {
                try {
                    ClientCollector.collect(data, warnings);
                    data.side = "client+server";
                } catch (RuntimeException | LinkageError e) {
                    LOGGER.warn("Client part of the dump failed", e);
                    warnings.add("client: " + e);
                }
            }
            Path root = ScriptManager.root();
            ProbeWriter writer = new ProbeWriter(root.resolve("probe"));
            String curated = "";
            try (var in = DumpJob.class.getResourceAsStream("/modjs/modjs.d.ts")) {
                if (in != null) {
                    curated = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            var classes = ApiReflector.scan(ModJSInfo.class);
            if (classes.isEmpty()) {
                warnings.add("api.d.ts skipped: no script classes could be listed");
            }
            List<String> written = DumpOutput.write(writer, root, data, warnings, curated, classes);
            long millis = (System.nanoTime() - startedAt) / 1_000_000L;
            String done = "Dump written to " + writer.root() + ": " + data.registries.size() + " registries, " + data.totalIds() + " ids, "
                + data.recipes.size() + " recipes, " + written.size() + " files in " + millis + " ms"
                + (data.hasClientData ? "." : " (server data only).");
            finish(done);
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    private void finish(String message) {
        LOGGER.info(message);
        server.execute(() -> {
            say.accept(message);
            for (String warning : warnings) {
                say.accept("Dump warning: " + warning);
            }
            RUNNING.set(false);
        });
    }

    private void fail(Throwable e) {
        LOGGER.error("Dump failed", e);
        server.execute(() -> {
            say.accept("Dump failed: " + e);
            RUNNING.set(false);
        });
    }
}
