package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import net.minecraft.ChatFormatting
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import java.util.UUID

/**
 * The single entry point for content-driven tech points.
 *
 * Every source — NPC trainers, advancements, Special bosses — goes through [claim], and
 * nothing else is allowed to. That is what makes the island-scoped rule impossible to forget
 * at a call site: a four-person island must not progress four times as fast as a solo island
 * on identical content, so the *first* member to beat a source banks it for the island and
 * everybody else is told it is already claimed (PROGRESSION_REWORK.md §3.2c).
 *
 * Source ids are namespaced by kind so they cannot collide:
 *   npc:<trainer_id>    a Cobblemon NPC carrying a `trainer_id` config variable
 *   adv:<advancement>   a custom advancement
 *   boss:<boss_id>      a Special — also what satisfies a `boss:` requirement
 */
object TechPointService {

    const val KIND_NPC = "npc"
    const val KIND_ADVANCEMENT = "adv"
    const val KIND_BOSS = "boss"

    fun sourceId(kind: String, id: String): String = "$kind:$id"

    /**
     * Banks [amount] points for the island if it has not already claimed [sourceId].
     *
     * @param displayName what the player sees as the source of the points
     * @return true when the points were granted, false when the island already had them
     */
    fun claim(
        island: IslandData,
        sourceId: String,
        amount: Long,
        claimedBy: UUID,
        displayName: String,
        server: MinecraftServer,
    ): Boolean {
        if (amount <= 0) {
            OneBlockCore.LOGGER.warn("Ignoring a claim of {} points from '{}'.", amount, sourceId)
            return false
        }
        if (!TechService.claimSource(island.id, sourceId, claimedBy)) return false

        TechService.grant(island.id, amount)
        announce(island, server, displayName, amount)
        OneBlockCore.LOGGER.info(
            "Island {} claimed {} for {} points (by {}).", island.id, sourceId, amount, claimedBy,
        )
        return true
    }

    /** True when this island has already banked the source — for "you already beat this" messages. */
    fun alreadyClaimed(island: IslandData, sourceId: String): Boolean =
        TechService.stateOf(island.id).hasClaimed(sourceId)

    /**
     * Tells the whole island, not just the player who earned it.
     *
     * A claim usually happens far from the island, in an event area, and it is the moment the
     * entire economy pays off — the members sitting at home should see their savings grow and
     * know who did it. Offline members find it in `/ob info`.
     */
    private fun announce(island: IslandData, server: MinecraftServer, source: String, amount: Long) {
        val balance = TechService.stateOf(island.id).balance
        val recipients = island.memberSet + island.owner()
        for (uuid in recipients) {
            val player = server.playerList.getPlayer(uuid) ?: continue
            player.sendSystemMessage(
                ServerLang.msg("cobblemon_oneblock.tech.claimed", source, amount, balance)
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
            )
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.2f)
        }
    }
}
