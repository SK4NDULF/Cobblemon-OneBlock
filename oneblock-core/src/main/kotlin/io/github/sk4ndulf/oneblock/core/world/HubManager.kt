package io.github.sk4ndulf.oneblock.core.world

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.db.MetaRepository
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks

/**
 * The hub: a circular platform at (0,64,0) in `oneblock:world`, surrounded by a full
 * protection zone of `hub_radius` blocks where nobody breaks or places anything
 * (admins excepted when `hub_allow_building` is true).
 */
object HubManager {

    const val HUB_Y = 64
    private const val META_PLATFORM_GENERATED = "hub_platform_generated"

    val spawnPos: BlockPos = BlockPos(0, HUB_Y, 0)

    fun registerProtection() {
        PlayerBlockBreakEvents.BEFORE.register { level, player, pos, _, _ ->
            if (isProtectedFor(level, pos, player)) {
                (player as? ServerPlayer)?.displayClientMessage(ServerLang.msg("oneblock.hub.no_break"), true)
                false
            } else {
                true
            }
        }

        UseBlockCallback.EVENT.register { player, level, hand, hitResult ->
            if (player is ServerPlayer && player.getItemInHand(hand).item is BlockItem) {
                val placePos = hitResult.blockPos.relative(hitResult.direction)
                if (isProtectedFor(level, placePos, player) || isProtectedFor(level, hitResult.blockPos, player)) {
                    player.displayClientMessage(ServerLang.msg("oneblock.hub.no_place"), true)
                    return@register InteractionResult.FAIL
                }
            }
            InteractionResult.PASS
        }
    }

    /** True when the position is inside the hub protection circle (ignoring permissions). */
    fun isInHub(level: Level, pos: BlockPos): Boolean {
        if (level.dimension() != OneBlockDimension.WORLD_KEY) return false
        val radius = OneBlockCore.configManager.mainConfig.hubRadius.toLong()
        val x = pos.x.toLong()
        val z = pos.z.toLong()
        return x * x + z * z <= radius * radius
    }

    private fun isProtectedFor(level: Level, pos: BlockPos, player: Player): Boolean {
        if (!isInHub(level, pos)) return false
        val config = OneBlockCore.configManager.mainConfig
        if (config.hubAllowBuilding && player.hasPermissions(2)) return false
        return true
    }

    /**
     * Server-start hook: ensures the world spawn points at the hub and generates the
     * platform exactly once (tracked in the meta table).
     */
    fun onServerStarted(server: MinecraftServer) {
        val level = OneBlockDimension.level(server)
        if (level == null) {
            OneBlockCore.LOGGER.error("Dimension oneblock:world is missing — datapack not loaded?")
            return
        }
        level.setDefaultSpawnPos(spawnPos, 0.0f)

        val database = OneBlockCore.database ?: return
        val meta = MetaRepository(database)
        if (meta.get(META_PLATFORM_GENERATED) == null) {
            val radius = OneBlockCore.configManager.mainConfig.hubPlatformRadius
            buildPlatform(level, radius)
            meta.setAsync(META_PLATFORM_GENERATED, "1")
            OneBlockCore.LOGGER.info("Hub platform generated at (0,{},0) with radius {}.", HUB_Y, radius)
        }
    }

    private fun buildPlatform(level: ServerLevel, radius: Int) {
        val floorY = HUB_Y - 1
        val rSquared = radius * radius
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                if (x * x + z * z <= rSquared) {
                    level.setBlockAndUpdate(BlockPos(x, floorY, z), Blocks.SMOOTH_STONE.defaultBlockState())
                }
            }
        }
        // Visible center marker so players and admins can find (0,64,0) at a glance.
        level.setBlockAndUpdate(BlockPos(0, floorY, 0), Blocks.CHISELED_STONE_BRICKS.defaultBlockState())
    }

    /** Teleports a player to the hub spawn and anchors their respawn there. */
    fun sendToHub(player: ServerPlayer) {
        val level = OneBlockDimension.level(player.server) ?: return
        player.teleportTo(level, 0.5, HUB_Y.toDouble(), 0.5, 0.0f, 0.0f)
        player.setRespawnPosition(OneBlockDimension.WORLD_KEY, spawnPos, 0.0f, true, false)
    }
}
