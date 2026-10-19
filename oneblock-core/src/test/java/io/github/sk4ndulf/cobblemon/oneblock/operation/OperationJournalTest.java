package io.github.sk4ndulf.cobblemon.oneblock.operation;

import io.github.sk4ndulf.cobblemon.oneblock.storage.MigrationRunner;
import io.github.sk4ndulf.cobblemon.oneblock.storage.Sql;
import io.github.sk4ndulf.cobblemon.oneblock.storage.SqliteConnections;
import io.github.sk4ndulf.cobblemon.oneblock.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationJournalTest {
    @TempDir
    Path dir;

    private Connection c;
    private final OperationJournal journal = new OperationJournal(Clock.systemUTC());

    @BeforeEach
    void setUp() throws SQLException {
        c = SqliteConnections.open(dir.resolve("j.db"), 1000);
        new MigrationRunner(Clock.systemUTC()).migrate(c, dir.resolve("j.db"), dir.resolve("backups"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        c.close();
    }

    private OperationJournal.Request request(String requestId) {
        return new OperationJournal.Request(requestId, "CREATE", UUID.randomUUID(), "player:alice", "RESERVED", "{\"slot\":1}");
    }

    @Test
    void beginIsIdempotentPerRequestId() throws SQLException {
        OperationJournal.BeginResult first = journal.begin(c, request("r1"));
        OperationJournal.BeginResult retry = journal.begin(c, request("r1"));
        assertTrue(first.created());
        assertFalse(retry.created());
        assertEquals(first.operation().operationId(), retry.operation().operationId());
        assertEquals(1, Sql.count(c, "operations"));
    }

    @Test
    void reusingARequestIdForSomethingElseIsRejected() throws SQLException {
        journal.begin(c, request("r1"));
        OperationJournal.Request other = new OperationJournal.Request("r1", "RESET", null, "player:alice", "X", "{}");
        assertThrows(StorageException.class, () -> journal.begin(c, other));
        OperationJournal.Request otherActor = new OperationJournal.Request("r1", "CREATE", null, "player:bob", "X", "{}");
        assertThrows(StorageException.class, () -> journal.begin(c, otherActor));
    }

    @Test
    void advanceNeedsTheCurrentRevision() throws SQLException {
        OperationRecord op = journal.begin(c, request("r1")).operation();
        assertEquals(0, op.revision());
        OperationRecord next = journal.advance(c, op.operationId(), 0, "WORLD_BUILT", OperationStatus.PENDING, null, null);
        assertEquals(1, next.revision());
        assertEquals("WORLD_BUILT", next.stage());
        // a stale caller (e.g. a retried callback) cannot advance from the old revision
        assertThrows(OperationJournal.StaleOperationException.class,
                () -> journal.advance(c, op.operationId(), 0, "OTHER", OperationStatus.PENDING, null, null));
        OperationRecord done = journal.advance(c, op.operationId(), 1, "DONE", OperationStatus.SUCCEEDED, "{\"ok\":true}", null);
        assertEquals(OperationStatus.SUCCEEDED, done.status());
        assertEquals("{\"ok\":true}", done.result());
    }

    @Test
    void finishedOperationsCannotChange() throws SQLException {
        OperationRecord op = journal.begin(c, request("r1")).operation();
        journal.advance(c, op.operationId(), 0, "FAILED_AT_RESERVE", OperationStatus.FAILED, null, "disk full");
        assertThrows(OperationJournal.StaleOperationException.class,
                () -> journal.advance(c, op.operationId(), 1, "DONE", OperationStatus.SUCCEEDED, null, null));
        assertEquals("disk full", journal.get(c, op.operationId()).orElseThrow().error());
    }

    @Test
    void listOpenReturnsPendingAndRecoveryRequiredOnly() throws SQLException {
        OperationRecord a = journal.begin(c, request("a")).operation();
        OperationRecord b = journal.begin(c, request("b")).operation();
        OperationRecord d = journal.begin(c, request("d")).operation();
        journal.advance(c, b.operationId(), 0, "DONE", OperationStatus.SUCCEEDED, null, null);
        journal.advance(c, d.operationId(), 0, "UNCLEAR", OperationStatus.RECOVERY_REQUIRED, null, "world/db mismatch");
        var open = journal.listOpen(c);
        assertEquals(2, open.size());
        assertTrue(open.stream().anyMatch(o -> o.operationId().equals(a.operationId())));
        assertTrue(open.stream().anyMatch(o -> o.operationId().equals(d.operationId())));
        assertEquals(1, new OperationReconciler(java.util.Set.of("CREATE")).blocking(open).size(),
                "RECOVERY_REQUIRED always blocks, even with a handler");
    }

    @Test
    void databaseConstraintsRejectInvalidRows() throws SQLException {
        try (Statement s = c.createStatement()) {
            assertThrows(SQLException.class, () -> s.execute("INSERT INTO operations VALUES "
                    + "('x', 'r', 't', NULL, 'a', 's', 'BOGUS', 0, '{}', NULL, NULL, 0, 0)"));
            assertThrows(SQLException.class, () -> s.execute("INSERT INTO audit_log (at, actor, action, operation_id, outcome) "
                    + "VALUES (0, 'a', 'b', 'no-such-operation', 'SUCCESS')"));
        }
    }

    @Test
    void auditEntriesArePersisted() throws SQLException {
        OperationRecord op = journal.begin(c, request("r1")).operation();
        new AuditLog(Clock.systemUTC()).append(c, new AuditLog.Entry("player:alice", "island.create", "slot:1",
                op.operationId(), AuditLog.Outcome.PENDING, null, null));
        assertEquals(1, Sql.count(c, "audit_log"));
    }
}
