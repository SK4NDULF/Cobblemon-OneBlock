package io.github.sk4ndulf.cobblemon.oneblock.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies {@link #MIGRATIONS} in order. Runs on the DB thread.
 *
 * <p>Safety rules: an applied migration whose checksum changed, a gap in the applied versions, or a schema newer than
 * this mod all stop the start ({@code recoveryRequired}). Before migrating an existing database a consistent copy is
 * written with {@code VACUUM INTO} and verified; without a verified backup nothing is migrated.
 */
public final class MigrationRunner {
    /** 'OBCM' - marks the file as a Cobblemon OneBlock database. */
    public static final int APPLICATION_ID = 0x4F42434D;

    public static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "initial", Migration.RESOURCE_DIR + "V0001__initial.sql"));

    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public record Report(int fromVersion, int toVersion, List<Integer> applied, Path backup) {
        public Report {
            applied = List.copyOf(applied);
        }
    }

    private final List<Migration> migrations;
    private final Clock clock;

    public MigrationRunner(Clock clock) {
        this(MIGRATIONS, clock);
    }

    MigrationRunner(List<Migration> migrations, Clock clock) {
        this.migrations = List.copyOf(migrations);
        this.clock = clock;
        for (int i = 0; i < this.migrations.size(); i++) {
            if (this.migrations.get(i).version() != i + 1) {
                throw new IllegalArgumentException("migrations must be numbered 1..n without gaps");
            }
        }
    }

    public int latestVersion() {
        return migrations.size();
    }

    public Report migrate(Connection c, Path dbFile, Path backupDir) throws SQLException {
        checkApplicationId(c);
        Map<Integer, String> applied = readApplied(c);
        int current = applied.size();
        for (Map.Entry<Integer, String> entry : applied.entrySet()) {
            int version = entry.getKey();
            if (version > migrations.size()) {
                throw new StorageException("database schema version " + version + " is newer than this mod (knows up to "
                        + migrations.size() + "). Downgrades are not supported; restore a backup or use the newer mod.", true);
            }
            Migration known = migrations.get(version - 1);
            if (!known.checksum().equals(entry.getValue())) {
                throw new StorageException("applied migration V" + version + " (" + known.name()
                        + ") has a different checksum than the bundled script. The schema cannot be trusted.", true);
            }
        }
        for (int v = 1; v <= current; v++) {
            if (!applied.containsKey(v)) {
                throw new StorageException("applied migrations have a gap at V" + v, true);
            }
        }
        long userVersion = Sql.pragmaLong(c, "user_version");
        if (userVersion != current) {
            throw new StorageException("PRAGMA user_version " + userVersion + " does not match " + current
                    + " applied migrations", true);
        }
        if (current == migrations.size()) {
            return new Report(current, current, List.of(), null);
        }

        Path backup = null;
        if (current > 0) {
            backup = backup(c, dbFile, backupDir, current);
        }
        List<Integer> done = new ArrayList<>();
        for (Migration m : migrations.subList(current, migrations.size())) {
            apply(c, m);
            done.add(m.version());
            LOG.info("Applied database migration V{} {}", m.version(), m.name());
        }
        return new Report(current, migrations.size(), done, backup);
    }

    private void checkApplicationId(Connection c) throws SQLException {
        long appId = Sql.pragmaLong(c, "application_id");
        if (appId == APPLICATION_ID) {
            return;
        }
        if (appId != 0) {
            throw new StorageException("database file belongs to another application (application_id " + appId + ")", true);
        }
        if (Sql.userTableCount(c) > 0) {
            throw new StorageException("database file is not empty but has no Cobblemon OneBlock marker", true);
        }
    }

    private static Map<Integer, String> readApplied(Connection c) throws SQLException {
        Map<Integer, String> applied = new LinkedHashMap<>();
        if (!Sql.tableExists(c, "schema_migrations")) {
            return applied;
        }
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT version, checksum FROM schema_migrations ORDER BY version")) {
            while (rs.next()) {
                applied.put(rs.getInt(1), rs.getString(2));
            }
        }
        return applied;
    }

    private void apply(Connection c, Migration m) throws SQLException {
        String checksum = m.checksum();
        List<String> statements = Migration.statements(m.sql());
        Sql.inTransaction(c, tx -> {
            try (Statement s = tx.createStatement()) {
                if (m.version() == 1) {
                    s.execute("PRAGMA application_id = " + APPLICATION_ID);
                    s.execute("CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, "
                            + "checksum TEXT NOT NULL, applied_at INTEGER NOT NULL) STRICT");
                }
                for (String statement : statements) {
                    s.execute(statement);
                }
            }
            try (PreparedStatement ps = tx.prepareStatement(
                    "INSERT INTO schema_migrations (version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, m.version());
                ps.setString(2, m.name());
                ps.setString(3, checksum);
                ps.setLong(4, clock.millis());
                ps.executeUpdate();
            }
            try (Statement s = tx.createStatement()) {
                s.execute("PRAGMA user_version = " + m.version());
            }
            return null;
        });
    }

    /** Consistent online copy of the whole database, verified before any migration runs. */
    Path backup(Connection c, Path dbFile, Path backupDir, int version) throws SQLException {
        try {
            Files.createDirectories(backupDir);
        } catch (java.io.IOException e) {
            throw new StorageException("cannot create backup directory " + backupDir, e, false);
        }
        String base = dbFile.getFileName().toString().replaceFirst("\\.db$", "");
        Path target = backupDir.resolve(base + "-v" + version + "-" + BACKUP_STAMP.format(clock.instant()) + ".db");
        if (Files.exists(target)) {
            throw new StorageException("backup target already exists: " + target, false);
        }
        try (PreparedStatement ps = c.prepareStatement("VACUUM INTO ?")) {
            ps.setString(1, target.toAbsolutePath().toString());
            ps.execute();
        }
        org.sqlite.SQLiteConfig readOnly = new org.sqlite.SQLiteConfig();
        readOnly.setReadOnly(true);
        try (Connection check = DriverManager.getConnection("jdbc:sqlite:" + target.toAbsolutePath(), readOnly.toProperties())) {
            String integrity = Sql.pragma(check, "integrity_check");
            long backupVersion = Sql.pragmaLong(check, "user_version");
            if (!"ok".equals(integrity) || backupVersion != version) {
                throw new StorageException("backup verification failed for " + target + " (integrity " + integrity
                        + ", version " + backupVersion + ")", false);
            }
        }
        LOG.info("Database backup before migration: {}", target);
        return target;
    }
}
