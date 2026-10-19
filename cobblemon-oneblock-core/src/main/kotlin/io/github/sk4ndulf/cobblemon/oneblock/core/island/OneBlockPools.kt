package io.github.sk4ndulf.cobblemon.oneblock.core.island

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import io.github.sk4ndulf.cobblemon.oneblock.core.compat.PolymerCompat
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * What the OneBlock produces, one pool per dimension.
 *
 * The base concept of the mod: an island has an anchor in each of its three dimensions, and
 * each one draws only from its own dimension's set. The Overworld anchor gives Overworld
 * blocks, the Nether anchor gives Nether blocks, the End anchor gives End blocks. Where you
 * are decides what you get, and nothing else does.
 *
 * Content lives in `oneblock.json5`, copied from a bundled default on first start, so retuning
 * a pool is an admin edit and a `/ob reload` rather than a rebuild.
 */
object OneBlockPools {

    const val FILE_NAME = "oneblock.json5"
    private const val DEFAULT_RESOURCE = "/data/cobblemon_oneblock/default_oneblock.json5"

    private data class Entry(val state: BlockState, val weight: Int)
    private class Pool(val entries: List<Entry>, val totalWeight: Long)

    /** Config key per dimension. Deliberately short — an admin types these. */
    val POOL_NAMES: Map<ResourceKey<Level>, String> = mapOf(
        OneBlockDimension.OVERWORLD_KEY to "overworld",
        OneBlockDimension.NETHER_KEY to "nether",
        OneBlockDimension.END_KEY to "end",
    )

    private var raw: Map<String, List<Pair<String, Int>>> = emptyMap()
    private var pools: Map<String, Pool> = emptyMap()

    private fun file(configDir: Path): Path = configDir.resolve(FILE_NAME)

    // --- loading -----------------------------------------------------------------------------

    /** Reads the config file. Does not touch registries — [build] does that. */
    fun load(configDir: Path, logger: Logger) {
        val path = file(configDir)
        if (!Files.exists(path)) writeDefault(configDir, path, logger)
        try {
            val json = Jankson.builder().build().load(path.toFile())
            raw = POOL_NAMES.values.associateWith { name ->
                readEntries(json.get(name) as? JsonArray, name, logger)
            }
            ChestLoot.configure(readChests(json.get("chests") as? JsonObject, logger))
        } catch (e: Exception) {
            logger.error("Could not read {} ({}) — keeping the previous pools.", FILE_NAME, e.message)
        }
    }

    private fun readEntries(array: JsonArray?, where: String, logger: Logger): List<Pair<String, Int>> {
        if (array == null) {
            logger.warn("{}: no \"{}\" list — that dimension's OneBlock will fall back to stone.", FILE_NAME, where)
            return emptyList()
        }
        return array.mapNotNull { element ->
            when (element) {
                // Shorthand: a bare string means weight 1.
                is JsonPrimitive -> element.asString()?.let { it to 1 }
                is JsonObject -> {
                    val block = (element.get("block") as? JsonPrimitive)?.asString()
                    val weight = (element.get("weight") as? JsonPrimitive)?.asInt(1) ?: 1
                    when {
                        block == null -> {
                            logger.warn("{}: an entry in \"{}\" has no \"block\".", FILE_NAME, where); null
                        }
                        weight <= 0 -> {
                            logger.warn("{}: '{}' in \"{}\" has weight {} — skipped.", FILE_NAME, block, where, weight)
                            null
                        }
                        else -> block to weight
                    }
                }
                else -> null
            }
        }
    }

    /**
     * Reads the `chests` section. A file written before a setting existed simply lacks it, and
     * the default applies — that is not an error, so it is not reported as one.
     */
    private fun readChests(json: JsonObject?, logger: Logger): ChestLoot.Config {
        if (json == null) {
            logger.info("{}: no \"chests\" section — using the defaults.", FILE_NAME)
            return ChestLoot.Config()
        }
        fun bool(key: String, fallback: Boolean) =
            (json.get(key) as? JsonPrimitive)?.asBoolean(fallback) ?: fallback
        fun strings(key: String) = (json.get(key) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.asString() } ?: emptyList()

        return ChestLoot.Config(
            enabled = bool("enabled", ChestLoot.DEFAULT_ENABLED),
            chance = (json.get("chance") as? JsonPrimitive)?.asDouble(ChestLoot.DEFAULT_CHANCE)
                ?: ChestLoot.DEFAULT_CHANCE,
            includeModded = bool("include_modded", ChestLoot.DEFAULT_INCLUDE_MODDED),
            blacklist = strings("blacklist").toSet(),
            extra = mapOf(
                ChestLoot.POOL_OVERWORLD to strings("overworld_extra"),
                ChestLoot.POOL_NETHER to strings("nether_extra"),
                ChestLoot.POOL_END to strings("end_extra"),
            ),
        )
    }

    /**
     * Resolves the configured ids against the registry. Needs a loaded registry, so it runs at
     * server start and after `/ob reload`.
     */
    fun build(server: MinecraftServer, logger: Logger) {
        // Any of our levels works for the support probe — it only needs a loaded, empty world.
        val probeLevel = OneBlockDimension.overworld(server)
        if (probeLevel == null) {
            logger.warn("{}: OneBlock world missing during build — the support check was skipped.", FILE_NAME)
        }
        pools = raw.mapValues { (name, entries) ->
            val resolved = resolve(entries, name, probeLevel, logger)
            Pool(resolved, resolved.sumOf { it.weight.toLong() })
        }

        for ((name, pool) in pools) {
            if (pool.entries.isEmpty()) {
                logger.error(
                    "{}: the \"{}\" pool resolved to nothing — that dimension's OneBlock will produce stone.",
                    FILE_NAME, name,
                )
            }
        }
        logger.info(
            "OneBlock pools: {}.",
            pools.entries.sortedBy { it.key }.joinToString(", ") { "${it.key} ${it.value.entries.size} blocks" },
        )
        ChestLoot.buildPool(server, logger)
    }

    /**
     * Turns configured ids into placeable entries, dropping anything that would misbehave as a
     * lone floating block.
     *
     * The two filters are not paranoia, they are the two ways a hand-written list bricks an
     * island: a block needing support pops off the instant it becomes the anchor and leaves a
     * hole over the void, and an unbreakable one can never be mined, so the island stops
     * forever on that block.
     */
    private fun resolve(
        entries: List<Pair<String, Int>>,
        where: String,
        level: ServerLevel?,
        logger: Logger,
    ): List<Entry> = entries.mapNotNull { (blockId, weight) ->
        val id = ResourceLocation.tryParse(blockId)
        val block = id?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
        if (block == null) {
            // Expected on a server without some mod installed; a warning, not an error.
            logger.warn("{}: unknown block '{}' in \"{}\" — skipped.", FILE_NAME, blockId, where)
            return@mapNotNull null
        }
        if (PolymerCompat.isPolymerContent(block)) {
            logger.warn("{}: '{}' is Polymer content and was skipped.", FILE_NAME, blockId)
            return@mapNotNull null
        }
        if (block.defaultDestroyTime() < 0f) {
            logger.warn(
                "{}: '{}' in \"{}\" is unbreakable and would stop the island on that block — skipped.",
                FILE_NAME, blockId, where,
            )
            return@mapNotNull null
        }
        val state = safeState(block.defaultBlockState())
        if (level != null && !state.canSurvive(level, SUPPORT_PROBE)) {
            logger.warn(
                "{}: '{}' in \"{}\" needs a block under it and would fall off the anchor — skipped.",
                FILE_NAME, blockId, where,
            )
            return@mapNotNull null
        }
        Entry(state, weight)
    }

    /** Leaves must be persistent or they decay the moment they sit alone over the void. */
    private fun safeState(state: BlockState): BlockState =
        if (state.hasProperty(LeavesBlock.PERSISTENT)) state.setValue(LeavesBlock.PERSISTENT, true) else state

    /** A guaranteed-air floating position for the support probe. */
    private val SUPPORT_PROBE = BlockPos(8, 200, 8)

    // --- picking -----------------------------------------------------------------------------

    /**
     * The next block for an anchor in this dimension.
     *
     * Stone rather than nothing when a pool is empty: an anchor that cannot be replaced leaves
     * a hole over the void, which is worse than a dull block.
     */
    fun next(level: Level, random: RandomSource): BlockState = next(poolNameFor(level.dimension()), random)

    /**
     * Which config list a dimension draws from, or null for a dimension that is not ours.
     *
     * Separate from [next] so the mapping can be tested without a world: it is the single
     * place where "where you are" becomes "what you get", and getting it wrong would give an
     * island Nether blocks in the Overworld with nothing else looking broken.
     */
    fun poolNameFor(dimension: ResourceKey<Level>): String? = POOL_NAMES[dimension]

    private fun next(poolName: String?, random: RandomSource): BlockState {
        val pool = poolName?.let { pools[it] }
        if (pool == null || pool.entries.isEmpty() || pool.totalWeight <= 0) {
            return Blocks.STONE.defaultBlockState()
        }
        var roll = random.nextLong().mod(pool.totalWeight)
        for (entry in pool.entries) {
            roll -= entry.weight
            if (roll < 0) return entry.state
        }
        return pool.entries.last().state
    }

    // --- default file ------------------------------------------------------------------------

    private fun writeDefault(configDir: Path, path: Path, logger: Logger) {
        val bundled = javaClass.getResourceAsStream(DEFAULT_RESOURCE)
        if (bundled == null) {
            logger.error("Bundled {} is missing from the jar — no pools written.", DEFAULT_RESOURCE)
            return
        }
        try {
            Files.createDirectories(configDir)
            bundled.use { Files.copy(it, path) }
            logger.info("Wrote the default OneBlock pools to {}.", path)
        } catch (e: Exception) {
            logger.error("Could not write the default {}: {}", FILE_NAME, e.message)
        }
    }
}
