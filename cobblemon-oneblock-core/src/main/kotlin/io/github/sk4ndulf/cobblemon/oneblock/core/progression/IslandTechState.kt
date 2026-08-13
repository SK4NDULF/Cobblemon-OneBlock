package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import java.util.UUID

/**
 * One island's tech progress, held in memory for fast lookups and written through to the
 * database. Server-thread only, like every other island-scoped state in this mod.
 *
 * Two point counters, not one: [balance] is what is left to spend, [lifetimeEarned] never
 * goes down. Requirements of the `tech_points>=N` kind read the lifetime figure, because a
 * gate that opens and then closes again when the island spends its savings would be
 * incomprehensible in play.
 */
class IslandTechState(val islandId: Long) {

    /** Node id to the level the island has reached. Absent means level 0. */
    val levels: MutableMap<String, Int> = HashMap()

    /** Source ids this island has already banked — see [TechPointService]. */
    val claims: MutableSet<String> = HashSet()

    /** Members allowed to spend the island's points. The owner always may and is not listed. */
    val spenders: MutableSet<UUID> = HashSet()

    var balance: Long = 0
    var lifetimeEarned: Long = 0

    fun levelOf(nodeId: String): Int = levels[nodeId] ?: 0

    fun hasClaimed(sourceId: String): Boolean = sourceId in claims

    /**
     * Points this island has sunk into one category — what a `spent:` gate reads.
     *
     * Recomputed from the levels rather than stored as a counter: a stored total would drift
     * the moment an admin retunes a cost in `techtree.json5`, and this is only read when a
     * node is inspected or bought, never in a hot path.
     */
    fun spentIn(tree: TechTree, category: TechCategory): Long =
        levels.entries.sumOf { (nodeId, level) ->
            val node = tree.node(nodeId)
            if (node == null || node.category != category) 0L else node.costs.take(level).sum()
        }

    /** Every effect currently active on this island, in node id order for stable output. */
    fun activeEffects(tree: TechTree): List<TechEffect> =
        levels.entries
            .sortedBy { it.key }
            .flatMap { (nodeId, level) -> tree.node(nodeId)?.effectsUpTo(level).orEmpty() }
}
