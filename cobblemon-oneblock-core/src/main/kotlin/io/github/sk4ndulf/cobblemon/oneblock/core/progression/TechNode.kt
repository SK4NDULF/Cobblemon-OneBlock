package io.github.sk4ndulf.cobblemon.oneblock.core.progression

/**
 * One node of the tech tree, as defined in techtree.json5.
 *
 * A node has levels 0..[maxLevel]; level 0 means "not bought". [costs] holds the price of
 * each step, so `costs[0]` buys level 1. Effects are per level and cumulative in meaning —
 * reaching level 3 means the effects of levels 1, 2 and 3 all apply — which is what lets a
 * biome ladder widen a pool step by step instead of replacing it.
 */
data class TechNode(
    val id: String,
    val category: TechCategory,
    val maxLevel: Int,
    /** Cost of each step; size equals [maxLevel]. Index 0 is the cost of reaching level 1. */
    val costs: List<Long>,
    val requires: List<TechRequirement>,
    /** Level (1-based) to the effects that level adds. A level may add none. */
    val effects: Map<Int, List<TechEffect>>,
    val displayName: String,
    val description: String,
    /** Item id shown in the chest menu. Purely presentational; an unknown id falls back. */
    val icon: String,
) {

    /** Cost of going from [currentLevel] to the next one, or null when already maxed. */
    fun costOfNext(currentLevel: Int): Long? =
        if (currentLevel >= maxLevel) null else costs[currentLevel]

    /** Total cost of taking this node from 0 to [maxLevel] — feeds the point budget check. */
    val totalCost: Long get() = costs.sum()

    /** Effects that apply at [level], i.e. everything granted at or below it. */
    fun effectsUpTo(level: Int): List<TechEffect> =
        (1..level).flatMap { effects[it].orEmpty() }

    /** True for a plain on/off node, which is shown differently from a ladder. */
    val isSwitch: Boolean get() = maxLevel == 1
}
