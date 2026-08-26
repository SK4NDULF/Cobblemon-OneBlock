package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Rank arithmetic.
 *
 * The cumulative rule is the one worth pinning down: reaching rank 3 means ranks 1, 2 and 3
 * all apply. Every consumer relies on it, and getting it wrong would either pay a ladder out
 * three times or make the lower ranks vanish when a higher one is bought.
 */
class TechNodeTest {

    private fun ladder() = TechNode(
        id = "forest",
        category = TechCategory.ONEBLOCK,
        maxLevel = 3,
        costs = listOf(1, 2, 4),
        requires = emptyList(),
        effects = mapOf(
            1 to listOf(TechEffect.OneBlockBiome("forest", 1)),
            3 to listOf(TechEffect.OneBlockBiome("forest", 3)),
        ),
        displayName = "Forest",
        description = "",
        icon = "minecraft:oak_log",
    )

    @Test
    fun `cost of the next rank walks the cost list`() {
        val node = ladder()
        assertEquals(1, node.costOfNext(0))
        assertEquals(2, node.costOfNext(1))
        assertEquals(4, node.costOfNext(2))
    }

    @Test
    fun `a maxed node has no next rank`() {
        assertNull(ladder().costOfNext(3))
    }

    @Test
    fun `total cost is the whole ladder`() {
        assertEquals(7, ladder().totalCost)
    }

    @Test
    fun `effects are cumulative up to the reached rank`() {
        val node = ladder()
        assertEquals(0, node.effectsUpTo(0).size)
        assertEquals(1, node.effectsUpTo(1).size)
        // Rank 2 grants nothing of its own, but must not lose rank 1.
        assertEquals(1, node.effectsUpTo(2).size)
        assertEquals(2, node.effectsUpTo(3).size)
    }

    @Test
    fun `the highest tier of a biome ladder is the last one granted`() {
        val tiers = ladder().effectsUpTo(3).filterIsInstance<TechEffect.OneBlockBiome>().map { it.tier }
        assertEquals(listOf(1, 3), tiers)
    }
}
