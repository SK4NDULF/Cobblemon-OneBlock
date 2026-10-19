package io.github.sk4ndulf.cobblemon.oneblock.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationRunnerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
    private static final Migration TEST_V2 = new Migration(2, "test_table", "/cobblemon_oneblock/db/test-migrations/V0002__test_table.sql");

    @TempDir
    Path dir;

    private Path db() {
        return dir.resolve("oneblock.db");
    }

    private Connection open() throws Exception {
        return SqliteConnections.open(db(), 1000);
    }

    @Test
    void freshDatabaseGetsAllMigrationsWithoutBackup() throws Exception {
        try (Connection c = open()) {
            MigrationRunner.Report report = new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups"));
            assertEquals(0, report.fromVersion());
            assertEquals(1, report.toVersion());
            assertEquals(List.of(1), report.applied());
            assertNull(report.backup());
            assertEquals(MigrationRunner.APPLICATION_ID, Sql.pragmaLong(c, "application_id"));
            assertEquals(1, Sql.pragmaLong(c, "user_version"));
            assertTrue(Sql.tableExists(c, "operations"));
            assertFalse(Files.exists(dir.resolve("backups")));
        }
    }

    @Test
    void secondRunIsANoOp() throws Exception {
        try (Connection c = open()) {
            new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups"));
        }
        try (Connection c = open()) {
            MigrationRunner.Report report = new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups"));
            assertEquals(List.of(), report.applied());
            assertNull(report.backup());
        }
    }

    @Test
    void migratingAnExistingDatabaseCreatesAVerifiedBackupFirst() throws Exception {
        try (Connection c = open()) {
            new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups"));
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO world_meta (key, value) VALUES ('probe', 'before-v2')");
            }
        }
        List<Migration> withV2 = List.of(MigrationRunner.MIGRATIONS.get(0), TEST_V2);
        try (Connection c = open()) {
            MigrationRunner.Report report = new MigrationRunner(withV2, CLOCK).migrate(c, db(), dir.resolve("backups"));
            assertEquals(List.of(2), report.applied());
            assertNotNull(report.backup());
            assertTrue(Files.exists(report.backup()));
            assertTrue(report.backup().getFileName().toString().startsWith("oneblock-v1-20261019-120000"));
            assertTrue(Sql.tableExists(c, "test_v2"));
        }
        // The backup is a complete, readable v1 database with the data from before the migration.
        Path backup;
        try (var files = Files.list(dir.resolve("backups"))) {
            backup = files.findFirst().orElseThrow();
        }
        try (Connection b = DriverManager.getConnection("jdbc:sqlite:" + backup)) {
            assertEquals(1, Sql.pragmaLong(b, "user_version"));
            assertFalse(Sql.tableExists(b, "test_v2"));
            assertEquals("before-v2", WorldBinding.meta(b, "probe"));
        }
    }

    @Test
    void newerSchemaIsRefused() throws Exception {
        List<Migration> withV2 = List.of(MigrationRunner.MIGRATIONS.get(0), TEST_V2);
        try (Connection c = open()) {
            new MigrationRunner(withV2, CLOCK).migrate(c, db(), dir.resolve("backups"));
        }
        try (Connection c = open()) {
            StorageException e = assertThrows(StorageException.class,
                    () -> new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups")));
            assertTrue(e.recoveryRequired());
            assertTrue(e.getMessage().contains("newer than this mod"), e.getMessage());
        }
    }

    @Test
    void modifiedAppliedMigrationIsRefused() throws Exception {
        try (Connection c = open()) {
            new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups"));
            try (Statement s = c.createStatement()) {
                s.execute("UPDATE schema_migrations SET checksum = 'tampered' WHERE version = 1");
            }
        }
        try (Connection c = open()) {
            StorageException e = assertThrows(StorageException.class,
                    () -> new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups")));
            assertTrue(e.recoveryRequired());
            assertTrue(e.getMessage().contains("different checksum"), e.getMessage());
        }
    }

    @Test
    void foreignDatabasesAreRefused() throws Exception {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE someone_elses (x INTEGER)");
            StorageException e = assertThrows(StorageException.class,
                    () -> new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups")));
            assertTrue(e.recoveryRequired());
        }
        Path other = dir.resolve("other.db");
        try (Connection c = SqliteConnections.open(other, 1000); Statement s = c.createStatement()) {
            s.execute("PRAGMA application_id = 12345");
            StorageException e = assertThrows(StorageException.class,
                    () -> new MigrationRunner(CLOCK).migrate(c, other, dir.resolve("backups")));
            assertTrue(e.getMessage().contains("another application"), e.getMessage());
        }
    }

    @Test
    void userVersionMismatchIsRefused() throws Exception {
        try (Connection c = open()) {
            new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups"));
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA user_version = 7");
            }
            StorageException e = assertThrows(StorageException.class,
                    () -> new MigrationRunner(CLOCK).migrate(c, db(), dir.resolve("backups")));
            assertTrue(e.recoveryRequired());
        }
    }

    @Test
    void statementSplitterAndBundledScripts() {
        assertEquals(List.of("CREATE TABLE a ( x INTEGER )", "CREATE INDEX i ON a (x)"),
                Migration.statements("-- c\nCREATE TABLE a (\n x INTEGER -- note\n);\n\nCREATE INDEX i ON a (x);\n")
                        .stream().map(st -> st.replaceAll("\\s+", " ")).toList());
        assertThrows(IllegalArgumentException.class, () -> Migration.statements("CREATE TABLE a (x INTEGER)"));
        for (Migration m : MigrationRunner.MIGRATIONS) {
            List<String> statements = Migration.statements(m.sql());
            assertFalse(statements.isEmpty(), m.name());
            for (String st : statements) {
                assertFalse(st.contains(";"), m.name() + ": semicolon inside a statement is not supported: " + st);
                assertFalse(st.contains("'--") || st.contains("--'"), m.name());
            }
            assertEquals(64, m.checksum().length());
        }
    }

    @Test
    void checksumIgnoresLineEndingStyle() {
        Migration m = MigrationRunner.MIGRATIONS.get(0);
        assertEquals(m.checksum(), m.checksum());
        assertFalse(m.sql().contains("\r"));
    }
}
