package io.github.sk4ndulf.oneblock.core.mixin

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.world.HubManager
import io.github.sk4ndulf.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor

/**
 * Kotlin side of the Java mixins. Keeps the mixin classes one-liners and all logic here.
 */
object MixinHooks {

    /** FallingBlockMixin: gravity blocks never fall while sitting on a OneBlock anchor. */
    @JvmStatic
    fun isOneBlockAnchor(level: ServerLevel, pos: BlockPos): Boolean =
        level.dimension() == OneBlockDimension.WORLD_KEY &&
            OneBlockCore.islandManager?.isAnchor(pos) == true

    /**
     * ExplosionMixin: strips positions an explosion may not destroy. An explosion only
     * affects blocks of the island whose CURRENT border area contains the explosion
     * center — never the hub, never the void buffer, never a neighbouring island.
     */
    @JvmStatic
    fun filterExplosion(level: Level, centerX: Double, centerZ: Double, toBlow: MutableList<BlockPos>) {
        if (level !is ServerLevel || level.dimension() != OneBlockDimension.WORLD_KEY) return
        val manager = OneBlockCore.islandManager ?: run { toBlow.clear(); return }
        val centerPos = BlockPos.containing(centerX, HubManager.HUB_Y.toDouble(), centerZ)
        val centerIsland = manager.islandAt(centerPos)
        toBlow.removeIf { pos ->
            HubManager.isInHub(level, pos) ||
                manager.islandAt(pos) !== centerIsland ||
                centerIsland == null ||
                !manager.isWithinCurrentBorder(centerIsland, pos)
        }
    }

    /**
     * FlowingFluidMixin: fluids may only spread inside an island's CURRENT border area.
     * Blocks flow into the hub, the void buffer, and across island borders.
     */
    @JvmStatic
    fun blockFluidSpread(level: LevelAccessor, pos: BlockPos): Boolean {
        if (level !is ServerLevel || level.dimension() != OneBlockDimension.WORLD_KEY) return false
        if (HubManager.isInHub(level, pos)) return true
        val manager = OneBlockCore.islandManager ?: return true
        val island = manager.islandAt(pos) ?: return true
        return !manager.isWithinCurrentBorder(island, pos)
    }
}
