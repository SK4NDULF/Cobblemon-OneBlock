package io.github.sk4ndulf.cobblemon.oneblock.operation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Append-only audit trail for sensitive actions (spec 11 §5, spec 12 §18). Runs on the DB thread. */
public final class AuditLog {
    public enum Outcome {
        SUCCESS,
        DENIED,
        FAILED,
        PENDING
    }

    public record Entry(String actor, String action, String target, UUID operationId, Outcome outcome, String reason,
                        String details) {
        public Entry {
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    private final Clock clock;

    public AuditLog(Clock clock) {
        this.clock = clock;
    }

    public void append(Connection c, Entry entry) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO audit_log (at, actor, action, target, operation_id, "
                + "outcome, reason, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, clock.millis());
            ps.setString(2, entry.actor());
            ps.setString(3, entry.action());
            ps.setString(4, entry.target());
            ps.setString(5, entry.operationId() == null ? null : entry.operationId().toString());
            ps.setString(6, entry.outcome().name());
            ps.setString(7, entry.reason());
            ps.setString(8, entry.details());
            ps.executeUpdate();
        }
    }
}
