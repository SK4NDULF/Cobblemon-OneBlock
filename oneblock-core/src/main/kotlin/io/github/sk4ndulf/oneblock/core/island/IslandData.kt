package io.github.sk4ndulf.oneblock.core.island

import io.github.sk4ndulf.oneblock.api.island.Island
import io.github.sk4ndulf.oneblock.core.config.MainConfig
import io.github.sk4ndulf.oneblock.core.world.GridMath
import net.minecraft.core.BlockPos
import java.util.Collections
import java.util.UUID

enum class IslandState { ACTIVE, ARCHIVED, PURGED }

/**
 * Mutable core-side island. Implements the read-only API [Island] view.
 * All mutation happens on the server thread; persistence is written through
 * asynchronously by [IslandRepository].
 */
class IslandData(
    val id: Long,
    private val slot: Int,
    private val owner: UUID,
    var state: IslandState,
    var borderLevel: Int,
    var breakCount: Long,
    val createdAt: Long,
    var archivedAt: Long?,
    config: MainConfig,
) : Island {

    private val anchor: BlockPos = GridMath.slotToAnchor(slot, config)
    val memberSet: MutableSet<UUID> = mutableSetOf()

    /** Where players stand when teleported home: on top of the OneBlock. */
    val spawnX: Double get() = anchor.x + 0.5
    val spawnY: Double get() = anchor.y + 1.0
    val spawnZ: Double get() = anchor.z + 0.5

    override fun id(): Long = id
    override fun slot(): Int = slot
    override fun owner(): UUID = owner
    override fun members(): Set<UUID> = Collections.unmodifiableSet(memberSet)
    override fun borderLevel(): Int = borderLevel
    override fun breakCount(): Long = breakCount
    override fun oneBlockPos(): BlockPos = anchor
}
