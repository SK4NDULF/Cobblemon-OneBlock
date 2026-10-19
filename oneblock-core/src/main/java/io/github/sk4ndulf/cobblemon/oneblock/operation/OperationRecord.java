package io.github.sk4ndulf.cobblemon.oneblock.operation;

import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of one row of the operation journal. */
public record OperationRecord(
        UUID operationId,
        String requestId,
        String type,
        UUID islandId,
        String actor,
        String stage,
        OperationStatus status,
        long revision,
        String intent,
        String result,
        String error,
        long createdAt,
        long updatedAt) {

    public OperationRecord {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(intent, "intent");
    }
}
