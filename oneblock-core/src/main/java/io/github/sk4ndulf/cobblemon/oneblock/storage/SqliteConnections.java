package io.github.sk4ndulf.cobblemon.oneblock.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens the world database with the PRAGMAs from ADR 004. */
public final class SqliteConnections {
    /** What the connection actually ended up with; SQLite may refuse WAL on some file systems. */
    public record Settings(String journalMode, String synchronous, boolean foreignKeys, int busyTimeoutMs) {
    }

    private SqliteConnections() {
    }

    public static Connection open(Path dbFile, int busyTimeoutMs) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout = " + busyTimeoutMs);
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA synchronous = FULL");
            s.executeQuery("PRAGMA journal_mode = WAL").close();
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }

    public static Settings settings(Connection c) throws SQLException {
        return new Settings(
                Sql.pragma(c, "journal_mode"),
                Sql.pragma(c, "synchronous"),
                "1".equals(Sql.pragma(c, "foreign_keys")),
                Integer.parseInt(Sql.pragma(c, "busy_timeout")));
    }
}
