package io.github.sk4ndulf.cobblemon.oneblock.api.island;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Read access to active islands. All methods must be called on the server thread;
 * results reflect the live state of the current tick.
 *
 * <p>Mutations (create, reset, delete) are intentionally not part of the public API —
 * they flow through player commands and admin tools. Addons react via the event bus
 * ({@code IslandCreatedEvent}, {@code OneBlockBreakEvent}, ...).</p>
 */
public interface IslandManager {

    /** The active island the player owns or is a member of. */
    Optional<Island> islandOf(UUID player);

    /** The active island occupying the given spiral slot. */
    Optional<Island> islandBySlot(int slot);

    /** All active islands. Unmodifiable snapshot view. */
    Collection<Island> activeIslands();
}
