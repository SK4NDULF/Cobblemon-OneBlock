package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tree validation.
 *
 * Every case here is one a hand-edited `techtree.json5` can produce, and every one of them
 * used to need a server boot to check. The rule under test throughout: a broken tree yields a
 * smaller tree and a loud log, never a failed boot and never a node with a dangling gate.
 */
class TechTreeTest {

    private fun node(
        id: String,
        costs: List<Long> = listOf(1),
        requires: List<TechRequirement> = emptyList(),
        category: TechCategory = TechCategory.ONEBLOCK,
        effects: Map<Int, List<TechEffect>> = mapOf(1 to listOf(TechEffect.OneBlockBiome("forest", 1))),
    ) = TechNode(
        id = id,
        category = category,
        maxLevel = costs.size,
        costs = costs,
        requires = requires,
        effects = effects,
        displayName = id,
        description = "",
        icon = "minecraft:paper",
    )

    @Test
    fun `a clean tree survives intact`() {
        val tree = TechTree.build(
            listOf(node("forest", costs = listOf(1, 2, 3)), node("cave", requires = listOf(TechRequirement.Node("forest", 2)))),
        )
        assertEquals(setOf("forest", "cave"), tree.nodes.keys)
        assertTrue(tree.problems.isEmpty())
    }

    @Test
    fun `a gate pointing at a node that does not exist drops the node`() {
        val tree = TechTree.build(listOf(node("orphan", requires = listOf(TechRequirement.Node("ghost", 1)))))
        assertTrue(tree.nodes.isEmpty())
        assertTrue(tree.problems.single().contains("does not exist"), tree.problems.toString())
    }

    @Test
    fun `a gate above the target's maximum rank drops the node and names both numbers`() {
        val tree = TechTree.build(
            listOf(node("forest", costs = listOf(1, 2)), node("greedy", requires = listOf(TechRequirement.Node("forest", 9)))),
        )
        assertEquals(setOf("forest"), tree.nodes.keys)
        val problem = tree.problems.single()
        assertTrue(problem.contains("9"), problem)
        assertTrue(problem.contains("2"), problem)
    }

    @Test
    fun `dropping a node cascades to everything that depended on it`() {
        // b requires a (which is bogus), c requires b. All three must go, not just a.
        val tree = TechTree.build(
            listOf(
                node("b", requires = listOf(TechRequirement.Node("ghost", 1))),
                node("c", requires = listOf(TechRequirement.Node("b", 1))),
                node("independent"),
            ),
        )
        assertEquals(setOf("independent"), tree.nodes.keys)
        assertEquals(2, tree.problems.size)
    }

    @Test
    fun `a requirement cycle is removed rather than left permanently unbuyable`() {
        val tree = TechTree.build(
            listOf(
                node("a", requires = listOf(TechRequirement.Node("b", 1))),
                node("b", requires = listOf(TechRequirement.Node("a", 1))),
                node("fine"),
            ),
        )
        assertEquals(setOf("fine"), tree.nodes.keys)
        assertTrue(tree.problems.all { it.contains("cycle") }, tree.problems.toString())
    }

    @Test
    fun `a node requiring itself is a cycle`() {
        val tree = TechTree.build(listOf(node("self", requires = listOf(TechRequirement.Node("self", 1)))))
        assertTrue(tree.nodes.isEmpty())
    }

    @Test
    fun `non-node gates never drop a node`() {
        // Boss and spend gates cannot dangle — there is nothing for them to point at.
        val tree = TechTree.build(
            listOf(
                node(
                    "gated",
                    requires = listOf(
                        TechRequirement.Boss("nether_king"),
                        TechRequirement.CategorySpend(TechCategory.ONEBLOCK, 50),
                        TechRequirement.LifetimePoints(100),
                    ),
                ),
            ),
        )
        assertEquals(setOf("gated"), tree.nodes.keys)
    }

    @Test
    fun `total cost sums every rank of every node`() {
        val tree = TechTree.build(listOf(node("a", costs = listOf(1, 2, 3)), node("b", costs = listOf(10))))
        assertEquals(16, tree.totalCost)
    }

    @Test
    fun `a node is implemented when at least one effect has a consumer`() {
        val tree = TechTree.build(
            listOf(
                node("works", effects = mapOf(1 to listOf(TechEffect.OneBlockBiome("forest", 1)))),
                node("dead", effects = mapOf(1 to listOf(TechEffect.PlayerEffect("fly", 1.0)))),
                node("gate_only", effects = emptyMap()),
            ),
        )
        assertTrue(tree.isImplemented(tree.nodes.getValue("works")))
        assertFalse(tree.isImplemented(tree.nodes.getValue("dead")))
        // A node with no effects exists to be a prerequisite; buying it is doing its job.
        assertTrue(tree.isImplemented(tree.nodes.getValue("gate_only")))
        assertEquals(listOf("dead"), tree.unimplementedNodes().map { it.id })
    }

    @Test
    fun `categories are grouped and sorted for stable display`() {
        val tree = TechTree.build(
            listOf(
                node("z_one", category = TechCategory.ONEBLOCK),
                node("a_one", category = TechCategory.ONEBLOCK),
                node("island", category = TechCategory.ISLAND),
            ),
        )
        assertEquals(listOf("a_one", "z_one"), tree.byCategory.getValue(TechCategory.ONEBLOCK).map { it.id })
        assertEquals(1, tree.byCategory.getValue(TechCategory.ISLAND).size)
    }
}
