package io.github.sk4ndulf.cobblemon.oneblock.diagnostics;

import java.time.Instant;
import java.util.Objects;

/**
 * One reported problem. {@code id} is safe to show to players; {@code message} is admin-facing and must not contain
 * secrets. Stack traces only go to the server log.
 */
public record Diagnostic(String id, ErrorClass errorClass, String message, Instant at) {
    public Diagnostic {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(errorClass, "errorClass");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(at, "at");
    }
}
