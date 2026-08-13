package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Tech points from beating Cobblemon NPC trainers.
 *
 * **Why the trainer's identity comes from a MoLang config variable and not from our database:**
 * keying on the NPC entity's UUID would reset an island's progress the moment an admin rebuilt
 * the gym, and a list of trainer ids in a config file would mean a config edit and a reload for
 * every trainer placed. Cobblemon NPC classes carry arbitrary config variables that are
 * settable in game, so a trainer is configured where it stands:
 *
 *     /npc edit <npc> variable trainer_id gym_rock
 *     /npc edit <npc> variable points 3
 *
 * Move it, rebuild it, or run five copies of the same gym in five event areas — all of them
 * behave correctly, because the identity travels with the configuration rather than with the
 * entity.
 *
 * Whether the island has *already* beaten this trainer is our database's business, not
 * MoLang's: a MoLang variable lives on one entity, and this question is about an island.
 */
object NpcPoints {

    /**
     * NPCs already reported as unconfigured, so the log says it once instead of on every
     * battle. Keyed by entity uuid — a rebuilt NPC is a new entity and worth reporting again.
     */
    private val reportedUnconfigured = HashSet<UUID>()

    fun register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL) { event ->
            val defeated = event.losers.filterIsInstance<NPCBattleActor>()
            if (defeated.isEmpty()) return@subscribe

            val winners = event.winners.filterIsInstance<PlayerBattleActor>().mapNotNull { it.entity }
            if (winners.isEmpty()) return@subscribe

            for (actor in defeated) {
                award(actor.npc, winners)
            }
        }
    }

    private fun award(npc: NPCEntity, winners: List<ServerPlayer>) {
        val config = OneBlockCore.pointSources
        val trainerId = npc.config.map[config.npcIdVariable]?.asString()?.trim().orEmpty()
        if (trainerId.isEmpty()) {
            if (reportedUnconfigured.add(npc.uuid)) {
                OneBlockCore.LOGGER.info(
                    "NPC {} at {} has no '{}' variable, so beating it grants no tech points. " +
                        "Set one with: /npc edit <npc> variable {} <id>",
                    npc.uuid, npc.blockPosition(), config.npcIdVariable, config.npcIdVariable,
                )
            }
            return
        }

        val points = npc.config.map[config.npcPointsVariable]
            ?.asDouble()?.toLong()?.takeIf { it > 0 }
            ?: config.npcDefaultPoints
        val sourceId = TechPointService.sourceId(TechPointService.KIND_NPC, trainerId)
        val manager = OneBlockCore.islandManager ?: return

        // One claim per island, not per winning player. Two members of the same island in the
        // same battle is the obvious case; the loop also covers a battle where players from
        // two different islands won together, and those are genuinely two separate claims.
        val handled = HashSet<Long>()
        for (player in winners) {
            val island: IslandData = manager.islandDataOf(player.uuid) ?: continue
            if (!handled.add(island.id)) continue

            val claimed = TechPointService.claim(
                island = island,
                sourceId = sourceId,
                amount = points,
                claimedBy = player.uuid,
                displayName = npc.effectiveNameForPoints(),
                server = player.server,
            )
            if (!claimed) {
                player.displayClientMessage(
                    ServerLang.msg("cobblemon_oneblock.tech.already_claimed", npc.effectiveNameForPoints())
                        .withStyle(ChatFormatting.GRAY),
                    true,
                )
            }
        }
    }

    /** The trainer's display name, falling back to the claim id when it has none worth showing. */
    private fun NPCEntity.effectiveNameForPoints(): String = name.string.ifBlank { uuid.toString() }
}
