package io.github.sk4ndulf.cobblemon.oneblock.core.world

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.db.MetaRepository
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

/**
 * The hub: a circular platform at (0, 64, 0) with a protection zone of `hub_radius` blocks
 * around it. Enforcement lives in ProtectionManager; this object owns geometry, platform
 * generation and hub teleports.
 *
 * **There is one hub per dimension**, all three at the same X/Z — same rule as the islands.
 * Each is its own place to build on: shops, trainers, whatever belongs in that dimension.
 * The Overworld hub keeps the two jobs it always had on top of that: it is the world spawn,
 * and it is where the mod puts a player who has nowhere else to be.
 */
object HubManager {

    const val HUB_Y = 64

    /**
     * Meta key of the Overworld platform. It is deliberately the bare key the mod has used
     * since the first version — renaming it would make an existing server generate its hub
     * platform a second time, on top of whatever an admin has built there since.
     */
    private const val META_PLATFORM_GENERATED = "hub_platform_generated"

    val spawnPos: BlockPos = BlockPos(0, HUB_Y, 0)

    /** Floor and centre marker of a hub platform. Each dimension gets its own materials. */
    private data class Palette(val floor: Block, val marker: Block)

    private fun paletteFor(dimension: ResourceKey<Level>): Palette = when (dimension) {
        OneBlockDimension.NETHER_KEY -> Palette(Blocks.POLISHED_BLACKSTONE, Blocks.CHISELED_POLISHED_BLACKSTONE)
        OneBlockDimension.END_KEY -> Palette(Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK)
        else -> Palette(Blocks.SMOOTH_STONE, Blocks.CHISELED_STONE_BRICKS)
    }

    private fun metaKeyFor(dimension: ResourceKey<Level>): String =
        if (dimension == OneBlockDimension.OVERWORLD_KEY) {
            META_PLATFORM_GENERATED
        } else {
            "${META_PLATFORM_GENERATED}_${dimension.location().path}"
        }

    /** True when the position is inside a hub protection circle (ignoring permissions). */
    fun isInHub(level: Level, pos: BlockPos): Boolean =
        OneBlockDimension.isOurs(level) && isInHubArea(pos)

    /** XZ-only hub circle check — for callers that already know they're in one of our worlds. */
    fun isInHubArea(pos: BlockPos): Boolean {
        val radius = OneBlockCore.configManager.mainConfig.hubRadius.toLong()
        val x = pos.x.toLong()
        val z = pos.z.toLong()
        return x * x + z * z <= radius * radius
    }

    /**
     * Server-start hook: points the world spawn at the Overworld hub and generates every
     * dimension's platform exactly once (tracked per dimension in the meta table).
     */
    fun onServerStarted(server: MinecraftServer) {
        val overworld = OneBlockDimension.overworld(server)
        if (overworld == null) {
            OneBlockCore.LOGGER.error("Dimension cobblemon_oneblock:world is missing — datapack not loaded?")
            return
        }
        overworld.setDefaultSpawnPos(spawnPos, 0.0f)

        val database = OneBlockCore.database ?: return
        val meta = MetaRepository(database)
        for (level in OneBlockDimension.loadedLevels(server)) {
            val key = metaKeyFor(level.dimension())
            if (meta.get(key) != null) continue
            buildPlatform(level)
            meta.setAsync(key, "1")
        }
    }

    private fun buildPlatform(level: ServerLevel) {
        val radius = OneBlockCore.configManager.mainConfig.hubPlatformRadius
        val palette = paletteFor(level.dimension())
        val floorY = HUB_Y - 1
        val rSquared = radius * radius
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                if (x * x + z * z <= rSquared) {
                    level.setBlockAndUpdate(BlockPos(x, floorY, z), palette.floor.defaultBlockState())
                }
            }
        }
        // Visible center marker so players and admins can find (0,64,0) at a glance.
        level.setBlockAndUpdate(BlockPos(0, floorY, 0), palette.marker.defaultBlockState())
        OneBlockCore.LOGGER.info(
            "Hub platform generated in {} at (0,{},0) with radius {}.",
            level.dimension().location(), HUB_Y, radius,
        )
    }

    /** Teleports a player to the Overworld hub and anchors their respawn there. */
    fun sendToHub(player: ServerPlayer) {
        val level = OneBlockDimension.overworld(player.server) ?: return
        teleportToHub(player, level)
        player.setRespawnPosition(OneBlockDimension.OVERWORLD_KEY, spawnPos, 0.0f, true, false)
    }

    /**
     * Teleports a player to a specific dimension's hub, leaving their respawn point alone.
     *
     * The respawn point stays put on purpose: the Nether and End hubs are places you travel
     * to, not places you live, and waking up there after a death would strand a player far
     * from their island.
     */
    fun sendToHub(player: ServerPlayer, level: ServerLevel) = teleportToHub(player, level)

    private fun teleportToHub(player: ServerPlayer, level: ServerLevel) {
        ensureFloor(level)
        player.teleportTo(level, 0.5, HUB_Y.toDouble(), 0.5, 0.0f, 0.0f)
    }

    /**
     * Safety net for the arrival spot. The platform is generated once at startup and is
     * protected afterwards, so this normally does nothing — but a dimension whose platform
     * never got written (the database was down on that first start) would otherwise drop
     * the arriving player into the void.
     */
    private fun ensureFloor(level: ServerLevel) {
        if (!level.getBlockState(BlockPos(0, HUB_Y - 1, 0)).isAir) return
        OneBlockCore.LOGGER.warn(
            "Hub platform in {} was missing on arrival — rebuilding it.", level.dimension().location(),
        )
        buildPlatform(level)
    }
}
