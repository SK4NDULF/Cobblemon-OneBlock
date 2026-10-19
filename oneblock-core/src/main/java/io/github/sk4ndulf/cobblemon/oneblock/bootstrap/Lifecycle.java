package io.github.sk4ndulf.cobblemon.oneblock.bootstrap;

import io.github.sk4ndulf.cobblemon.oneblock.diagnostics.Diagnostic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current {@link LifecycleState} and the reasons for it. Reads are lock-free from any thread; transitions
 * are validated so that, for example, {@code RECOVERY_MODE} can only be left by a restart and {@code STOPPING} is final.
 */
public final class Lifecycle {
    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");

    /** Immutable snapshot of state plus the diagnostics that explain it. */
    public record Status(LifecycleState state, List<Diagnostic> reasons) {
        public Status {
            Objects.requireNonNull(state, "state");
            reasons = List.copyOf(reasons);
        }
    }

    private final AtomicReference<Status> status = new AtomicReference<>(new Status(LifecycleState.STARTING, List.of()));

    public Status status() {
        return status.get();
    }

    public LifecycleState state() {
        return status.get().state();
    }

    /**
     * Moves to {@code target}. Returns false (and changes nothing) if the transition is not allowed.
     */
    public boolean transition(LifecycleState target, List<Diagnostic> reasons) {
        while (true) {
            Status current = status.get();
            if (!isAllowed(current.state(), target)) {
                LOG.warn("Refused lifecycle transition {} -> {}", current.state(), target);
                return false;
            }
            Status next = new Status(target, reasons);
            if (status.compareAndSet(current, next)) {
                if (current.state() != target) {
                    LOG.info("Lifecycle {} -> {}{}", current.state(), target,
                            reasons.isEmpty() ? "" : " " + reasons.stream().map(Diagnostic::id).toList());
                }
                return true;
            }
        }
    }

    static boolean isAllowed(LifecycleState from, LifecycleState to) {
        if (from == LifecycleState.STOPPING) {
            return to == LifecycleState.STOPPING;
        }
        if (to == LifecycleState.STOPPING) {
            return true;
        }
        return switch (from) {
            case STARTING -> to != LifecycleState.STARTING;
            // Recovery is only left by fixing the data and restarting the server.
            case RECOVERY_MODE -> to == LifecycleState.RECOVERY_MODE;
            // Degraded storage is not trusted again without a restart.
            case READ_ONLY -> to == LifecycleState.READ_ONLY || to == LifecycleState.RECOVERY_MODE;
            case SETUP_PENDING, READY -> to != LifecycleState.STARTING;
            case STOPPING -> false;
        };
    }
}
