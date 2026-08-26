package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Tech points from advancements.
 *
 * Called by `PlayerAdvancementsMixin` the moment an advancement is completed. The mixin itself
 * holds no logic — the mixin package may only contain mixin classes (see `HANDOFF.md` §3), and
 * this is also the piece worth being able to read without Mixin in the way.
 *
 * **A limit worth knowing about, because it looks like a bug otherwise:** advancements are
 * per-player and permanent, while claims are per-island. Only advancements *completed while
 * the player is on an island* pay out. Something the player finished before they had an island
 * — or on a previous island, before a reset — never fires again, so those points are gone for
 * that player.
 *
 * That is the deliberate direction to fail in. The alternative, granting every already-finished
 * advancement when an island is created, turns `/ob reset` into an infinite point machine:
 * reset, instantly re-claim everything already completed, reset again. Losing a few points on
 * a reset is a far smaller problem than that.
 */
object AdvancementPoints {

    /** Invoked from the mixin when [player] has just completed [advancement]. */
    @JvmStatic
    fun onCompleted(player: ServerPlayer, advancement: ResourceLocation) {
        val points = OneBlockCore.pointSources.advancements[advancement.toString()] ?: return
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid) ?: return

        TechPointService.claim(
            island = island,
            sourceId = TechPointService.sourceId(TechPointService.KIND_ADVANCEMENT, advancement.toString()),
            amount = points,
            claimedBy = player.uuid,
            displayName = advancement.toString(),
            server = player.server,
        )
    }
}
