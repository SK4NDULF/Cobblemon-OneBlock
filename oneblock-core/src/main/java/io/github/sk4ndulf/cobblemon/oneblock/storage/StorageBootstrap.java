package io.github.sk4ndulf.cobblemon.oneblock.storage;

import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationJournal;
import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationReconciler;
import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Opens and checks the world database (spec 13 boot order, step 3) entirely on the DB thread:
 * open with PRAGMAs → integrity check → migrations (with verified backup) → world binding → open operations.
 * Never deletes or recreates an existing database file.
 */
public final class StorageBootstrap {
    public static final String DB_DIR = "data/cobblemon_oneblock";
    public static final String DB_FILE = "oneblock.db";
    public static final String BACKUP_DIR = "backups";

    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");

    /** Everything the server thread needs to decide the lifecycle state. Immutable. */
    public record Report(
            Path dbFile,
            SqliteConnections.Settings settings,
            MigrationRunner.Report migrations,
            UUID installationId,
            boolean freshlyCreated,
            boolean markerRewritten,
            boolean setupCompleted,
            List<OperationRecord> openOperations,
            List<OperationReconciler.Finding> blockingOperations) {
        public Report {
            openOperations = List.copyOf(openOperations);
            blockingOperations = List.copyOf(blockingOperations);
        }
    }

    private final Clock clock;
    private final String modVersion;
    private final OperationReconciler reconciler;

    public StorageBootstrap(Clock clock, String modVersion, OperationReconciler reconciler) {
        this.clock = clock;
        this.modVersion = modVersion;
        this.reconciler = reconciler;
    }

    public static Path dbFile(Path worldRoot) {
        return worldRoot.resolve(DB_DIR).resolve(DB_FILE);
    }

    /**
     * @return a future completed on the DB thread; fails with {@link StorageException} (check
     *         {@link StorageException#recoveryRequired()}) or another exception for unexpected I/O problems
     */
    public CompletableFuture<Report> start(DbExecutor db, Path worldRoot, long worldSeed, int busyTimeoutMs) {
        Path dbFile = dbFile(worldRoot).toAbsolutePath().normalize();
        // Checked before opening: SQLite would create an empty file.
        boolean dbExisted = Files.exists(dbFile);
        if (!dbExisted && Files.exists(worldRoot.resolve(WorldBinding.MARKER_FILE))) {
            return CompletableFuture.failedFuture(new StorageException("this world was set up for Cobblemon OneBlock (marker "
                    + WorldBinding.MARKER_FILE + ") but its database " + dbFile + " is missing. Restore it from a backup. "
                    + "No new empty database is created, because that would silently forget all islands.", true));
        }
        return db.open(() -> {
                    try {
                        Files.createDirectories(dbFile.getParent());
                    } catch (IOException e) {
                        throw new StorageException("cannot create database directory " + dbFile.getParent(), e, false);
                    }
                    return SqliteConnections.open(dbFile, busyTimeoutMs);
                })
                .thenCompose(ignored -> db.submit("storage-bootstrap", c -> run(c, dbFile, dbExisted, worldRoot, worldSeed)));
    }

    private Report run(Connection c, Path dbFile, boolean dbExisted, Path worldRoot, long worldSeed) throws SQLException {
        String quick = Sql.pragma(c, "quick_check");
        if (!"ok".equals(quick)) {
            throw new StorageException("database integrity check failed: " + quick, true);
        }
        SqliteConnections.Settings settings = SqliteConnections.settings(c);
        if (!"wal".equalsIgnoreCase(settings.journalMode())) {
            LOG.warn("SQLite refused WAL mode (journal_mode={}); continuing with the safer default journal", settings.journalMode());
        }
        MigrationRunner.Report migrations = new MigrationRunner(clock)
                .migrate(c, dbFile, dbFile.getParent().resolve(BACKUP_DIR));
        WorldBinding.Result binding = new WorldBinding(clock, modVersion).bind(c, worldRoot, worldSeed, dbExisted);
        List<OperationRecord> open = new OperationJournal(clock).listOpen(c);
        List<OperationReconciler.Finding> blocking = reconciler.blocking(open);
        return new Report(dbFile, settings, migrations, binding.installationId(), binding.freshlyCreated(),
                binding.markerRewritten(), binding.setupCompleted(), open, blocking);
    }
}
