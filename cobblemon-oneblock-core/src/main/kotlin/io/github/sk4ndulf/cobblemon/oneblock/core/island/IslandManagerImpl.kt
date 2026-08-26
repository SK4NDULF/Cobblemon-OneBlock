package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.api.event.IslandCreatedEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.event.OneBlockBreakEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.event.PartyJoinEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.event.PartyLeaveEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.event.PermissionChangeEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island
import io.github.sk4ndulf.cobblemon.oneblock.api.island.IslandManager
import io.github.sk4ndulf.cobblemon.oneblock.api.permission.IslandRole
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.biome.BiomeService
import io.github.sk4ndulf.cobblemon.oneblock.core.config.MainConfig
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.moderation.AuditLog
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechEffects
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechService
import io.github.sk4ndulf.cobblemon.oneblock.core.world.GridMath
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubManager
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.Blocks
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

    /** Writes the island's visitor Cobblemon settings through to the database. */
    fun persistVisitorSettings(island: IslandData) {
        repository.updateVisitorSettingsAsync(island.id, island.allowVisitorCatch, island.allowVisitorBattle)
    }

    /** Sets or clears the island's name. Pass null to fall back to the owner's name. */
    fun rename(island: IslandData, name: String?, actor: ServerPlayer?) {
        island.name = name
        repository.updateNameAsync(island.id, name)
        // Audited because it is player-authored text other players see; if a name has to be
        // dealt with later, the log says who set it and when.
        AuditLog.record("island.rename", actor, island.owner(), "island=${island.id} name=${name ?: "-"}")
    }

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

    fun islandDataById(id: Long): IslandData? = byId[id]

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
            borderLevel = 1, breakCount = 0, points = 0.0, createdAt = System.currentTimeMillis(),
            archivedAt = null, config = config,
        )
        byId[id] = island
        usedSlots.add(slot)
        index(island)

        val level = OneBlockDimension.overworld(player.server)
        if (level != null) {
            ensureFoundation(level, island)
            placeInitialBlock(level, island)
        }
        OneBlockCore.eventBus.post(IslandCreatedEvent(island, player))
        AuditLog.record("island.create", player, player.uuid, "island=$id slot=$slot")
        sendHome(player, island)
        OneBlockCore.LOGGER.info("Island {} created for {} at slot {} {}.",
            id, player.gameProfile.name, slot, island.oneBlockPos().toShortString())
        return island
    }

    // --- party membership -----------------------------------------------------------------

    /** Adds a member. Caller has validated invite, limits and that the player is island-less. */
    fun addMember(island: IslandData, member: UUID) {
        island.memberSet.add(member)
        activeByPlayer[member] = island
        repository.insertMemberAsync(island.id, member)
        OneBlockCore.eventBus.post(PartyJoinEvent(island, member))
        OneBlockCore.eventBus.post(PermissionChangeEvent(island, member, IslandRole.VISITOR, IslandRole.MEMBER))
    }

    /** Removes a member (leave or kick). Returns false if not a member. */
    fun removeMember(island: IslandData, member: UUID, reason: PartyLeaveEvent.Reason): Boolean {
        if (!island.memberSet.remove(member)) return false
        activeByPlayer.remove(member)
        repository.deleteMemberAsync(island.id, member)
        OneBlockCore.eventBus.post(PartyLeaveEvent(island, member, reason))
        OneBlockCore.eventBus.post(PermissionChangeEvent(island, member, IslandRole.MEMBER, IslandRole.VISITOR))
        return true
    }

    /**
     * Archives the owner's island. Members are released (event + hub respawn for online
     * players); their DB rows stay so a restore brings the party back intact.
     */
    fun archive(owner: UUID, server: MinecraftServer): Boolean {
        val island = activeByPlayer[owner] ?: return false
        if (island.owner() != owner) return false

        val members = island.memberSet.toList()
        island.state = IslandState.ARCHIVED
        island.archivedAt = System.currentTimeMillis()
        unindex(island)
        repository.updateStateAsync(island.id, IslandState.ARCHIVED, island.archivedAt)
        BiomeService.clearIsland(island.id)
        AuditLog.record("island.archive", null, "SERVER", owner, "island=${island.id} members=${members.size}")

        for (member in members) {
            OneBlockCore.eventBus.post(PartyLeaveEvent(island, member, PartyLeaveEvent.Reason.ISLAND_ARCHIVED))
            OneBlockCore.eventBus.post(
                PermissionChangeEvent(island, member, IslandRole.MEMBER, IslandRole.VISITOR),
            )
            server.playerList.getPlayer(member)?.let { online ->
                HubManager.sendToHub(online)
                online.displayClientMessage(ServerLang.msg("cobblemon_oneblock.party.archived_member"), false)
            }
        }
        OneBlockCore.LOGGER.info("Island {} (slot {}) archived, {} members released.", island.id, island.slot(), members.size)
        return true
    }

    // --- OneBlock break handling (event registered once in OneBlockCore) ------------------------

    fun handleBreak(level: ServerLevel, player: ServerPlayer, pos: BlockPos, state: net.minecraft.world.level.block.state.BlockState) {
        if (level.dimension() != OneBlockDimension.OVERWORLD_KEY) return
        val island = activeByAnchor[pos] ?: return

        // Count the break BEFORE asking loot providers, so a provider sees a break count
        // that includes the break it is deciding for (that is what API.md promises).
        island.breakCount++

        // Addon loot providers get the first say (API.md promises that), then the treasure
        // chest roll, then the configured block pool. A provider that answers therefore also
        // suppresses the chest for that break — it asked for a specific block, it gets it.
        val provided = LootRegistryImpl.query(island, level)
        val chestTable =
            if (provided == null) ChestLoot.roll(level.random, TechEffects.chestChanceBonus(island.id)) else null
        val next = provided
            ?: chestTable?.let { ChestLoot.chestState(level.random) }
            ?: OneBlockCore.lootTable.next(level.random, island.id)
        level.setBlockAndUpdate(pos, next)
        // Only after the block exists — the chest's block entity is created by the placement.
        chestTable?.let { ChestLoot.fill(level, pos, it) }

        // The yield bonus is rolled against the block that was *just broken*, not the one being
        // placed. That is what a player means by "this drops twice", and it also means a
        // treasure chest can never be doubled: a chest belongs to no biome set.
        val doubled = level.random.nextDouble() < BiomePools.yieldChance(island.id, state.block)

        // Vanilla has not dropped anything yet at this point (see DropCollector); this only
        // registers the anchor to be swept at the end of the tick.
        DropCollector.queue(pos, player, doubled)
        ProgressionService.onBreak(island, level.server)
        repository.updateProgressAsync(island.id, island.breakCount, island.points, island.borderLevel)
        OneBlockCore.eventBus.post(OneBlockBreakEvent(island, player, state, next))
    }

    // --- teleports ------------------------------------------------------------------------------

    /**
     * Teleports a player onto the island without touching their respawn point.
     * Used for visitors — dying on a foreign island must not move your bed.
     */
    fun sendToIsland(player: ServerPlayer, island: IslandData) {
        val level = OneBlockDimension.overworld(player.server) ?: return
        ensureFoundation(level, island)
        if (level.getBlockState(island.oneBlockPos()).isAir) {
            placeInitialBlock(level, island)
        }
        player.teleportTo(level, island.spawnX, island.spawnY, island.spawnZ, 0.0f, 0.0f)
    }

    /** Teleports a player to their own island and anchors their respawn there. */
    fun sendHome(player: ServerPlayer, island: IslandData) {
        sendToIsland(player, island)
        player.setRespawnPosition(
            OneBlockDimension.OVERWORLD_KEY, island.oneBlockPos().above(), 0.0f, true, false,
        )
    }

    private fun placeInitialBlock(level: ServerLevel, island: IslandData) {
        level.setBlockAndUpdate(island.oneBlockPos(), OneBlockCore.lootTable.next(level.random, island.id))
    }

    /**
     * Guarantees the indestructible bedrock floor one block under the OneBlock.
     *
     * It is the island's safety net: whatever happens to the OneBlock — a player mining
     * it, an explosion, a rogue mod — nobody drops into the void from the anchor, and the
     * island always has a foothold to rebuild from.
     */
    private fun ensureFoundation(level: ServerLevel, island: IslandData) {
        val below = island.oneBlockPos().below()
        val isBedrock = level.getBlockState(below).`is`(Blocks.BEDROCK)
        if (OneBlockCore.configManager.mainConfig.anchorBedrockFoundation) {
            if (!isBedrock) level.setBlockAndUpdate(below, Blocks.BEDROCK.defaultBlockState())
        } else if (isBedrock) {
            // The setting was turned off: take our own bedrock back out. Only bedrock, so a
            // block the player put under their anchor themselves survives.
            level.setBlockAndUpdate(below, Blocks.AIR.defaultBlockState())
        }
    }

    /**
     * Safety net for the OneBlock itself. The break hook only fires when a *player* mines
     * it — an explosion, a command or another mod can leave the anchor empty and the
     * island unplayable. This sweep restores anchor and foundation.
     *
     * Chunks are never force-loaded: an unloaded island has nobody on it, and both
     * [sendToIsland] and the next sweep repair it as soon as it matters.
     */
    fun repairAnchors(server: MinecraftServer) {
        val level = OneBlockDimension.overworld(server) ?: return
        for (island in activeBySlot.values) {
            val pos = island.oneBlockPos()
            if (level.chunkSource.getChunkNow(pos.x shr 4, pos.z shr 4) == null) continue

            ensureFoundation(level, island)
            if (level.getBlockState(pos).isAir) {
                placeInitialBlock(level, island)
                OneBlockCore.LOGGER.info(
                    "Restored the missing OneBlock of island {} at {}.", island.id, pos.toShortString(),
                )
            }
        }
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
                        if (archive(owner, server)) {
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
                // Tech progress dies here rather than at archive time: archiving is
                // restorable, so an archived island has to keep its unlocks and its claims
                // for the restore to mean anything. A purge is the point of no return, and
                // from here every source is claimable again by whoever owns the slot next
                // (PROGRESSION_REWORK.md §4.3). A player who runs /ob reset gets a brand new
                // island id and therefore an empty tree immediately, which is what they see.
                TechService.clear(island.id)
                BiomePools.forget(island.id)
                // Slot stays reserved: the area still contains the old builds. Slot reuse
                // arrives together with chunk clearing (see PROJECT_PLAN.md open points).
                OneBlockCore.LOGGER.info("Purged archived island {} (slot {}).", island.id, island.slot())
            }
        }
    }
}
