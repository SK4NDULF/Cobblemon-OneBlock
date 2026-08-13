package io.github.sk4ndulf.cobblemon.oneblock.api.permission;

import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Protection queries. All methods must be called on the server thread.
 *
 * <p>The world is partitioned into: the hub (fully protected), island footprints
 * (owner/member build rights inside the island's <em>current</em> border level area),
 * and the void buffer between islands, which belongs to the fallback VOID owner —
 * nobody can modify it.</p>
 */
public interface PermissionManager {

    /** The player's role on the given island. */
    IslandRole roleOn(UUID player, Island island);

    /**
     * Whether the player may modify (break/place/interact) the block at this position
     * in the OneBlock world. Positions outside the OneBlock dimension are always allowed
     * (this mod does not police other dimensions).
     */
    boolean canModify(UUID player, BlockPos pos);
}
