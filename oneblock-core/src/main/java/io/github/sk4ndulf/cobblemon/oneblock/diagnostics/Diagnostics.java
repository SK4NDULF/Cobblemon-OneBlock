package io.github.sk4ndulf.cobblemon.oneblock.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;

/**
 * Creates diagnostic IDs ({@code OB-<CLASS>-<6 hex>}), logs the full detail once and keeps a bounded list of recent
 * problems for {@code /ob status}. Thread-safe.
 */
public final class Diagnostics {
    public static final int MAX_RECENT = 50;

    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final Deque<Diagnostic> recent = new ArrayDeque<>();

    public Diagnostics(Clock clock) {
        this.clock = clock;
    }

    public static String newId(ErrorClass errorClass) {
        byte[] bytes = new byte[3];
        RANDOM.nextBytes(bytes);
        return "OB-" + errorClass.name() + "-" + HexFormat.of().formatHex(bytes);
    }

    public Diagnostic report(ErrorClass errorClass, String message, Throwable cause) {
        Diagnostic diagnostic = new Diagnostic(newId(errorClass), errorClass, message, clock.instant());
        if (cause == null) {
            LOG.error("[{}] {}", diagnostic.id(), message);
        } else {
            LOG.error("[{}] {}", diagnostic.id(), message, cause);
        }
        synchronized (recent) {
            recent.addFirst(diagnostic);
            while (recent.size() > MAX_RECENT) {
                recent.removeLast();
            }
        }
        return diagnostic;
    }

    public Diagnostic report(ErrorClass errorClass, String message) {
        return report(errorClass, message, null);
    }

    /** Newest first. */
    public List<Diagnostic> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }
}
