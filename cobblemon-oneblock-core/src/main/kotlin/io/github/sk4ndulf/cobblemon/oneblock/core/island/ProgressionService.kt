package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.api.event.BorderLevelUpEvent
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.world.GridMath
import net.minecraft.ChatFormatting
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Border-level progression. Points come from OneBlock mining only — every party member's
 * breaks count for the island, scaled by configurable diminishing returns so large
 * parties don't level linearly faster (decision #3 in the plan).
 */
object ProgressionService {

    const val MAX_LEVEL = 8

    /** Points one break is currently worth for this island (party-size scaled). */
    fun pointsForBreak(island: IslandData): Double {
        val config = OneBlockCore.configManager.mainConfig
        val partySize = island.memberSet.size + 1
        val factor = 1.0 / (1.0 + (partySize - 1) * config.partyDiminishingReturns)
        return config.pointsPerBreak * factor
    }

    /** Cumulative points required to reach the given level (2..8); level 1 = 0. */
    fun pointsRequiredFor(level: Int): Double {
        if (level <= 1) return 0.0
        val thresholds = OneBlockCore.configManager.mainConfig.borderLevelThresholds
        return thresholds[(level - 2).coerceIn(0, thresholds.size - 1)]
    }

    /**
     * Called for every OneBlock break, after the break counter was incremented.
     * Awards points, applies level-ups, persists, announces, fires the API event.
     */
    fun onBreak(island: IslandData, server: MinecraftServer) {
        island.points += pointsForBreak(island)

        while (island.borderLevel < MAX_LEVEL && island.points >= pointsRequiredFor(island.borderLevel + 1)) {
            val oldLevel = island.borderLevel
            island.borderLevel++
            val newSize = GridMath.borderSizeAt(island.borderLevel, OneBlockCore.configManager.mainConfig)
            announce(island, server, island.borderLevel, newSize)
            OneBlockCore.eventBus.post(BorderLevelUpEvent(island, oldLevel, island.borderLevel, newSize))
            OneBlockCore.LOGGER.info(
                "Island {} reached border level {} ({}x{}).", island.id, island.borderLevel, newSize, newSize,
            )
        }
    }

    private fun announce(island: IslandData, server: MinecraftServer, level: Int, size: Int) {
        val recipients = island.memberSet + island.owner()
        for (uuid in recipients) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            player.sendSystemMessage(
                ServerLang.msg("cobblemon_oneblock.progress.level_up", level, size, size)
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
            )
            player.displayClientMessage(
                ServerLang.msg("cobblemon_oneblock.progress.level_up_bar", level).withStyle(ChatFormatting.GOLD), true,
            )
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f)
        }
    }
}
