package io.github.sk4ndulf.cobblemon.oneblock.core.island

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.ChestType
import net.minecraft.world.level.storage.loot.LootTable
import org.slf4j.Logger

/**
 * Treasure chests in the OneBlock pool.
 *
 * A configurable share of breaks regenerates the anchor as a loot chest instead of a plain
 * block. The chest is filled from a real chest loot table — the very ones world generation
 * uses for mineshafts, dungeons, temples, villages, strongholds, shipwrecks and so on. The
 * pool is discovered from the server's loot table registry at startup rather than hardcoded,
 * so a data pack that adds `chests/...` tables is picked up without a code change.
 *
 * The contents are not rolled here: the chest gets the loot table and a seed, and vanilla
 * fills it the first time it is opened — or dropped, if a player breaks it unopened.
 *
 * The chance is flat and never scales with border level. Difficulty scales on this project,
 * rewards do not (see PROJECT_PLAN.md), and a chest is a reward.
 *
 * Configured through the `chests` section of `loottable.json5`; reloaded by `/ob reload`.
 */
object ChestLoot {

    /** Loot table ids under this path are chest loot: `minecraft:chests/abandoned_mineshaft`. */
    private const val CHEST_PATH_PREFIX = "chests/"
    private const val VANILLA_NAMESPACE = "minecraft"

    const val DEFAULT_ENABLED = true
    const val DEFAULT_CHANCE = 0.02
    const val DEFAULT_INCLUDE_MODDED = false

    private val HORIZONTAL = arrayOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)

    private var enabled: Boolean = DEFAULT_ENABLED
    private var chance: Double = DEFAULT_CHANCE
    private var includeModded: Boolean = DEFAULT_INCLUDE_MODDED
    private var blacklist: Set<String> = emptySet()
    private var extra: List<String> = emptyList()

    private var tables: List<ResourceKey<LootTable>> = emptyList()

    /** Applies config values. Clamps the chance so a typo cannot turn every block into a chest. */
    fun configure(
        enabled: Boolean,
        chance: Double,
        includeModded: Boolean,
        blacklist: Set<String>,
        extra: List<String>,
    ) {
        this.enabled = enabled
        this.chance = chance.coerceIn(0.0, 1.0)
        this.includeModded = includeModded
        this.blacklist = blacklist
        this.extra = extra
    }

    /**
     * Collects the chest loot tables the server actually has. Loot tables are a reloadable
     * (data pack driven) registry, so this needs a started server and has to run again after
     * `/ob reload`.
     */
    fun buildPool(server: MinecraftServer, logger: Logger) {
        if (!enabled) {
            tables = emptyList()
            logger.info("OneBlock treasure chests are disabled.")
            return
        }

        val known = server.reloadableRegistries().getKeys(Registries.LOOT_TABLE)
        var moddedSkipped = 0
        val pool = LinkedHashSet<ResourceKey<LootTable>>()
        for (id in known) {
            if (!id.path.startsWith(CHEST_PATH_PREFIX)) continue
            if (id.toString() in blacklist) continue
            if (id.namespace != VANILLA_NAMESPACE && !includeModded) {
                moddedSkipped++
                continue
            }
            pool.add(ResourceKey.create(Registries.LOOT_TABLE, id))
        }

        // Explicitly listed tables. Needed because the `chests/` convention is vanilla's alone:
        // Cobblemon keeps its structure chest loot under `ruins/gilded_chests/...` and friends,
        // so no namespace filter could ever find it.
        for (entry in extra) {
            if (entry in blacklist) continue
            val id = ResourceLocation.tryParse(entry)
            if (id == null) {
                logger.warn("chests.extra: '{}' is not a valid loot table id — skipped.", entry)
                continue
            }
            if (id !in known) {
                logger.warn("chests.extra: loot table '{}' does not exist on this server — skipped.", entry)
                continue
            }
            pool.add(ResourceKey.create(Registries.LOOT_TABLE, id))
        }

        tables = pool.toList()

        if (pool.isEmpty()) {
            logger.warn("No chest loot tables found — OneBlock treasure chests stay off this session.")
            return
        }
        logger.info(
            "OneBlock treasure chests: {} loot tables, {}% of breaks.",
            pool.size, chance * 100.0,
        )
        if (moddedSkipped > 0) {
            logger.info(
                "{} modded chest loot tables were skipped — set chests.include_modded to true in " +
                    "loottable.json5 to use them as well.",
                moddedSkipped,
            )
        }
    }

    /**
     * Rolls for a chest. Returns the loot table to use, or null for a normal block.
     *
     * [bonus] is the island's own `chest_chance` unlock, added to the configured base and
     * clamped with it. Note that the base can be 0 while the bonus is not: an admin who turns
     * chests off with `chance: 0.0` still leaves them buyable, which is the intended reading —
     * `enabled: false` is the switch that turns the feature off entirely.
     */
    @JvmOverloads
    fun roll(random: RandomSource, bonus: Double = 0.0): ResourceKey<LootTable>? {
        if (!enabled || tables.isEmpty()) return null
        val effective = (chance + bonus).coerceIn(0.0, 1.0)
        if (effective <= 0.0) return null
        if (random.nextDouble() >= effective) return null
        return tables[random.nextInt(tables.size)]
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
}
