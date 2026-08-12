package io.github.sk4ndulf.oneblock.core.island

import io.github.sk4ndulf.oneblock.api.event.IslandCreatedEvent
import io.github.sk4ndulf.oneblock.api.event.OneBlockBreakEvent
import io.github.sk4ndulf.oneblock.api.island.Island
import io.github.sk4ndulf.oneblock.api.island.IslandManager
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.config.MainConfig
import io.github.sk4ndulf.oneblock.core.world.GridMath
import io.github.sk4ndulf.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Live registry + lifecycle of all islands. All mutations run on the server thread;
 * persistence is written through via [IslandRepository].
 *
 * Slot allocation: lowest spiral slot that was never used and doesn't intersect the hub.
 * Slots of archived/purged islands are NOT reused yet — a reused slot would inherit the
 * previous island's builds. Slot reuse lands together with chunk clearing (see plan).
 */
class IslandManagerImpl(private val repository: IslandRepository) : IslandManager {

    private val byId = HashMap<Long, IslandData>()
    private val activeBySlot = HashMap<Int, IslandData>()
    private val activeByPlayer = HashMap<UUID, IslandData>()
    private val activeByAnchor = HashMap<BlockPos, IslandData>()
    private val activeByGrid = HashMap<GridMath.GridPos, IslandData>()
    private val usedSlots = HashSet<Int>()

    // --- loading -------------------------------------------------------------------------

    fun loadAll() {
        byId.clear(); activeBySlot.clear(); activeByPlayer.clear(); activeByAnchor.clear()
        activeByGrid.clear(); usedSlots.clear()
        val config = OneBlockCore.configManager.mainConfig
        for (island in repository.loadAll(config)) {
            byId[island.id] = island
            usedSlots.add(island.slot())
            if (island.state == IslandState.ACTIVE) {
                index(island)
            }
        }
        OneBlockCore.LOGGER.info(
            "Loaded {} islands ({} active).", byId.size, activeBySlot.size,
        )
    }

    private fun index(island: IslandData) {
        activeBySlot[island.slot()] = island
        activeByAnchor[island.oneBlockPos()] = island
        activeByGrid[GridMath.slotToGrid(island.slot())] = island
        activeByPlayer[island.owner()] = island
        island.memberSet.forEach { activeByPlayer[it] = island }
    }

    private fun unindex(island: IslandData) {
        activeBySlot.remove(island.slot())
        activeByAnchor.remove(island.oneBlockPos())
        activeByGrid.remove(GridMath.slotToGrid(island.slot()))
        activeByPlayer.remove(island.owner())
        island.memberSet.forEach { activeByPlayer.remove(it) }
    }

    /** True when the position is exactly an active island's OneBlock. */
    fun isAnchor(pos: BlockPos): Boolean = activeByAnchor.containsKey(pos)

    /**
     * The active island whose maximum footprint (max_island_size square) contains the
     * position, or null for the void buffer between islands.
     */
    fun islandAt(pos: BlockPos): IslandData? {
        val config = OneBlockCore.configManager.mainConfig
        val spacing = config.islandSpacing
        val gx = Math.round(pos.x.toDouble() / spacing).toInt()
        val gz = Math.round(pos.z.toDouble() / spacing).toInt()
        val island = activeByGrid[GridMath.GridPos(gx, gz)] ?: return null
        val half = MainConfig.chunkAlign(config.maxIslandSize) / 2
        val anchor = island.oneBlockPos()
        return if (kotlin.math.abs(pos.x - anchor.x) <= half && kotlin.math.abs(pos.z - anchor.z) <= half) {
            island
        } else {
            null
        }
    }

    /** Chebyshev check: is the position inside the island's CURRENT border level area? */
    fun isWithinCurrentBorder(island: IslandData, pos: BlockPos): Boolean {
        val half = GridMath.borderSizeAt(island.borderLevel, OneBlockCore.configManager.mainConfig) / 2
        val anchor = island.oneBlockPos()
        return kotlin.math.abs(pos.x - anchor.x) <= half && kotlin.math.abs(pos.z - anchor.z) <= half
    }

    // --- IslandManager (public API) --------------------------------------------------------

    override fun islandOf(player: UUID): Optional<Island> = Optional.ofNullable(activeByPlayer[player])

    override fun islandBySlot(slot: Int): Optional<Island> = Optional.ofNullable(activeBySlot[slot])

    override fun activeIslands(): Collection<Island> = activeBySlot.values.toList()

    fun islandDataOf(player: UUID): IslandData? = activeByPlayer[player]

    // --- lifecycle -------------------------------------------------------------------------

    /** Lowest never-used spiral slot whose full footprint clears the hub circle. */
    private fun allocateSlot(): Int {
        val config = OneBlockCore.configManager.mainConfig
        var slot = 1
        while (slot in usedSlots || GridMath.intersectsHub(slot, config)) {
            slot++
        }
        return slot
    }

    /** Creates an island for the player, places the OneBlock, teleports them onto it. */
    fun create(player: ServerPlayer): IslandData {
        check(activeByPlayer[player.uuid] == null) { "Player already has an island" }
        val config = OneBlockCore.configManager.mainConfig
        val slot = allocateSlot()
        val id = repository.insert(slot, player.uuid, System.currentTimeMillis())
        val island = IslandData(
            id = id, slot = slot, owner = player.uuid, state = IslandState.ACTIVE,
            borderLevel = 1, breakCount = 0, createdAt = System.currentTimeMillis(),
            archivedAt = null, config = config,
        )
        byId[id] = island
        usedSlots.add(slot)
        index(island)

        val level = OneBlockDimension.level(player.server)
        if (level != null) {
            placeInitialBlock(level, island)
        }
        OneBlockCore.eventBus.post(IslandCreatedEvent(island, player))
        sendHome(player, island)
        OneBlockCore.LOGGER.info("Island {} created for {} at slot {} {}.",
            id, player.gameProfile.name, slot, island.oneBlockPos().toShortString())
        return island
    }

    /** Archives the current island. Returns false if the player has none. */
    fun archive(player: UUID): Boolean {
        val island = activeByPlayer[player] ?: return false
        if (island.owner() != player) return false
        island.state = IslandState.ARCHIVED
        island.archivedAt = System.currentTimeMillis()
        unindex(island)
        repository.updateStateAsync(island.id, IslandState.ARCHIVED, island.archivedAt)
        OneBlockCore.LOGGER.info("Island {} (slot {}) archived.", island.id, island.slot())
        return true
    }

    // --- OneBlock break handling (event registered once in OneBlockCore) ------------------------

    fun handleBreak(level: ServerLevel, player: ServerPlayer, pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
        if (level.dimension() != OneBlockDimension.WORLD_KEY) return
        val island = activeByAnchor[pos] ?: return

        val next = OneBlockCore.lootTable.next(level.random)
        level.setBlockAndUpdate(pos, next)
        island.breakCount++
        repository.updateBreakCountAsync(island.id, island.breakCount)
        OneBlockCore.eventBus.post(OneBlockBreakEvent(island, player, state, next))
    }

    // --- teleports ------------------------------------------------------------------------------

    /** Teleports a player to their island spawn, repairing a missing OneBlock first. */
    fun sendHome(player: ServerPlayer, island: IslandData) {
        val level = OneBlockDimension.level(player.server) ?: return
        if (level.getBlockState(island.oneBlockPos()).isAir) {
            placeInitialBlock(level, island)
        }
        player.teleportTo(level, island.spawnX, island.spawnY, island.spawnZ, 0.0f, 0.0f)
        player.setRespawnPosition(
            OneBlockDimension.WORLD_KEY, island.oneBlockPos().above(), 0.0f, true, false,
        )
    }

    private fun placeInitialBlock(level: ServerLevel, island: IslandData) {
        level.setBlockAndUpdate(island.oneBlockPos(), OneBlockCore.lootTable.next(level.random))
    }

    // --- maintenance (hourly + at startup) -----------------------------------------------------

    /**
     * Archive islands of long-inactive owners and permanently purge islands whose
     * archive window ran out. Runs off-thread for the DB scan, mutates on the server thread.
     */
    fun runMaintenance(server: MinecraftServer) {
        val config = OneBlockCore.configManager.mainConfig
        val now = System.currentTimeMillis()

        if (config.inactivityPurgeDays > 0) {
            val cutoff = now - TimeUnit.DAYS.toMillis(config.inactivityPurgeDays.toLong())
            repository.findInactiveOwnersAsync(cutoff).thenAccept { inactiveOwners ->
                if (inactiveOwners.isEmpty()) return@thenAccept
                server.execute {
                    for (owner in inactiveOwners) {
                        if (archive(owner)) {
                            OneBlockCore.LOGGER.info("Archived island of inactive owner {}.", owner)
                        }
                    }
                }
            }
        }

        if (config.resetArchiveDays >= 0) {
            val cutoff = now - TimeUnit.DAYS.toMillis(config.resetArchiveDays.toLong())
            val toPurge = byId.values.filter {
                it.state == IslandState.ARCHIVED && (it.archivedAt ?: Long.MAX_VALUE) < cutoff
            }
            for (island in toPurge) {
                island.state = IslandState.PURGED
                repository.updateStateAsync(island.id, IslandState.PURGED, island.archivedAt)
                // Slot stays reserved: the area still contains the old builds. Slot reuse
                // arrives together with chunk clearing (see PROJECT_PLAN.md open points).
                OneBlockCore.LOGGER.info("Purged archived island {} (slot {}).", island.id, island.slot())
            }
        }
    }
}
