package io.github.sk4ndulf.cobblemon.oneblock.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Small JDBC helpers. Everything here runs on the DB thread. */
public final class Sql {
    private Sql() {
    }

    @FunctionalInterface
    public interface TxBody<T> {
        T run(Connection connection) throws SQLException;
    }

    /** Runs {@code body} in one transaction; commits on success, rolls back on any failure. */
    public static <T> T inTransaction(Connection c, TxBody<T> body) throws SQLException {
        boolean previous = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = body.run(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException | Error e) {
            try {
                c.rollback();
            } catch (SQLException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            c.setAutoCommit(previous);
        }
    }

    public static String pragma(Connection c, String pragma) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("PRAGMA " + pragma)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    public static long pragmaLong(Connection c, String pragma) throws SQLException {
        String value = pragma(c, pragma);
        return value == null ? 0 : Long.parseLong(value);
    }

    public static boolean tableExists(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM sqlite_schema WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Number of user tables (excluding sqlite_* internals). */
    public static int userTableCount(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM sqlite_schema WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static long count(Connection c, String table) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }
}
