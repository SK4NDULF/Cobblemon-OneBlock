package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import org.slf4j.LoggerFactory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Effect rendering, against the real language files.
 *
 * The point is not the wording, it is that every effect type has a key and that the key's
 * placeholders match the arguments passed to it. A `%d` handed a string throws at the moment
 * the player opens the menu, and there is no test in the game for that.
 */
class TechEffectTextTest {

    @BeforeTest
    fun loadLanguage() {
        ServerLang.load("en_us", LoggerFactory.getLogger(TechEffectTextTest::class.java))
    }

    /** Every effect type, so a new one added without a lang key fails here. */
    private fun oneOfEach(): List<TechEffect> = listOf(
        TechEffect.OneBlockBiome("forest", 3),
        TechEffect.OneBlockYield("cave", 0.35),
        TechEffect.ChestChance(0.03),
        TechEffect.BorderSize(288),
        TechEffect.IslandInt("max_biome_regions", 8),
        TechEffect.IslandInt("max_party_size", 6),
        TechEffect.PlayerEffect("hunger", 0.2),
        TechEffect.PlayerEffect("regen", 1.0),
        TechEffect.PlayerEffect("fall", 0.25),
        TechEffect.PlayerEffect("fly", 1.0),
        TechEffect.PokemonWork("fire", "speed", 0.15),
        TechEffect.PokemonWork("fire", "hp", 0.1),
        TechEffect.PokemonWork("fire", "resist", 0.3),
        TechEffect.PokemonWork("fire", "yield", 0.2),
        TechEffect.PokemonWork("fire", "threshold", 0.75),
        TechEffect.PokemonWork("fire", "sleep_regen", 0.5),
        TechEffect.BoostUnlock("shiny", 1.5, 10),
        TechEffect.BoostUnlock("typing", 0.2, 20),
        TechEffect.UnlockFlag("something"),
    )

    @Test
    fun `every effect type renders without falling back to its key`() {
        for (effect in oneOfEach()) {
            val line = TechEffectText.describe(effect)
            // ServerLang returns the key itself when there is no entry, and a key always
            // starts with the namespace — so this catches a missing translation.
            assertFalse(
                line.startsWith("cobblemon_oneblock."),
                "no language entry for ${effect.type}: rendered as '$line'",
            )
            assertTrue(line.isNotBlank(), "${effect.type} rendered blank")
        }
    }

    @Test
    fun `percentages read the way a player expects`() {
        assertEquals("35% chance that Cave blocks drop twice", TechEffectText.describe(TechEffect.OneBlockYield("cave", 0.35)))
        // A whole number must not carry a trailing .0, and a fraction must keep its decimal.
        assertEquals("+3% treasure chest chance", TechEffectText.describe(TechEffect.ChestChance(0.03)))
        assertEquals("+7.5% treasure chest chance", TechEffectText.describe(TechEffect.ChestChance(0.075)))
    }

    @Test
    fun `biome ids are shown by their proper name`() {
        assertTrue(TechEffectText.describe(TechEffect.OneBlockBiome("forest", 1)).contains("Forest"))
        // An id with no lang entry is title-cased rather than shown raw or as a key.
        assertTrue(TechEffectText.describe(TechEffect.OneBlockBiome("swamp", 1)).contains("Swamp"))
    }

    @Test
    fun `a list of effects renders one line each`() {
        val lines = TechEffectText.describe(
            listOf(TechEffect.OneBlockBiome("forest", 1), TechEffect.ChestChance(0.02)),
        )
        assertEquals(2, lines.size)
    }

    @Test
    fun `boost strength keeps its decimal but drops a pointless one`() {
        assertTrue(TechEffectText.describe(TechEffect.BoostUnlock("shiny", 1.5, 10)).contains("1.5x"))
        assertTrue(TechEffectText.describe(TechEffect.BoostUnlock("shiny", 5.0, 60)).contains("5x"))
    }
}
