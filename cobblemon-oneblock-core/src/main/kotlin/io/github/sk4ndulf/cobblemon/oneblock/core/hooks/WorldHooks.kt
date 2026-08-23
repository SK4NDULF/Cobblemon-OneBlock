package io.github.sk4ndulf.cobblemon.oneblock.core.hooks

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubManager
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor

/**
 * Logic behind the Java mixins.
 *
 * This deliberately lives OUTSIDE the mixin package: everything in the package declared
 * by cobblemon_oneblock.mixins.json is treated as a mixin class by the transformer, and a plain
 * helper class there fails to load.
 */
object WorldHooks {

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
            // The OneBlock itself is never blown up: losing it would stall the island
            // until the repair sweep runs. The bedrock under it is explosion-proof anyway.
            manager.isAnchor(pos) ||
                HubManager.isInHub(level, pos) ||
                manager.islandAt(pos) !== centerIsland ||
                centerIsland == null ||
                !manager.isWithinCurrentBorder(centerIsland, pos)
        }
    }

    /**
     * PortalShapeMixin and NetherPortalBlockMixin: no Nether portal works in the OneBlock world.
     *
     * **This closes an escape hatch that made the whole mod pointless.** A nether portal sends
     * an entity to whatever dimension is not the Nether — from our world, that is the real,
     * infinite, unprotected vanilla Nether, and a second portal there reaches the vanilla
     * Overworld. Every border, every protection rule and the entire island economy stop
     * mattering the moment a player steps through, because the whole vanilla world is on the
     * other side.
     *
     * The tech tree itself hands out the key: the Nether biome ladder drops obsidian at tier 4,
     * and the End ladder drops more. Verified on a dev server before the fix — a pig placed in
     * a portal here landed in the Nether at nether-scaled coordinates, exactly as it does in
     * the vanilla Overworld.
     *
     * Blocked in two places rather than one: the shape check stops new portals from forming at
     * all, and the entity check makes any portal that already exists in an old world inert.
     */
    @JvmStatic
    fun blocksPortals(level: LevelAccessor): Boolean =
        level is Level && level.dimension() == OneBlockDimension.WORLD_KEY

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
