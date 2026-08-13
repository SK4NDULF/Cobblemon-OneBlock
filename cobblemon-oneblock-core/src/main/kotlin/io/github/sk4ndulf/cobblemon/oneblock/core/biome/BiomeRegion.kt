package io.github.sk4ndulf.cobblemon.oneblock.core.biome

import net.minecraft.core.BlockPos

/**
 * A cuboid biome override on an island. Coordinates are already snapped to Minecraft's
 * 4x4x4 biome cell grid, so a region's edges always coincide with real biome boundaries
 * (the "hard cut" from the project plan — no blending, no ragged edges).
 */
data class BiomeRegion(
    val id: Long,
    val islandId: Long,
    val minX: Int, val minY: Int, val minZ: Int,
    val maxX: Int, val maxY: Int, val maxZ: Int,
    val biomeId: String,
) {
    fun contains(pos: BlockPos): Boolean =
        pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ

    val sizeDescription: String get() = "${maxX - minX + 1}x${maxY - minY + 1}x${maxZ - minZ + 1}"

    companion object {
        /** Minecraft stores biomes per 4x4x4 cell — every selection snaps to that grid. */
        const val BIOME_CELL = 4

        fun snapDown(value: Int): Int = Math.floorDiv(value, BIOME_CELL) * BIOME_CELL

        fun snapUp(value: Int): Int = snapDown(value) + BIOME_CELL - 1
    }
}
