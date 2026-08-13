package io.github.sk4ndulf.cobblemon.oneblock.api.island;

import net.minecraft.core.BlockPos;

import java.util.Set;
import java.util.UUID;

/**
 * A player island in the OneBlock world. Instances are live views onto the core's
 * state — values change as the island progresses. Never cache the returned
 * collections beyond the current tick.
 */
public interface Island {

    /** Stable database id of this island. */
    long id();

    /** Grid slot on the spiral (1-based; slot 0 is the hub and never used). */
    int slot();

    /** Owner UUID. Exactly one owner per island. */
    UUID owner();

    /** Party member UUIDs, excluding the owner. Empty until the party system fills it. */
    Set<UUID> members();

    /** True if the given player is the owner or a member. */
    default boolean isMemberOrOwner(UUID player) {
        return owner().equals(player) || members().contains(player);
    }

    /** Current border level, 1-8. */
    int borderLevel();

    /** Total OneBlock breaks on this island. */
    long breakCount();

    /** Position of the island's OneBlock (also the island anchor). */
    BlockPos oneBlockPos();

    /**
     * The owner-chosen island name, if one was set.
     *
     * Empty means the island has never been renamed, not that it has no name to show: the
     * core falls back to the owner's name when displaying it. Treat the contents as untrusted
     * player input — it is sanitised on the way in (no formatting codes, no control
     * characters, length-capped), but it is still text a player wrote.
     *
     * Defaulted so existing implementations of this interface keep compiling.
     */
    default java.util.Optional<String> name() {
        return java.util.Optional.empty();
    }
}
