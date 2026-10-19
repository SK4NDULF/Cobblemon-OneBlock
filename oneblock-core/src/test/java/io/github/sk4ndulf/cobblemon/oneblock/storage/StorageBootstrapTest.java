package io.github.sk4ndulf.cobblemon.oneblock.storage;

import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationJournal;
import io.github.sk4ndulf.cobblemon.oneblock.operation.OperationReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageBootstrapTest {
    private static final long SEED = 4242L;

    @TempDir
    Path world;

    private StorageBootstrap.Report boot(long seed) throws Exception {
        return boot(seed, OperationReconciler.withoutHandlers());
    }

    private StorageBootstrap.Report boot(long seed, OperationReconciler reconciler) throws Exception {
        DbExecutor db = new DbExecutor(64);
        try {
            return new StorageBootstrap(Clock.systemUTC(), "test", reconciler).start(db, world, seed, 1000).get(30, TimeUnit.SECONDS);
        } finally {
            db.shutdown(Duration.ofSeconds(10));
        }
    }

    private StorageException bootFails(long seed) {
        ExecutionException e = assertThrows(ExecutionException.class, () -> boot(seed));
        return assertInstanceOf(StorageException.class, e.getCause());
    }

    private Path marker() {
        return world.resolve(WorldBinding.MARKER_FILE);
    }

    @Test
    void freshWorldCreatesDatabaseAndMarker() throws Exception {
        StorageBootstrap.Report report = boot(SEED);
        assertTrue(report.freshlyCreated());
        assertFalse(report.setupCompleted());
        assertEquals(StorageBootstrap.dbFile(world).toAbsolutePath().normalize(), report.dbFile());
        assertTrue(Files.exists(report.dbFile()));
        assertTrue(Files.readString(marker()).contains(report.installationId().toString()));
        assertEquals("wal", report.settings().journalMode());
        assertEquals("2", report.settings().synchronous(), "synchronous=FULL");
        assertTrue(report.settings().foreignKeys());
    }

    @Test
    void restartKeepsTheSameInstallation() throws Exception {
        UUID first = boot(SEED).installationId();
        StorageBootstrap.Report second = boot(SEED);
        assertFalse(second.freshlyCreated());
        assertEquals(first, second.installationId());
        assertEquals(java.util.List.of(), second.migrations().applied());
    }

    @Test
    void databaseFromAnotherWorldIsRefused() throws Exception {
        boot(SEED);
        StorageException e = bootFails(SEED + 1);
        assertTrue(e.recoveryRequired());
        assertTrue(e.getMessage().contains("seed"), e.getMessage());
    }

    @Test
    void missingDatabaseWithMarkerIsRefusedWithoutCreatingAnEmptyOne() throws Exception {
        StorageBootstrap.Report report = boot(SEED);
        Path dbDir = report.dbFile().getParent();
        try (var files = Files.list(dbDir)) {
            for (Path p : files.toList()) {
                Files.delete(p);
            }
        }
        StorageException e = bootFails(SEED);
        assertTrue(e.recoveryRequired());
        assertFalse(Files.exists(report.dbFile()), "no empty replacement database may be created");
    }

    @Test
    void missingMarkerIsRecreatedWhenTheSeedMatches() throws Exception {
        UUID id = boot(SEED).installationId();
        Files.delete(marker());
        StorageBootstrap.Report report = boot(SEED);
        assertTrue(report.markerRewritten());
        assertEquals(id, report.installationId());
        assertTrue(Files.readString(marker()).contains(id.toString()));
    }

    @Test
    void markerFromAnotherInstallationIsRefused() throws Exception {
        boot(SEED);
        Files.writeString(marker(), "installation_id=" + UUID.randomUUID() + "\n", StandardCharsets.UTF_8);
        StorageException e = bootFails(SEED);
        assertTrue(e.getMessage().contains("does not match the marker"), e.getMessage());
    }

    @Test
    void garbageDatabaseFileFailsAndIsNotReplaced() throws Exception {
        Path dbFile = StorageBootstrap.dbFile(world);
        Files.createDirectories(dbFile.getParent());
        byte[] garbage = "this is not an sqlite database, it is just text that is long enough".repeat(20).getBytes(StandardCharsets.UTF_8);
        Files.write(dbFile, garbage);
        assertThrows(ExecutionException.class, () -> boot(SEED));
        assertTrue(java.util.Arrays.equals(garbage, Files.readAllBytes(dbFile)), "the broken file must stay untouched");
    }

    @Test
    void openOperationWithoutHandlerBlocksStartup() throws Exception {
        StorageBootstrap.Report first = boot(SEED);
        try (Connection c = SqliteConnections.open(first.dbFile(), 1000)) {
            new OperationJournal(Clock.systemUTC()).begin(c,
                    new OperationJournal.Request("req-1", "CREATE", null, "player:test", "RESERVED", "{}"));
        }
        StorageBootstrap.Report second = boot(SEED);
        assertEquals(1, second.openOperations().size());
        assertEquals(1, second.blockingOperations().size());
        assertTrue(second.blockingOperations().get(0).problem().contains("no handler"));

        StorageBootstrap.Report withHandler = boot(SEED, new OperationReconciler(Set.of("CREATE")));
        assertEquals(0, withHandler.blockingOperations().size());
    }
}
