package io.github.sk4ndulf.cobblemon.oneblock.api.event;

import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island;

import java.util.UUID;

/**
 * Fired on the server thread after a player stopped being a member of an island.
 */
public final class PartyLeaveEvent extends OneBlockEvent {

    /** Why the membership ended. */
    public enum Reason {
        /** The member left voluntarily (/ob party leave). */
        LEFT,
        /** The owner removed the member (/ob party kick). */
        KICKED,
        /** The island was archived (reset, delete, or inactivity purge). */
        ISLAND_ARCHIVED,
    }

    private final Island island;
    private final UUID player;
    private final Reason reason;

    public PartyLeaveEvent(Island island, UUID player, Reason reason) {
        this.island = island;
        this.player = player;
        this.reason = reason;
    }

    public Island island() {
        return island;
    }

    public UUID player() {
        return player;
    }

    public Reason reason() {
        return reason;
    }
}
