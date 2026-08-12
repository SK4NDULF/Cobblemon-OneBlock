package io.github.sk4ndulf.oneblock.api.event;

import io.github.sk4ndulf.oneblock.api.island.Island;

import java.util.UUID;

/**
 * Fired on the server thread after a player accepted an invite and became a member.
 */
public final class PartyJoinEvent extends OneBlockEvent {

    private final Island island;
    private final UUID player;

    public PartyJoinEvent(Island island, UUID player) {
        this.island = island;
        this.player = player;
    }

    public Island island() {
        return island;
    }

    public UUID player() {
        return player;
    }
}
