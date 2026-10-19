package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.storage.loot.LootTable
import org.slf4j.Logger

/**
 * Treasure chests from the OneBlock, sorted by dimension.
 *
 * A share of breaks regenerates the anchor as a loot chest instead of a plain block, filled
 * from a real chest loot table — the very ones world generation uses. The pool is discovered
 * from the server's loot table registry at startup rather than hardcoded, so a data pack that
 * adds `chests/...` tables is picked up without a code change.
 *
 * **Sorted by dimension, because that is the mod's base concept.** A Nether anchor gives
 * bastion and fortress loot and never a village chest; the End gives End City loot. The sort
 * is done on the table id, since that is the only signal a loot table carries about where it
 * belongs — a heuristic, but a stable one, and [Config.extra] exists for anything it misses.
 *
 * The contents are not rolled here: the chest gets the loot table and a seed, and vanilla
 * fills it the first time it is opened — or dropped, if a player breaks it unopened.
 *
 * The chance is flat and never scales with island progress. Difficulty scales on this project,
 * rewards do not.
 */
object ChestLoot {

    /** Loot table ids under this path are chest loot: `minecraft:chests/abandoned_mineshaft`. */
    private const val CHEST_PATH_PREFIX = "chests/"
    private const val VANILLA_NAMESPACE = "minecraft"

    const val DEFAULT_ENABLED = true
    const val DEFAULT_CHANCE = 0.02
    const val DEFAULT_INCLUDE_MODDED = false

    /**
     * Id fragments that mark a table as belonging to a dimension.
     *
     * Only the two non-Overworld sets need listing: everything else under `chests/` is
     * Overworld by default, which is both the largest group and the right fallback for a
     * modded table nobody has classified.
     */
    private val NETHER_MARKERS = listOf("bastion", "nether_bridge", "nether_fortress", "ruined_portal")
    private val END_MARKERS = listOf("end_city")

    private val HORIZONTAL = arrayOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)

    data class Config(
        val enabled: Boolean = DEFAULT_ENABLED,
        val chance: Double = DEFAULT_CHANCE,
        val includeModded: Boolean = DEFAULT_INCLUDE_MODDED,
        val blacklist: Set<String> = emptySet(),
        /** Dimension pool name to loot table ids added to it verbatim. */
        val extra: Map<String, List<String>> = emptyMap(),
    )

    private var config = Config()
    private var tables: Map<String, List<ResourceKey<LootTable>>> = emptyMap()

    /** Applies config values. Clamps the chance so a typo cannot turn every block into a chest. */
    fun configure(config: Config) {
        this.config = config.copy(chance = config.chance.coerceIn(0.0, 1.0))
    }

    /**
     * Collects the chest loot tables the server actually has and sorts them by dimension.
     * Loot tables are a reloadable, data-pack driven registry, so this needs a started server
     * and has to run again after `/ob reload`.
     */
    fun buildPool(server: MinecraftServer, logger: Logger) {
        if (!config.enabled) {
            tables = emptyMap()
            logger.info("OneBlock treasure chests are disabled.")
            return
        }

        val known = server.reloadableRegistries().getKeys(Registries.LOOT_TABLE)
        val sorted = mutableMapOf(
            POOL_OVERWORLD to mutableListOf<ResourceKey<LootTable>>(),
            POOL_NETHER to mutableListOf(),
            POOL_END to mutableListOf(),
        )
        var moddedSkipped = 0

        for (id in known) {
            if (!id.path.startsWith(CHEST_PATH_PREFIX)) continue
            if (id.toString() in config.blacklist) continue
            if (id.namespace != VANILLA_NAMESPACE && !config.includeModded) {
                moddedSkipped++
                continue
            }
            sorted.getValue(poolFor(id.path)).add(ResourceKey.create(Registries.LOOT_TABLE, id))
        }

        // Explicitly listed tables. Needed because the `chests/` convention is vanilla's alone:
        // Cobblemon keeps its structure chest loot under `ruins/gilded_chests/...` and friends,
        // so no path filter could ever find it.
        for ((pool, ids) in config.extra) {
            for (entry in ids) {
                if (entry in config.blacklist) continue
                val id = ResourceLocation.tryParse(entry)
                if (id == null) {
                    logger.warn("chests.{}_extra: '{}' is not a valid loot table id — skipped.", pool, entry)
                    continue
                }
                if (id !in known) {
                    logger.warn("chests.{}_extra: '{}' does not exist on this server — skipped.", pool, entry)
                    continue
                }
                sorted[pool]?.add(ResourceKey.create(Registries.LOOT_TABLE, id))
            }
        }

        tables = sorted.mapValues { it.value.distinct() }
        val summary = tables.entries.sortedBy { it.key }.joinToString(", ") { "${it.key} ${it.value.size}" }
        if (tables.values.all { it.isEmpty() }) {
            logger.warn("No chest loot tables found — OneBlock treasure chests stay off this session.")
            return
        }
        logger.info("OneBlock treasure chests: {} tables ({}), {}% of breaks.", summary, "by dimension", config.chance * 100.0)
        if (moddedSkipped > 0) {
            logger.info(
                "{} modded chest loot tables were skipped — set chests.include_modded to true in {} " +
                    "to use them as well.",
                moddedSkipped, OneBlockPools.FILE_NAME,
            )
        }
    }

    /** Which dimension pool a loot table id belongs to. Overworld is the fallback. */
    private fun poolFor(path: String): String = when {
        NETHER_MARKERS.any { path.contains(it) } -> POOL_NETHER
        END_MARKERS.any { path.contains(it) } -> POOL_END
        else -> POOL_OVERWORLD
    }

    /**
     * Rolls for a chest in this dimension. Returns the loot table to use, or null for a
     * normal block.
     */
    fun roll(level: Level, random: RandomSource): ResourceKey<LootTable>? {
        if (!config.enabled || config.chance <= 0.0) return null
        val pool = tables[poolName(level)].orEmpty()
        if (pool.isEmpty()) return null
        if (random.nextDouble() >= config.chance) return null
        return pool[random.nextInt(pool.size)]
    }

    private fun poolName(level: Level): String = when (level.dimension()) {
        OneBlockDimension.NETHER_KEY -> POOL_NETHER
        OneBlockDimension.END_KEY -> POOL_END
        else -> POOL_OVERWORLD
    }

    /**
     * The block state to place for a rolled chest. Explicitly [ChestType.SINGLE] so it never
     * merges with a chest a player parked next to the anchor.
     */
    fun chestState(random: RandomSource): BlockState =
        Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.FACING, HORIZONTAL[random.nextInt(HORIZONTAL.size)])
            .setValue(ChestBlock.TYPE, ChestType.SINGLE)

    /**
     * Assigns the loot table to the chest that was just placed. Call this after the block is
     * in the world — the block entity does not exist before that.
     */
    fun fill(level: ServerLevel, pos: BlockPos, table: ResourceKey<LootTable>) {
        val chest = level.getBlockEntity(pos) as? ChestBlockEntity ?: return
        chest.lootTable = table
        chest.lootTableSeed = level.random.nextLong()
        chest.setChanged()
    }

    const val POOL_OVERWORLD = "overworld"
    const val POOL_NETHER = "nether"
    const val POOL_END = "end"
}
