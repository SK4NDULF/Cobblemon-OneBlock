package io.github.sk4ndulf.cobblemon.oneblock.core.world

import io.github.sk4ndulf.cobblemon.oneblock.core.config.MainConfig
import net.minecraft.core.BlockPos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * THE single place for all island grid geometry (PROJECT_PLAN.md section 2).
 * Slot numbering follows an Ulam spiral around the hub; slot 0 = (0,0) is the hub
 * and is never assigned.
 */
object GridMath {

    /** Y level of every island's OneBlock (same level as the hub platform surface). */
    const val ISLAND_Y = HubManager.HUB_Y - 1

    data class GridPos(val gx: Int, val gz: Int)

    /**
     * Maps a spiral slot (>= 1) to grid coordinates. Verified properties: unique per slot,
     * never (0,0), consecutive slots are grid neighbours.
     */
    fun slotToGrid(slot: Int): GridPos {
        require(slot >= 1) { "Slot must be >= 1 (slot 0 is the hub)" }
        val ring = ((sqrt(slot.toDouble()) + 1) / 2).toInt()
        val p = slot - (2 * ring - 1) * (2 * ring - 1)
        val side = p / (2 * ring)
        val offset = p % (2 * ring)
        return when (side) {
            0 -> GridPos(ring, -ring + 1 + offset)
            1 -> GridPos(ring - 1 - offset, ring)
            2 -> GridPos(-ring, ring - 1 - offset)
            else -> GridPos(-ring + 1 + offset, -ring)
        }
    }

    /** World position of the slot's OneBlock (= island anchor). */
    fun slotToAnchor(slot: Int, config: MainConfig): BlockPos {
        val grid = slotToGrid(slot)
        val spacing = config.islandSpacing
        return BlockPos(grid.gx * spacing, ISLAND_Y, grid.gz * spacing)
    }

    /**
     * True when the island's maximum footprint (max_island_size square around the anchor)
     * would intersect the hub protection circle. Such slots are skipped during allocation —
     * otherwise hub protection would swallow parts of the island.
     */
    fun intersectsHub(slot: Int, config: MainConfig): Boolean {
        val anchor = slotToAnchor(slot, config)
        val half = MainConfig.chunkAlign(config.maxIslandSize) / 2L
        val hubRadius = config.hubRadius.toLong()
        // Distance from origin to the closest point of the island square.
        val dx = max(0L, kotlin.math.abs(anchor.x.toLong()) - half)
        val dz = max(0L, kotlin.math.abs(anchor.z.toLong()) - half)
        return dx * dx + dz * dz <= hubRadius * hubRadius
    }

    /**
     * Border side length of the island area at the given border level. If the config
     * provides manual `border_level_sizes`, those win; otherwise level 1 is a fixed
     * 16x16 start, level 8 equals max_island_size, and levels in between interpolate
     * exponentially. All values are chunk-aligned.
     */
    fun borderSizeAt(level: Int, config: MainConfig): Int {
        val clamped = level.coerceIn(1, 8)
        config.borderLevelSizes.takeIf { it.size == 8 }?.let { return it[clamped - 1] }
        val start = 16.0
        val end = MainConfig.chunkAlign(config.maxIslandSize).toDouble()
        val factor = Math.pow(end / start, (clamped - 1) / 7.0)
        return MainConfig.chunkAlign((start * factor).toInt())
    }
}
