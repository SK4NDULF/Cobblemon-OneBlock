package io.github.sk4ndulf.cobblemon.oneblock.core.permission

import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island
import io.github.sk4ndulf.cobblemon.oneblock.api.permission.IslandRole
import io.github.sk4ndulf.cobblemon.oneblock.api.permission.PermissionManager
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubManager
import net.minecraft.core.BlockPos
import java.util.UUID

/**
 * API view of the protection rules. Mirrors [ProtectionManager.denialFor] minus the
 * player-object specifics (no admin bypass here — this answers for regular players).
 */
class PermissionManagerImpl : PermissionManager {

    override fun roleOn(player: UUID, island: Island): IslandRole = when {
        island.owner() == player -> IslandRole.OWNER
        island.members().contains(player) -> IslandRole.MEMBER
        else -> IslandRole.VISITOR
    }

    override fun canModify(player: UUID, pos: BlockPos): Boolean {
        val manager = OneBlockCore.islandManager ?: return false
        if (HubManager.isInHubArea(pos)) return false
        val island = manager.islandAt(pos) ?: return false
        if (!island.isMemberOrOwner(player)) return false
        return manager.isWithinCurrentBorder(island, pos)
    }
}
