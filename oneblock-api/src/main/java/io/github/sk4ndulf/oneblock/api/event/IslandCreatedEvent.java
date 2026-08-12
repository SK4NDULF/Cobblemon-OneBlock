package io.github.sk4ndulf.oneblock.api.event;

import io.github.sk4ndulf.oneblock.api.island.Island;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired on the server thread after an island has been created and its OneBlock placed,
 * but before the owner is teleported onto it.
 */
public final class IslandCreatedEvent extends OneBlockEvent {

    private final Island island;
    private final ServerPlayer owner;

    public IslandCreatedEvent(Island island, ServerPlayer owner) {
        this.island = island;
        this.owner = owner;
    }

    public Island island() {
        return island;
    }

    public ServerPlayer owner() {
        return owner;
    }
}
