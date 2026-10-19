package io.github.sk4ndulf.cobblemon.oneblock.operation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides at startup whether unfinished operations block the server. Phase 1 has no operation types yet, so the
 * handler set is empty: any open operation means something unexpected happened and the mod stays in recovery mode
 * instead of guessing (fail closed). Later phases register a handler per type that resumes or rolls back each stage.
 */
public final class OperationReconciler {
    /** Outcome for one open operation. */
    public record Finding(OperationRecord operation, String problem) {
    }

    private final Set<String> knownTypes;

    public OperationReconciler(Set<String> knownTypes) {
        this.knownTypes = Set.copyOf(knownTypes);
    }

    public static OperationReconciler withoutHandlers() {
        return new OperationReconciler(Set.of());
    }

    /** Open operations that cannot be reconciled automatically. Empty list = safe to continue. */
    public List<Finding> blocking(List<OperationRecord> open) {
        return open.stream()
                .map(op -> {
                    if (op.status() == OperationStatus.RECOVERY_REQUIRED) {
                        return new Finding(op, "marked RECOVERY_REQUIRED at stage " + op.stage());
                    }
                    if (!knownTypes.contains(op.type())) {
                        return new Finding(op, "no handler for operation type " + op.type() + " (stage " + op.stage() + ")");
                    }
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public Map<String, Integer> countByType(List<OperationRecord> open) {
        return open.stream().collect(java.util.stream.Collectors.toMap(OperationRecord::type, op -> 1, Integer::sum));
    }
}
