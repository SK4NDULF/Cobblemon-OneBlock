package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where tech points come from: `points.json5`.
 *
 * Two sources are configured here — NPC trainers and advancements. Special bosses will be the
 * third, and they carry their own values, so they are not in this file.
 *
 * NPC trainers are **not** listed by id. An NPC carries its own `trainer_id` and `points` as
 * Cobblemon config variables, set in game with the NPC edit command, so adding a gym is
 * placing an entity and typing two values — no config edit, no restart. This file only holds
 * the variable names and the fallback value.
 */
class PointSourceConfig(private val configDir: Path, private val logger: Logger) {

    /** MoLang config variable on an NPC holding its claim id. Blank or missing = grants nothing. */
    var npcIdVariable: String = DEFAULT_ID_VARIABLE
        private set

    /** MoLang config variable holding the point value. Missing = [npcDefaultPoints]. */
    var npcPointsVariable: String = DEFAULT_POINTS_VARIABLE
        private set

    var npcDefaultPoints: Long = DEFAULT_NPC_POINTS
        private set

    /** Advancement id to points. Anything not listed grants nothing. */
    var advancements: Map<String, Long> = emptyMap()
        private set

    private val file: Path get() = configDir.resolve(FILE_NAME)

    fun load() {
        if (!Files.exists(file)) writeDefault()
        try {
            val root = Jankson.builder().build().load(file.toFile())
            val npc = root.get("npc") as? JsonObject
            npcIdVariable = npc?.string("id_variable") ?: DEFAULT_ID_VARIABLE
            npcPointsVariable = npc?.string("points_variable") ?: DEFAULT_POINTS_VARIABLE
            npcDefaultPoints = (npc?.get("default_points") as? JsonPrimitive)
                ?.asLong(DEFAULT_NPC_POINTS)?.coerceAtLeast(0L) ?: DEFAULT_NPC_POINTS
            advancements = readAdvancements(root.get("advancements") as? JsonObject)
        } catch (e: Exception) {
            logger.error("Could not read {} ({}) — keeping the previous point sources.", FILE_NAME, e.message)
            return
        }
        report()
    }

    private fun readAdvancements(json: JsonObject?): Map<String, Long> {
        if (json == null) return emptyMap()
        val result = LinkedHashMap<String, Long>()
        for (key in json.keys) {
            val points = (json.get(key) as? JsonPrimitive)?.asLong(0L) ?: 0L
            if (points <= 0) {
                logger.warn("{}: advancement '{}' is worth {} points — skipped.", FILE_NAME, key, points)
                continue
            }
            result[key] = points
        }
        return result
    }

    /**
     * Reports the advancement budget against the tree's cost.
     *
     * Progression is finite and one-shot per island, so the sum of everything obtainable has
     * to exceed the sum of every node cost or the last nodes are unreachable for everyone
     * (PROGRESSION_REWORK.md §3.2d). Only half of that sum is knowable here — NPC trainers are
     * entities in the world, not config — so this prints the half it knows and leaves the
     * arithmetic to the admin rather than guessing and crying wolf.
     */
    private fun report() {
        val total = advancements.values.sum()
        val treeCost = TechService.tree.totalCost
        logger.info(
            "Point sources: {} advancements worth {} points. The tech tree costs {} to complete, " +
                "so NPC trainers and bosses need to cover the remaining {}.",
            advancements.size, total, treeCost, (treeCost - total).coerceAtLeast(0),
        )
    }

    private fun writeDefault() {
        val bundled = javaClass.getResourceAsStream(DEFAULT_RESOURCE)
        if (bundled == null) {
            logger.error("Bundled {} is missing from the jar — no point sources written.", DEFAULT_RESOURCE)
            return
        }
        try {
            Files.createDirectories(configDir)
            bundled.use { Files.copy(it, file) }
            logger.info("Wrote the default point sources to {}.", file)
        } catch (e: Exception) {
            logger.error("Could not write the default {}: {}", FILE_NAME, e.message)
        }
    }

    private fun JsonObject.string(key: String): String? =
        (get(key) as? JsonPrimitive)?.asString()?.takeIf { it.isNotBlank() }

    companion object {
        const val FILE_NAME = "points.json5"
        private const val DEFAULT_RESOURCE = "/data/cobblemon_oneblock/default_points.json5"
        const val DEFAULT_ID_VARIABLE = "trainer_id"
        const val DEFAULT_POINTS_VARIABLE = "points"
        const val DEFAULT_NPC_POINTS = 1L
    }
}
