package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Gate parsing.
 *
 * These cases are the ones that were previously checked by injecting broken entries into a
 * config file and reading a server log, which took two minutes per run. The rejection cases
 * matter as much as the accepting ones: an unparseable gate drops its whole node, so a parser
 * that is too lenient silently ungates endgame content and one that is too strict deletes it.
 */
class TechRequirementTest {

    @Test
    fun `node gate with an explicit rank`() {
        assertEquals(TechRequirement.Node("forest", 2), TechRequirement.parse("node:forest>=2"))
    }

    @Test
    fun `node gate without a rank means rank one`() {
        assertEquals(TechRequirement.Node("flight", 1), TechRequirement.parse("node:flight"))
    }

    @Test
    fun `category spend gate`() {
        assertEquals(
            TechRequirement.CategorySpend(TechCategory.ONEBLOCK, 15),
            TechRequirement.parse("spent:oneblock>=15"),
        )
    }

    @Test
    fun `category names are case insensitive`() {
        assertEquals(
            TechRequirement.CategorySpend(TechCategory.ONEBLOCK, 5),
            TechRequirement.parse("spent:ONEBLOCK>=5"),
        )
    }

    @Test
    fun `boss and lifetime point gates`() {
        assertEquals(TechRequirement.Boss("nether_king"), TechRequirement.parse("boss:nether_king"))
        assertEquals(TechRequirement.LifetimePoints(120), TechRequirement.parse("tech_points>=120"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(TechRequirement.Node("cave", 3), TechRequirement.parse("  node:cave >= 3  "))
    }

    @Test
    fun `unknown category is rejected rather than defaulted`() {
        assertNull(TechRequirement.parse("spent:not_a_category>=5"))
    }

    @Test
    fun `non-numeric threshold is rejected`() {
        assertNull(TechRequirement.parse("spent:oneblock>=abc"))
        assertNull(TechRequirement.parse("node:forest>=x"))
        assertNull(TechRequirement.parse("tech_points>=lots"))
    }

    @Test
    fun `spend gate without an operator is rejected`() {
        assertNull(TechRequirement.parse("spent:oneblock"))
    }

    @Test
    fun `empty ids are rejected`() {
        assertNull(TechRequirement.parse("node:"))
        assertNull(TechRequirement.parse("boss:"))
        assertNull(TechRequirement.parse("boss:   "))
    }

    @Test
    fun `rank zero is rejected because every node starts there`() {
        assertNull(TechRequirement.parse("node:forest>=0"))
        assertNull(TechRequirement.parse("node:forest>=-1"))
    }

    @Test
    fun `unknown prefixes are rejected`() {
        assertNull(TechRequirement.parse("nonsense_requirement"))
        assertNull(TechRequirement.parse(""))
        assertNull(TechRequirement.parse("forest>=2"))
    }
}
