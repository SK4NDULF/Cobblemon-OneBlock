package io.github.sk4ndulf.cobblemon.oneblock.core.progression

/**
 * Reads the tech effects an island currently has.
 *
 * Consumers ask questions here ("what is this island's chest bonus?") instead of walking the
 * effect list themselves, so the rule for how multiple nodes combine lives in one place. It
 * matters more than it looks: effects on a ladder are cumulative, so a node at rank 3 also
 * grants its rank 1 and 2 effects, and summing those instead of taking the highest would pay
 * out three times for one purchase.
 *
 * The rule for every scalar effect on a ladder is therefore **highest wins, not sum.**
 */
object TechEffects {

    /** Extra treasure chest chance, on top of the configured base, from `chest_chance` nodes. */
    fun chestChanceBonus(islandId: Long): Double =
        highest(islandId) { (it as? TechEffect.ChestChance)?.added }

    private inline fun highest(islandId: Long, select: (TechEffect) -> Double?): Double {
        var best = 0.0
        for (effect in TechService.stateOf(islandId).activeEffects(TechService.tree)) {
            select(effect)?.let { best = maxOf(best, it) }
        }
        return best
    }
}
