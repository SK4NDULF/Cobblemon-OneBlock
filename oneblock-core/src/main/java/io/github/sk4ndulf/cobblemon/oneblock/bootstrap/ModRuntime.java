package io.github.sk4ndulf.cobblemon.oneblock.bootstrap;

import io.github.sk4ndulf.cobblemon.oneblock.cobblemon.ShutdownRecall;
import io.github.sk4ndulf.cobblemon.oneblock.config.ConfigIssue;
import io.github.sk4ndulf.cobblemon.oneblock.config.ConfigLoader;
import io.github.sk4ndulf.cobblemon.oneblock.config.ConfigManager;
import io.github.sk4ndulf.cobblemon.oneblock.diagnostics.Diagnostic;
import io.github.sk4ndulf.cobblemon.oneblock.diagnostics.Diagnostics;
import io.github.sk4ndulf.cobblemon.oneblock.diagnostics.ErrorClass;
import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationReconciler;
import io.github.sk4ndulf.cobblemon.oneblock.storage.DbExecutor;
import io.github.sk4ndulf.cobblemon.oneblock.storage.StorageBootstrap;
import io.github.sk4ndulf.cobblemon.oneblock.storage.StorageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Everything that lives exactly as long as one server run (boot order from spec 13):
 * config → storage (async on the DB thread) → lifecycle decision on the server thread. Shutdown: refuse new work →
 * recall owned Pokémon (ADR 005) → drain and close the database.
 */
public final class ModRuntime {
    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");
    /** Bound on recovery reasons shown/kept, so a corrupt journal cannot flood the status output. */
    private static final int MAX_REASONS = 10;

    private final MinecraftServer server;
    private final String modVersion;
    private final Clock clock = Clock.systemUTC();
    private final Lifecycle lifecycle = new Lifecycle();
    private final Diagnostics diagnostics = new Diagnostics(clock);
    private final ConfigManager config;
    private final ExecutorService io;
    private volatile DbExecutor db;
    private volatile StorageBootstrap.Report storage;
    private volatile Diagnostic configProblem;

    private ModRuntime(MinecraftServer server, Path configDir, String modVersion) {
        this.server = server;
        this.modVersion = modVersion;
        this.config = new ConfigManager(configDir);
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "cobblemon-oneblock-io");
            t.setDaemon(true);
            return t;
        });
    }

    /** Called from SERVER_STARTING on the server thread, before the first tick. */
    public static ModRuntime start(MinecraftServer server, Path configDir, String modVersion) {
        ModRuntime runtime = new ModRuntime(server, configDir, modVersion);
        runtime.boot();
        return runtime;
    }

    private void boot() {
        // Boot happens before the first tick, so reading the small config file here does not stall gameplay.
        ConfigManager.Outcome loaded = config.loadAtBoot();
        if (!loaded.errors().isEmpty()) {
            configProblem = diagnostics.report(ErrorClass.CONFIG, "config invalid: " + summarize(loaded.errors()));
        }

        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        long seed = server.getWorldData().worldGenOptions().seed();
        db = new DbExecutor(config.current().dbQueueCapacity());
        new StorageBootstrap(clock, modVersion, OperationReconciler.withoutHandlers())
                .start(db, worldRoot, seed, config.current().dbBusyTimeoutMs())
                .whenCompleteAsync(this::onStorageReady, server);
    }

    /** Server thread. Any unexpected failure here must still end in a defined state, never in a lost exception. */
    private void onStorageReady(StorageBootstrap.Report report, Throwable error) {
        try {
            decideAfterStorage(report, error);
        } catch (RuntimeException e) {
            lifecycle.transition(LifecycleState.RECOVERY_MODE,
                    List.of(diagnostics.report(ErrorClass.UNKNOWN, "startup decision failed", e)));
        }
    }

    private void decideAfterStorage(StorageBootstrap.Report report, Throwable error) {
        if (lifecycle.state() == LifecycleState.STOPPING) {
            return;
        }
        if (error != null) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            // A deliberate StorageException explains itself; a stack trace is only useful for unexpected failures.
            boolean expected = cause instanceof StorageException && cause.getCause() == null;
            String message = cause instanceof StorageException ? cause.getMessage() : "database could not be opened: " + cause;
            lifecycle.transition(LifecycleState.RECOVERY_MODE,
                    List.of(diagnostics.report(ErrorClass.STORAGE, message, expected ? null : cause)));
            return;
        }
        storage = report;
        LOG.info("Database ready: {} (schema v{}, journal {}, installation {}{}{})", report.dbFile(),
                report.migrations().toVersion(), report.settings().journalMode(), report.installationId(),
                report.freshlyCreated() ? ", new" : "",
                report.migrations().backup() != null ? ", backup " + report.migrations().backup() : "");
        if (!report.blockingOperations().isEmpty()) {
            List<Diagnostic> reasons = new ArrayList<>();
            report.blockingOperations().stream().limit(MAX_REASONS).forEach(finding -> reasons.add(diagnostics.report(
                    ErrorClass.STORAGE, "unfinished operation " + finding.operation().operationId() + ": " + finding.problem())));
            lifecycle.transition(LifecycleState.RECOVERY_MODE, reasons);
            return;
        }
        recomputeState();
    }

    /** SETUP_PENDING/READY depending on config validity and setup flag. Server thread. */
    private void recomputeState() {
        LifecycleState current = lifecycle.state();
        if (current != LifecycleState.STARTING && current != LifecycleState.SETUP_PENDING && current != LifecycleState.READY) {
            return;
        }
        if (storage == null) {
            return;
        }
        if (!config.valid()) {
            lifecycle.transition(LifecycleState.SETUP_PENDING, configProblem == null ? List.of() : List.of(configProblem));
        } else if (!storage.setupCompleted()) {
            lifecycle.transition(LifecycleState.SETUP_PENDING, List.of());
        } else {
            lifecycle.transition(LifecycleState.READY, List.of());
        }
    }

    /**
     * {@code /ob reload}: reads and validates on the I/O thread, applies on the server thread.
     * The returned future always completes on the server thread, also when reading failed.
     */
    public CompletableFuture<ConfigManager.Outcome> reloadConfig() {
        return CompletableFuture.supplyAsync(config::read, io)
                .handleAsync((result, error) -> {
                    if (error != null) {
                        throw new CompletionException(error);
                    }
                    return applyReload(result);
                }, server);
    }

    private ConfigManager.Outcome applyReload(ConfigLoader.Result result) {
        if (lifecycle.state() == LifecycleState.STOPPING) {
            throw new IllegalStateException("server is stopping");
        }
        // Phase 1 has no islands yet, so world-locked values are still changeable.
        ConfigManager.Outcome outcome = config.apply(result, false);
        if (outcome.applied()) {
            configProblem = null;
            recomputeState();
        } else {
            diagnostics.report(ErrorClass.CONFIG, "reload rejected: " + summarize(outcome.errors()));
        }
        return outcome;
    }

    /** Called from SERVER_STOPPING on the server thread. */
    public void stop() {
        lifecycle.transition(LifecycleState.STOPPING, List.of());
        try {
            int recalled = ShutdownRecall.recallOwnedPokemon(server);
            if (recalled > 0) {
                LOG.info("Recalled {} sent-out Pokémon before saving the worlds (ADR 005)", recalled);
            }
        } catch (RuntimeException e) {
            diagnostics.report(ErrorClass.COBBLEMON, "recalling sent-out Pokémon at shutdown failed", e);
        }
        DbExecutor database = db;
        if (database != null) {
            Duration timeout = Duration.ofSeconds(config.current().shutdownTimeoutSeconds());
            if (!database.shutdown(timeout)) {
                diagnostics.report(ErrorClass.STORAGE, "database did not finish its queued work within " + timeout.toSeconds()
                        + " s; the last queued changes may be missing");
            } else {
                LOG.info("Database closed cleanly");
            }
        }
        io.shutdownNow();
    }

    private static String summarize(List<ConfigIssue> issues) {
        return issues.stream().limit(3).map(ConfigIssue::toString).reduce((a, b) -> a + "; " + b).orElse("")
                + (issues.size() > 3 ? " (+" + (issues.size() - 3) + " more)" : "");
    }

    public Lifecycle lifecycle() {
        return lifecycle;
    }

    public Diagnostics diagnostics() {
        return diagnostics;
    }

    public ConfigManager config() {
        return config;
    }

    public StorageBootstrap.Report storage() {
        return storage;
    }

    public DbExecutor db() {
        return db;
    }

    public String modVersion() {
        return modVersion;
    }
}
