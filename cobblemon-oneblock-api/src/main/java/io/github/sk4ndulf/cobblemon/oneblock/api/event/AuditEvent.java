package io.github.sk4ndulf.cobblemon.oneblock.api.event;

import java.util.UUID;

/**
 * Fired on the server thread for every moderation-relevant action (island created,
 * reset, deleted, ban, kick, admin override, ...).
 *
 * <p>The core already writes these to its audit log file. Addons can subscribe to
 * forward them anywhere else — a Discord bot, a web dashboard, an external database.</p>
 */
public final class AuditEvent extends OneBlockEvent {

    private final String action;
    private final UUID actor;
    private final String actorName;
    private final UUID target;
    private final String details;
    private final long timestamp;

    /**
     * @param actor  who performed the action; null for server-initiated actions
     * @param target who the action was performed on; null when not applicable
     */
    public AuditEvent(String action, UUID actor, String actorName, UUID target, String details) {
        this.action = action;
        this.actor = actor;
        this.actorName = actorName;
        this.target = target;
        this.details = details;
        this.timestamp = System.currentTimeMillis();
    }

    /** Machine-readable action key, e.g. {@code "island.reset"} or {@code "island.ban"}. */
    public String action() {
        return action;
    }

    /** Who performed the action. Null for actions the server itself performed. */
    public UUID actor() {
        return actor;
    }

    /** Display name of the actor, or {@code "SERVER"}. */
    public String actorName() {
        return actorName;
    }

    /** Who or what the action was performed on, when applicable. May be null. */
    public UUID target() {
        return target;
    }

    /** Human-readable context, e.g. the island id and reason. */
    public String details() {
        return details;
    }

    public long timestamp() {
        return timestamp;
    }
}
