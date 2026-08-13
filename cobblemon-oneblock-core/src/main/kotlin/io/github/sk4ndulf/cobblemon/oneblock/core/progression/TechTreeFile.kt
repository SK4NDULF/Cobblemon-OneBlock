package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads `techtree.json5` and turns it into a validated [TechTree].
 *
 * The default file is a bundled resource rather than a JsonObject assembled in code, which is
 * how `loottable.json5` does it. The reason for the difference: the loot table has five
 * settings and the tree has dozens of nodes with nested effects, and building that in code
 * would be several hundred lines that are harder to read than the JSON they produce — and the
 * resource keeps its comments for free. It is copied to the config directory on first start
 * and never touched again, so an admin's edits always win.
 *
 * Every parse failure is logged and skipped. A file full of mistakes yields a small tree and
 * a loud log, never a failed boot.
 */
class TechTreeFile(private val configDir: Path, private val logger: Logger) {

    private val file: Path get() = configDir.resolve(FILE_NAME)

    var tree: TechTree = TechTree.empty()
        private set

    /** Reads the file, writing the bundled default first if it is missing. */
    fun load() {
        if (!Files.exists(file)) {
            writeDefault()
        }
        val parsed = try {
            parse(Jankson.builder().build().load(file.toFile()))
        } catch (e: Exception) {
            logger.error("Could not read {} ({}) — keeping the previously loaded tree.", FILE_NAME, e.message)
            return
        }
        val built = TechTree.build(parsed)
        for (problem in built.problems) {
            logger.warn("techtree.json5: {}", problem)
        }
        tree = built
        logger.info(
            "Tech tree loaded: {} nodes across {} categories, {} points to complete everything.",
            built.nodes.size, built.byCategory.size, built.totalCost,
        )
        built.reportUnconsumedEffects(logger)
    }

    private fun parse(root: JsonObject): List<TechNode> {
        val array = root.get("nodes") as? JsonArray
        if (array == null) {
            logger.error("techtree.json5 has no \"nodes\" array — no tech nodes were loaded.")
            return emptyList()
        }
        val seen = HashSet<String>()
        val nodes = ArrayList<TechNode>()
        for (element in array) {
            val node = parseNode(element as? JsonObject ?: continue) ?: continue
            if (!seen.add(node.id)) {
                logger.warn("techtree.json5: duplicate node id '{}' — only the first is used.", node.id)
                continue
            }
            nodes += node
        }
        return nodes
    }

    private fun parseNode(json: JsonObject): TechNode? {
        val id = json.string("id")
        if (id.isNullOrBlank()) {
            logger.warn("techtree.json5: a node has no \"id\" and was skipped.")
            return null
        }
        val category = TechCategory.parse(json.string("category") ?: "")
        if (category == null) {
            logger.warn(
                "techtree.json5: node '{}' has an unknown category '{}'. Known: {}.",
                id, json.string("category"), TechCategory.entries.joinToString(", ") { it.key },
            )
            return null
        }

        val costs = (json.get("costs") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.asLong(-1L) }
            ?.filter { it >= 0 }
            ?: emptyList()
        if (costs.isEmpty()) {
            logger.warn("techtree.json5: node '{}' has no valid \"costs\" and was skipped.", id)
            return null
        }
        // max_level is derived from the cost list rather than trusted separately: two sources
        // of truth for the same number is how a level ends up unreachable or free.
        val declaredMax = (json.get("max_level") as? JsonPrimitive)?.asInt(costs.size) ?: costs.size
        if (declaredMax != costs.size) {
            logger.warn(
                "techtree.json5: node '{}' declares max_level {} but lists {} cost(s) — using {}.",
                id, declaredMax, costs.size, costs.size,
            )
        }

        val requires = (json.get("requires") as? JsonArray)
            ?.mapNotNull { element ->
                val raw = (element as? JsonPrimitive)?.asString() ?: return@mapNotNull null
                TechRequirement.parse(raw).also {
                    if (it == null) logger.warn("techtree.json5: node '{}' has an unparseable requirement '{}'.", id, raw)
                }
            }
            ?: emptyList()

        return TechNode(
            id = id,
            category = category,
            maxLevel = costs.size,
            costs = costs,
            requires = requires,
            effects = parseEffects(id, json.get("effects") as? JsonObject, costs.size),
            displayName = json.string("name") ?: id,
            description = json.string("description") ?: "",
            icon = json.string("icon") ?: DEFAULT_ICON,
        )
    }

    private fun parseEffects(nodeId: String, json: JsonObject?, maxLevel: Int): Map<Int, List<TechEffect>> {
        if (json == null) return emptyMap()
        val result = HashMap<Int, List<TechEffect>>()
        for (key in json.keys) {
            val level = key.toIntOrNull()
            if (level == null || level < 1 || level > maxLevel) {
                logger.warn(
                    "techtree.json5: node '{}' has effects for level '{}', which is not between 1 and {}.",
                    nodeId, key, maxLevel,
                )
                continue
            }
            val array = json.get(key) as? JsonArray ?: continue
            val effects = array.mapNotNull { parseEffect(nodeId, level, it as? JsonObject ?: return@mapNotNull null) }
            if (effects.isNotEmpty()) result[level] = effects
        }
        return result
    }

    private fun parseEffect(nodeId: String, level: Int, json: JsonObject): TechEffect? {
        val type = json.string("type")
        fun bad(reason: String): TechEffect? {
            logger.warn("techtree.json5: node '{}' level {} has a bad '{}' effect — {}.", nodeId, level, type, reason)
            return null
        }
        return when (type) {
            TechEffect.TYPE_ONEBLOCK_BIOME -> {
                val biome = json.string("biome") ?: return bad("no \"biome\"")
                val tier = json.int("tier", 0)
                if (tier < 1) bad("\"tier\" must be 1 or higher") else TechEffect.OneBlockBiome(biome, tier)
            }
            TechEffect.TYPE_ONEBLOCK_YIELD -> {
                val biome = json.string("biome") ?: return bad("no \"biome\"")
                TechEffect.OneBlockYield(biome, json.double("chance", 0.0).coerceIn(0.0, 1.0))
            }
            TechEffect.TYPE_CHEST_CHANCE ->
                TechEffect.ChestChance(json.double("added", 0.0).coerceIn(0.0, 1.0))
            TechEffect.TYPE_BORDER_SIZE -> {
                val size = json.int("size", 0)
                if (size < 16) bad("\"size\" must be at least 16") else TechEffect.BorderSize(size)
            }
            TechEffect.TYPE_ISLAND_INT -> {
                val key = json.string("key") ?: return bad("no \"key\"")
                if (key !in TechEffect.ISLAND_INT_KEYS) {
                    return bad("unknown key, expected one of ${TechEffect.ISLAND_INT_KEYS.joinToString(", ")}")
                }
                TechEffect.IslandInt(key, json.int("value", 0))
            }
            TechEffect.TYPE_PLAYER_EFFECT -> {
                val key = json.string("key") ?: return bad("no \"key\"")
                if (key !in TechEffect.PLAYER_EFFECT_KEYS) {
                    return bad("unknown key, expected one of ${TechEffect.PLAYER_EFFECT_KEYS.joinToString(", ")}")
                }
                TechEffect.PlayerEffect(key, json.double("magnitude", 0.0))
            }
            TechEffect.TYPE_POKEMON_WORK -> {
                val pokemonType = json.string("pokemon_type") ?: return bad("no \"pokemon_type\"")
                val axis = json.string("axis") ?: return bad("no \"axis\"")
                if (axis !in TechEffect.WORK_AXES) {
                    return bad("unknown axis, expected one of ${TechEffect.WORK_AXES.joinToString(", ")}")
                }
                TechEffect.PokemonWork(pokemonType.lowercase(), axis, json.double("magnitude", 0.0))
            }
            TechEffect.TYPE_BOOST_UNLOCK -> {
                val boost = json.string("boost") ?: return bad("no \"boost\"")
                TechEffect.BoostUnlock(
                    boost,
                    json.double("strength", 1.0),
                    json.int("duration_minutes", 10).coerceAtLeast(1),
                )
            }
            TechEffect.TYPE_UNLOCK_FLAG -> {
                val flag = json.string("flag") ?: return bad("no \"flag\"")
                TechEffect.UnlockFlag(flag)
            }
            else -> bad("unknown effect type, expected one of ${TechEffect.ALL_TYPES.sorted().joinToString(", ")}")
        }
    }

    private fun writeDefault() {
        val bundled = javaClass.getResourceAsStream(DEFAULT_RESOURCE)
        if (bundled == null) {
            logger.error("Bundled default tech tree {} is missing from the jar — no tree file written.", DEFAULT_RESOURCE)
            return
        }
        try {
            Files.createDirectories(configDir)
            bundled.use { input -> Files.copy(input, file) }
            logger.info("Wrote the default tech tree to {}.", file)
        } catch (e: Exception) {
            logger.error("Could not write the default {}: {}", FILE_NAME, e.message)
        }
    }

    // --- small Jankson helpers; the raw casts are noisy enough to be worth hiding ----------

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.asString()
    private fun JsonObject.int(key: String, fallback: Int): Int = (get(key) as? JsonPrimitive)?.asInt(fallback) ?: fallback
    private fun JsonObject.double(key: String, fallback: Double): Double =
        (get(key) as? JsonPrimitive)?.asDouble(fallback) ?: fallback

    companion object {
        const val FILE_NAME = "techtree.json5"
        private const val DEFAULT_RESOURCE = "/data/cobblemon_oneblock/default_techtree.json5"
        private const val DEFAULT_ICON = "minecraft:paper"
    }
}
