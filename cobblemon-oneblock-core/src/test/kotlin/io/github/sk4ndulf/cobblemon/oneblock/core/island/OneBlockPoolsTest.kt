package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The dimension-to-pool mapping: the single place where "where you are" becomes "what you get".
 *
 * This is the base concept of the mod, and it is the kind of wiring that fails silently — an
 * island handed Nether blocks in the Overworld looks like a tuning mistake, not a bug, and
 * nothing else in the game would complain.
 */
class OneBlockPoolsTest {

    @Test
    fun `each of our dimensions draws from its own list`() {
        assertEquals("overworld", OneBlockPools.poolNameFor(OneBlockDimension.OVERWORLD_KEY))
        assertEquals("nether", OneBlockPools.poolNameFor(OneBlockDimension.NETHER_KEY))
        assertEquals("end", OneBlockPools.poolNameFor(OneBlockDimension.END_KEY))
    }

    @Test
    fun `a dimension that is not ours has no pool`() {
        // Vanilla worlds must fall through untouched — the mod only owns its own dimensions.
        assertNull(OneBlockPools.poolNameFor(net.minecraft.world.level.Level.OVERWORLD))
        assertNull(OneBlockPools.poolNameFor(net.minecraft.world.level.Level.NETHER))
        assertNull(OneBlockPools.poolNameFor(net.minecraft.world.level.Level.END))
        assertNull(
            OneBlockPools.poolNameFor(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("othermod", "world")),
            ),
        )
    }

    @Test
    fun `every dimension the mod owns has a pool, and no pool is orphaned`() {
        // Adding a dimension without a config list would silently give it stone forever.
        assertEquals(OneBlockDimension.ALL.toSet(), OneBlockPools.POOL_NAMES.keys)
        assertEquals(
            OneBlockPools.POOL_NAMES.values.size,
            OneBlockPools.POOL_NAMES.values.toSet().size,
            "two dimensions share a pool name, so one would overwrite the other",
        )
    }
}
