package io.github.sk4ndulf.oneblock.api.event;

import io.github.sk4ndulf.oneblock.api.island.Island;
import io.github.sk4ndulf.oneblock.api.permission.IslandRole;

import java.util.UUID;

/**
 * Fired on the server thread whenever a player's role on an island changes
 * (member added: VISITOR → MEMBER; member removed: MEMBER → VISITOR).
 */
public final class PermissionChangeEvent extends OneBlockEvent {

    private final Island island;
    private final UUID player;
    private final IslandRole oldRole;
    private final IslandRole newRole;

    public PermissionChangeEvent(Island island, UUID player, IslandRole oldRole, IslandRole newRole) {
        this.island = island;
        this.player = player;
        this.oldRole = oldRole;
        this.newRole = newRole;
    }

    public Island island() {
        return island;
    }

    public UUID player() {
        return player;
    }

    public IslandRole oldRole() {
        return oldRole;
    }

    public IslandRole newRole() {
        return newRole;
    }
}
