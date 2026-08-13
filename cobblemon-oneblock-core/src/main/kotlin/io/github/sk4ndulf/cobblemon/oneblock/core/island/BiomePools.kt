package io.github.sk4ndulf.cobblemon.oneblock.core.island

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.compat.PolymerCompat
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechEffect
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechService
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * The block sets behind the OneBlock biome ladders.
 *
 * This is the fix for the mod's oldest defect: in `all_blocks` mode the anchor draws from
 * every registered block, uniformly, so break one and break fifty thousand are statistically
 * identical and nothing that comes out means anything. Here the pool is *the island's own*,
 * assembled from the biome tiers its tech tree has unlocked, so what comes out of the block is
 * feedback on a decision the player made.
 *
 * An island with nothing unlocked gets the deliberately poor [base] set. Each unlocked tier
 * adds its blocks on top — the base never disappears, it just becomes a smaller share.
 *
 * Content lives in `oneblock_biomes.json5`, copied from a bundled default on first start, so
 * retuning the ladders is an admin edit and a `/ob reload`, not a rebuild.
 */
object BiomePools {

    const val FILE_NAME = "oneblock_biomes.json5"
    private const val DEFAULT_RESOURCE = "/data/cobblemon_oneblock/default_biomes.json5"

    private data class Entry(val state: BlockState, val weight: Int, val biome: String)

    /** Raw config: biome → tier → (block id, weight). Resolved against the registry later. */
    private var rawBase: List<Pair<String, Int>> = emptyList()
    private var rawTiers: Map<String, Map<Int, List<Pair<String, Int>>>> = emptyMap()

    private var baseEntries: List<Entry> = emptyList()
    private var tierEntries: Map<String, Map<Int, List<Entry>>> = emptyMap()

    /**
     * Which biome a block belongs to, for the yield bonus.
     *
     * A block listed in two biomes keeps the first one that claimed it. That is a real
     * ambiguity — stone is plausibly Cave and Tundra — but the alternative is tracking the
     * origin of every placed anchor block, and getting a double-drop attributed to the wrong
     * ladder is a far smaller problem than that machinery.
     */
    private val biomeOfBlock = HashMap<Block, String>()

    /** One island's assembled pool, remembered so it is not rebuilt on every single break. */
    private class Pool(val signature: Map<String, Int>, val entries: List<Entry>, val totalWeight: Long)

    private val cache = HashMap<Long, Pool>()

    private fun file(configDir: Path): Path = configDir.resolve(FILE_NAME)

    // --- loading -----------------------------------------------------------------------------

    /** Reads the config file. Does not touch registries — [build] does that. */
    fun load(configDir: Path, logger: Logger) {
        val path = file(configDir)
        if (!Files.exists(path)) writeDefault(configDir, path, logger)
        try {
            val json = Jankson.builder().build().load(path.toFile())
            rawBase = readEntries(json.get("base") as? JsonArray, logger, "base")
            rawTiers = readBiomes(json.get("biomes") as? JsonObject, logger)
        } catch (e: Exception) {
            logger.error("Could not read {} ({}) — keeping the previous biome sets.", FILE_NAME, e.message)
        }
    }

    private fun readBiomes(json: JsonObject?, logger: Logger): Map<String, Map<Int, List<Pair<String, Int>>>> {
        if (json == null) {
            logger.error("{} has no \"biomes\" object — no biome ladders were loaded.", FILE_NAME)
            return emptyMap()
        }
        val result = HashMap<String, Map<Int, List<Pair<String, Int>>>>()
        for (biome in json.keys) {
            val tiers = json.get(biome) as? JsonObject ?: continue
            val byTier = HashMap<Int, List<Pair<String, Int>>>()
            for (key in tiers.keys) {
                val tier = key.toIntOrNull()
                if (tier == null || tier < 1) {
                    logger.warn("{}: biome '{}' has a tier '{}' that is not a positive number.", FILE_NAME, biome, key)
                    continue
                }
                byTier[tier] = readEntries(tiers.get(key) as? JsonArray, logger, "$biome tier $tier")
            }
            result[biome] = byTier
        }
        return result
    }

    private fun readEntries(array: JsonArray?, logger: Logger, where: String): List<Pair<String, Int>> {
        if (array == null) return emptyList()
        return array.mapNotNull { element ->
            when (element) {
                // Shorthand: a bare string means weight 1.
                is JsonPrimitive -> element.asString()?.let { it to 1 }
                is JsonObject -> {
                    val block = (element.get("block") as? JsonPrimitive)?.asString()
                    val weight = (element.get("weight") as? JsonPrimitive)?.asInt(1) ?: 1
                    when {
                        block == null -> {
                            logger.warn("{}: an entry in {} has no \"block\".", FILE_NAME, where); null
                        }
                        weight <= 0 -> {
                            logger.warn("{}: '{}' in {} has weight {} — skipped.", FILE_NAME, block, where, weight); null
                        }
                        else -> block to weight
                    }
                }
                else -> null
            }
        }
    }

    /**
     * Resolves the configured block ids against the registry. Needs a loaded registry, so it
     * runs at server start and after `/ob reload`, alongside the main loot pool build.
     */
    fun build(server: MinecraftServer, logger: Logger) {
        cache.clear()
        biomeOfBlock.clear()

        val level = OneBlockDimension.level(server)
        if (level == null) {
            logger.warn("{}: OneBlock world missing during build — the support check was skipped.", FILE_NAME)
        }

        baseEntries = resolve(rawBase, BASE_BIOME, level, logger)
        tierEntries = rawTiers.mapValues { (biome, tiers) ->
            tiers.mapValues { (_, entries) -> resolve(entries, biome, level, logger) }
        }

        if (baseEntries.isEmpty()) {
            logger.error(
                "{}: the base set resolved to nothing — falling back to grass block, or a new island " +
                    "would have an empty OneBlock.", FILE_NAME,
            )
            baseEntries = listOf(Entry(Blocks.GRASS_BLOCK.defaultBlockState(), 1, BASE_BIOME))
        }

        val tierCount = tierEntries.values.sumOf { it.size }
        val blockCount = tierEntries.values.sumOf { tiers -> tiers.values.sumOf { it.size } }
        logger.info(
            "OneBlock biome sets: {} base blocks, {} biomes, {} tiers, {} blocks across them.",
            baseEntries.size, tierEntries.size, tierCount, blockCount,
        )
    }

    /**
     * Turns configured ids into placeable entries, dropping anything that would misbehave as
     * a lone floating block.
     *
     * The two filters are not paranoia, they are the two ways a hand-written list bricks an
     * island: a block needing support (a sapling, a torch, a rail) pops off the instant it
     * becomes the anchor and leaves a hole over the void, and an unbreakable one can never be
     * mined, so the island stops forever on that block.
     */
    private fun resolve(
        entries: List<Pair<String, Int>>,
        biome: String,
        level: ServerLevel?,
        logger: Logger,
    ): List<Entry> = entries.mapNotNull { (blockId, weight) ->
        val id = ResourceLocation.tryParse(blockId)
        val block = id?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
        if (block == null) {
            // Expected on a server without some mod installed; a warning, not an error.
            logger.warn("{}: unknown block '{}' in '{}' — skipped.", FILE_NAME, blockId, biome)
            return@mapNotNull null
        }
        if (PolymerCompat.isPolymerContent(block)) {
            logger.warn("{}: '{}' is Polymer content and was skipped.", FILE_NAME, blockId)
            return@mapNotNull null
        }
        if (block.defaultDestroyTime() < 0f) {
            logger.warn(
                "{}: '{}' in '{}' is unbreakable and would stop the island on that block — skipped.",
                FILE_NAME, blockId, biome,
            )
            return@mapNotNull null
        }
        val state = safeState(block.defaultBlockState())
        if (level != null && !state.canSurvive(level, SUPPORT_PROBE)) {
            logger.warn(
                "{}: '{}' in '{}' needs a block under it and would fall off the anchor — skipped.",
                FILE_NAME, blockId, biome,
            )
            return@mapNotNull null
        }
        biomeOfBlock.putIfAbsent(block, biome)
        Entry(state, weight, biome)
    }

    /** A guaranteed-air floating position, for the same support probe the all-blocks pool uses. */
    private val SUPPORT_PROBE = BlockPos(8, 200, 8)

    /** Leaves must be persistent or they decay the moment they sit alone over the void. */
    private fun safeState(state: BlockState): BlockState =
        if (state.hasProperty(LeavesBlock.PERSISTENT)) state.setValue(LeavesBlock.PERSISTENT, true) else state

    // --- per-island pools --------------------------------------------------------------------

    /**
     * The biome tiers this island has unlocked, as biome → highest tier.
     *
     * Read from the tech effects rather than from node ids, so an admin who renames a node or
     * splits a ladder across two nodes does not break anything.
     */
    fun unlockedTiers(islandId: Long): Map<String, Int> {
        val state = TechService.stateOf(islandId)
        val result = HashMap<String, Int>()
        for (effect in state.activeEffects(TechService.tree)) {
            if (effect !is TechEffect.OneBlockBiome) continue
            result.merge(effect.biome, effect.tier, ::maxOf)
        }
        return result
    }

    /**
     * Picks the next block for this island.
     *
     * The pool is cached against the island's unlock signature rather than invalidated by a
     * hook: buying a node changes the signature, so the rebuild happens by itself and there is
     * no way to forget to trigger it.
     */
    fun next(islandId: Long, random: RandomSource): BlockState {
        val pool = poolFor(islandId)
        if (pool.entries.isEmpty() || pool.totalWeight <= 0) return Blocks.GRASS_BLOCK.defaultBlockState()
        var roll = random.nextLong().mod(pool.totalWeight)
        for (entry in pool.entries) {
            roll -= entry.weight
            if (roll < 0) return entry.state
        }
        return pool.entries.last().state
    }

    private fun poolFor(islandId: Long): Pool {
        val signature = unlockedTiers(islandId)
        cache[islandId]?.let { if (it.signature == signature) return it }

        val entries = ArrayList<Entry>(baseEntries)
        for ((biome, highestTier) in signature) {
            val tiers = tierEntries[biome]
            if (tiers == null) {
                OneBlockCore.LOGGER.warn(
                    "Island {} unlocked biome '{}', which has no block sets in {}. Nothing was added.",
                    islandId, biome, FILE_NAME,
                )
                continue
            }
            for (tier in 1..highestTier) {
                entries += tiers[tier].orEmpty()
            }
        }
        val pool = Pool(signature, entries, entries.sumOf { it.weight.toLong() })
        cache[islandId] = pool
        return pool
    }

    /** Drops a cached pool. Only needed when an island goes away; unlocks handle themselves. */
    fun forget(islandId: Long) {
        cache.remove(islandId)
    }

    // --- yield -------------------------------------------------------------------------------

    /** The biome a block belongs to, or null when it is not part of any ladder. */
    fun biomeOf(block: Block): String? = biomeOfBlock[block]

    /**
     * Chance that breaking [block] on this island drops twice, from `oneblock_yield` nodes.
     *
     * Returns 0 for anything not in a biome set — a treasure chest, or a block the player
     * placed there themselves — so the bonus can never apply to something it was not bought for.
     */
    fun yieldChance(islandId: Long, block: Block): Double {
        val biome = biomeOfBlock[block] ?: return 0.0
        if (biome == BASE_BIOME) return 0.0
        var best = 0.0
        for (effect in TechService.stateOf(islandId).activeEffects(TechService.tree)) {
            if (effect is TechEffect.OneBlockYield && effect.biome == biome) {
                best = maxOf(best, effect.chance)
            }
        }
        return best
    }

    // --- default file ------------------------------------------------------------------------

    private fun writeDefault(configDir: Path, path: Path, logger: Logger) {
        val bundled = javaClass.getResourceAsStream(DEFAULT_RESOURCE)
        if (bundled == null) {
            logger.error("Bundled {} is missing from the jar — no biome sets written.", DEFAULT_RESOURCE)
            return
        }
        try {
            Files.createDirectories(configDir)
            bundled.use { Files.copy(it, path) }
            logger.info("Wrote the default OneBlock biome sets to {}.", path)
        } catch (e: Exception) {
            logger.error("Could not write the default {}: {}", FILE_NAME, e.message)
        }
    }

    /** Marker biome for the starting blocks, so they can never earn a yield bonus. */
    private const val BASE_BIOME = "base"
}
