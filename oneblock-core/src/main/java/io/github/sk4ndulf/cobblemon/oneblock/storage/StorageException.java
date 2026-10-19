package io.github.sk4ndulf.cobblemon.oneblock.storage;

/** Any storage failure. {@code recoveryRequired} means the data itself cannot be trusted (not just a transient error). */
public class StorageException extends RuntimeException {
    private final boolean recoveryRequired;

    public StorageException(String message, boolean recoveryRequired) {
        super(message);
        this.recoveryRequired = recoveryRequired;
    }

    public StorageException(String message, Throwable cause, boolean recoveryRequired) {
        super(message, cause);
        this.recoveryRequired = recoveryRequired;
    }

    public boolean recoveryRequired() {
        return recoveryRequired;
    }
}
