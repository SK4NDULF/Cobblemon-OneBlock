package io.github.sk4ndulf.oneblock.core.biome

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.island.IslandData
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.oneblock.core.world.OneBlockDimension
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.util.UUID

/**
 * The 2-point selection workflow and the region bookkeeping behind `/ob biome ...`.
 * Selections live in memory (per player); regions are persisted per island.
 */
object BiomeService {

    private const val SELECTION_REACH = 64.0

    private class Selection(var first: BlockPos? = null, var second: BlockPos? = null)

    private val selections = HashMap<UUID, Selection>()
    private val regionsByIsland = HashMap<Long, MutableList<BiomeRegion>>()

    private var repository: BiomeRepository? = null

    fun reload(repository: BiomeRepository) {
        this.repository = repository
        regionsByIsland.clear()
        for (region in repository.loadAll()) {
            regionsByIsland.getOrPut(region.islandId) { mutableListOf() }.add(region)
        }
        val count = regionsByIsland.values.sumOf { it.size }
        if (count > 0) OneBlockCore.LOGGER.info("Loaded {} biome regions.", count)
    }

    fun regionsOf(islandId: Long): List<BiomeRegion> = regionsByIsland[islandId] ?: emptyList()

    fun allRegions(): List<BiomeRegion> = regionsByIsland.values.flatten()

    /** Biome id that applies at a position (innermost matching region wins), or null. */
    fun biomeAt(islandId: Long, pos: BlockPos): String? =
        regionsByIsland[islandId]?.lastOrNull { it.contains(pos) }?.biomeId

    // --- selection -----------------------------------------------------------------------

    /**
     * Sets a selection corner at the block the player is looking at; when they aim at
     * nothing (the void is mostly empty), their own position is used instead.
     */
    fun setCorner(player: ServerPlayer, first: Boolean): BlockPos {
        val hit = player.pick(SELECTION_REACH, 0.0f, false)
        val pos = if (hit is BlockHitResult && hit.type != HitResult.Type.MISS) {
            hit.blockPos
        } else {
            player.blockPosition()
        }
        val selection = selections.getOrPut(player.uuid) { Selection() }
        if (first) selection.first = pos else selection.second = pos

        player.sendSystemMessage(
            ServerLang.msg(
                if (first) "oneblock.biome.pos1_set" else "oneblock.biome.pos2_set",
                pos.x, pos.y, pos.z,
            ).withStyle(ChatFormatting.AQUA),
        )
        return pos
    }

    fun clearSelection(player: ServerPlayer) {
        selections.remove(player.uuid)
    }

    // --- applying ------------------------------------------------------------------------

    sealed interface SetResult {
        data class Success(val region: BiomeRegion, val cells: Int) : SetResult
        data class Error(val message: String) : SetResult
    }

    /**
     * Validates the current selection against the island and applies the biome.
     * Everything is checked before a single cell is written.
     */
    fun applySelection(player: ServerPlayer, island: IslandData, biomeId: String): SetResult {
        val repo = repository ?: return SetResult.Error(ServerLang.raw("oneblock.error.not_ready"))
        val level = OneBlockDimension.level(player.server)
            ?: return SetResult.Error(ServerLang.raw("oneblock.error.not_ready"))

        val selection = selections[player.uuid]
        val first = selection?.first
        val second = selection?.second
        if (first == null || second == null) {
            return SetResult.Error(ServerLang.raw("oneblock.biome.no_selection"))
        }

        val biome = BiomeEditor.resolveBiome(level, biomeId)
            ?: return SetResult.Error(ServerLang.raw("oneblock.biome.unknown", biomeId))

        // Snap outward to full biome cells so the region edges are real biome boundaries.
        val minX = BiomeRegion.snapDown(minOf(first.x, second.x))
        val minY = BiomeRegion.snapDown(minOf(first.y, second.y)).coerceAtLeast(level.minBuildHeight)
        val minZ = BiomeRegion.snapDown(minOf(first.z, second.z))
        val maxX = BiomeRegion.snapUp(maxOf(first.x, second.x))
        val maxY = BiomeRegion.snapUp(maxOf(first.y, second.y)).coerceAtMost(level.maxBuildHeight - 1)
        val maxZ = BiomeRegion.snapUp(maxOf(first.z, second.z))

        val manager = OneBlockCore.islandManager
            ?: return SetResult.Error(ServerLang.raw("oneblock.error.not_ready"))
        for (corner in listOf(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ))) {
            if (manager.islandAt(corner) !== island || !manager.isWithinCurrentBorder(island, corner)) {
                return SetResult.Error(ServerLang.raw("oneblock.biome.outside_island"))
            }
        }

        val existing = regionsByIsland.getOrPut(island.id) { mutableListOf() }
        val limit = OneBlockCore.configManager.mainConfig.maxBiomeRegions
        if (existing.size >= limit) {
            return SetResult.Error(ServerLang.raw("oneblock.biome.limit_reached", limit))
        }

        val draft = BiomeRegion(0, island.id, minX, minY, minZ, maxX, maxY, maxZ, biomeId)
        val id = repo.insert(draft)
        val region = draft.copy(id = id)
        existing.add(region)

        val cells = BiomeEditor.apply(level, region, biome)
        clearSelection(player)
        return SetResult.Success(region, cells)
    }

    /** Removes a region by its 1-based index in the island's list. Returns false if absent. */
    fun removeRegion(island: IslandData, index: Int): BiomeRegion? {
        val regions = regionsByIsland[island.id] ?: return null
        val region = regions.getOrNull(index - 1) ?: return null
        regions.removeAt(index - 1)
        repository?.deleteAsync(region.id)
        return region
    }

    /** Drops every region of an island (used when the island is archived). */
    fun clearIsland(islandId: Long) {
        if (regionsByIsland.remove(islandId) != null) {
            repository?.deleteForIslandAsync(islandId)
        }
    }

    fun reapplyAll(level: ServerLevel) {
        BiomeEditor.reapplyAll(level, allRegions())
    }
}
