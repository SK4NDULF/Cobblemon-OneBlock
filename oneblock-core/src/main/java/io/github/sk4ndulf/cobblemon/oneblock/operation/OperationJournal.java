package io.github.sk4ndulf.cobblemon.oneblock.operation;

import io.github.sk4ndulf.cobblemon.oneblock.storage.Sql;
import io.github.sk4ndulf.cobblemon.oneblock.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable operation journal (spec 11, CLAUDE.md rule 4). All methods run on the DB thread and take the connection
 * explicitly; each call is one transaction.
 *
 * <ul>
 *   <li>{@link #begin}: idempotent per {@code requestId}. A retry with the same request returns the existing operation;
 *       reusing a request id for a different type or actor is rejected.</li>
 *   <li>{@link #advance}: optimistic. It only succeeds if the operation is still {@code PENDING} and at the expected
 *       revision; every successful advance increments the revision.</li>
 *   <li>Rows are never deleted.</li>
 * </ul>
 */
public final class OperationJournal {
    public record Request(String requestId, String type, UUID islandId, String actor, String initialStage, String intent) {
        public Request {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(initialStage, "initialStage");
            Objects.requireNonNull(intent, "intent");
            if (requestId.isBlank() || type.isBlank()) {
                throw new IllegalArgumentException("requestId and type must not be blank");
            }
        }
    }

    public record BeginResult(OperationRecord operation, boolean created) {
    }

    /** Thrown when an advance targets a stale revision or a finished operation. */
    public static final class StaleOperationException extends StorageException {
        public StaleOperationException(String message) {
            super(message, false);
        }
    }

    private static final String COLUMNS = "operation_id, request_id, type, island_id, actor, stage, status, revision, "
            + "intent, result, error, created_at, updated_at";

    private final Clock clock;

    public OperationJournal(Clock clock) {
        this.clock = clock;
    }

    public BeginResult begin(Connection c, Request request) throws SQLException {
        return Sql.inTransaction(c, tx -> {
            Optional<OperationRecord> existing = findByRequestId(tx, request.requestId());
            if (existing.isPresent()) {
                OperationRecord op = existing.get();
                if (!op.type().equals(request.type()) || !op.actor().equals(request.actor())) {
                    throw new StorageException("request id " + request.requestId() + " is already used by a "
                            + op.type() + " operation of " + op.actor(), false);
                }
                return new BeginResult(op, false);
            }
            long now = clock.millis();
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = tx.prepareStatement("INSERT INTO operations (" + COLUMNS
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, NULL, NULL, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, request.requestId());
                ps.setString(3, request.type());
                ps.setString(4, request.islandId() == null ? null : request.islandId().toString());
                ps.setString(5, request.actor());
                ps.setString(6, request.initialStage());
                ps.setString(7, OperationStatus.PENDING.name());
                ps.setString(8, request.intent());
                ps.setLong(9, now);
                ps.setLong(10, now);
                ps.executeUpdate();
            }
            return new BeginResult(get(tx, id).orElseThrow(), true);
        });
    }

    /**
     * Moves a pending operation to {@code newStage}/{@code newStatus}. {@code result} and {@code error} replace the
     * stored values only when non-null.
     */
    public OperationRecord advance(Connection c, UUID operationId, long expectedRevision, String newStage,
                                   OperationStatus newStatus, String result, String error) throws SQLException {
        return Sql.inTransaction(c, tx -> {
            int updated;
            try (PreparedStatement ps = tx.prepareStatement("UPDATE operations SET stage = ?, status = ?, "
                    + "revision = revision + 1, result = COALESCE(?, result), error = COALESCE(?, error), updated_at = ? "
                    + "WHERE operation_id = ? AND revision = ? AND status = 'PENDING'")) {
                ps.setString(1, newStage);
                ps.setString(2, newStatus.name());
                ps.setString(3, result);
                ps.setString(4, error);
                ps.setLong(5, clock.millis());
                ps.setString(6, operationId.toString());
                ps.setLong(7, expectedRevision);
                updated = ps.executeUpdate();
            }
            if (updated != 1) {
                OperationRecord current = get(tx, operationId)
                        .orElseThrow(() -> new StorageException("unknown operation " + operationId, false));
                throw new StaleOperationException("operation " + operationId + " is " + current.status() + " at revision "
                        + current.revision() + ", expected PENDING at revision " + expectedRevision);
            }
            return get(tx, operationId).orElseThrow();
        });
    }

    public Optional<OperationRecord> get(Connection c, UUID operationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM operations WHERE operation_id = ?")) {
            ps.setString(1, operationId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<OperationRecord> findByRequestId(Connection c, String requestId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM operations WHERE request_id = ?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Operations that are not finished: PENDING and RECOVERY_REQUIRED, oldest first. */
    public List<OperationRecord> listOpen(Connection c) throws SQLException {
        List<OperationRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM operations "
                + "WHERE status IN ('PENDING', 'RECOVERY_REQUIRED') ORDER BY created_at, operation_id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    private static OperationRecord map(ResultSet rs) throws SQLException {
        String island = rs.getString("island_id");
        return new OperationRecord(
                UUID.fromString(rs.getString("operation_id")),
                rs.getString("request_id"),
                rs.getString("type"),
                island == null ? null : UUID.fromString(island),
                rs.getString("actor"),
                rs.getString("stage"),
                OperationStatus.valueOf(rs.getString("status")),
                rs.getLong("revision"),
                rs.getString("intent"),
                rs.getString("result"),
                rs.getString("error"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }
}
