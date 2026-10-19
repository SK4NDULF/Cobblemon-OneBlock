package io.github.sk4ndulf.cobblemon.oneblock.operation;

/** Status of a durable operation. Only {@link #PENDING} operations can still change. */
public enum OperationStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    ABORTED,
    /** Reconciliation could not decide automatically; an admin has to look at it. */
    RECOVERY_REQUIRED;

    public boolean isOpen() {
        return this == PENDING || this == RECOVERY_REQUIRED;
    }
}
