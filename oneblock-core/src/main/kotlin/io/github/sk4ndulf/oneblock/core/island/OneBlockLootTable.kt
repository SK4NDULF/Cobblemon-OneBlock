package io.github.sk4ndulf.oneblock.core.island

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Weighted random pool for OneBlock regeneration, loaded from
 * `config/oneblock/loottable.json5` (written with defaults on first start,
 * freely editable by admins, reloaded by `/ob reload`).
 *
 * Custom per-phase loot tiers and addon-provided loot providers are a later
 * extension point (PROJECT_PLAN.md phase 11).
 */
class OneBlockLootTable(private val configDir: Path, private val logger: Logger) {

    private data class Entry(val state: BlockState, val weight: Int)

    private var entries: List<Entry> = emptyList()
    private var totalWeight: Long = 0

    private val file: Path get() = configDir.resolve("loottable.json5")

    /** Sensible starter pool: overworld basics with rarer ores. */
    private val defaults: List<Pair<String, Int>> = listOf(
        "minecraft:grass_block" to 220,
        "minecraft:dirt" to 200,
        "minecraft:stone" to 200,
        "minecraft:cobblestone" to 160,
        "minecraft:oak_log" to 120,
        "minecraft:birch_log" to 60,
        "minecraft:oak_leaves" to 80,
        "minecraft:sand" to 70,
        "minecraft:gravel" to 70,
        "minecraft:clay" to 40,
        "minecraft:coal_ore" to 50,
        "minecraft:copper_ore" to 35,
        "minecraft:iron_ore" to 30,
        "minecraft:gold_ore" to 12,
        "minecraft:redstone_ore" to 12,
        "minecraft:lapis_ore" to 10,
        "minecraft:diamond_ore" to 4,
        "minecraft:emerald_ore" to 2,
        "minecraft:mossy_cobblestone" to 25,
        "minecraft:pumpkin" to 15,
        "minecraft:melon" to 15,
        "minecraft:bookshelf" to 8,
        "minecraft:hay_block" to 20,
        "minecraft:snow_block" to 20,
        "minecraft:ice" to 15,
        "minecraft:podzol" to 25,
        "minecraft:mud" to 25,
    )

    fun load() {
        if (!Files.exists(file)) {
            writeDefaults()
        }
        val loaded = ArrayList<Entry>()
        try {
            val json = Jankson.builder().build().load(file.toFile())
            val array = json.get("entries") as? JsonArray
            if (array == null) {
                logger.error("loottable.json5 has no 'entries' array — using built-in defaults.")
            } else {
                for (element in array) {
                    val obj = element as? JsonObject ?: continue
                    val blockId = (obj.get("block") as? JsonPrimitive)?.asString() ?: continue
                    val weight = (obj.get("weight") as? JsonPrimitive)?.asInt(0) ?: 0
                    if (weight <= 0) continue
                    val id = ResourceLocation.tryParse(blockId)
                    val block = id?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
                    if (block == null || (block == Blocks.AIR && blockId != "minecraft:air")) {
                        logger.warn("loottable.json5: unknown block '{}' skipped.", blockId)
                        continue
                    }
                    loaded.add(Entry(block.defaultBlockState(), weight))
                }
            }
        } catch (e: Exception) {
            logger.error("Could not read loottable.json5 ({}) — using built-in defaults.", e.message)
        }

        entries = loaded.ifEmpty {
            defaults.mapNotNull { (blockId, weight) ->
                BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId)).orElse(null)
                    ?.let { Entry(it.defaultBlockState(), weight) }
            }
        }
        totalWeight = entries.sumOf { it.weight.toLong() }
        logger.info("OneBlock loot table loaded: {} entries, total weight {}.", entries.size, totalWeight)
    }

    fun next(random: RandomSource): BlockState {
        if (entries.isEmpty() || totalWeight <= 0) return Blocks.GRASS_BLOCK.defaultBlockState()
        var roll = random.nextLong().mod(totalWeight)
        for (entry in entries) {
            roll -= entry.weight
            if (roll < 0) return entry.state
        }
        return entries.last().state
    }

    private fun writeDefaults() {
        val root = JsonObject()
        val array = JsonArray()
        for ((blockId, weight) in defaults) {
            val obj = JsonObject()
            obj.put("block", JsonPrimitive(blockId), null)
            obj.put("weight", JsonPrimitive(weight.toLong()), null)
            array.add(obj)
        }
        root.put(
            "entries", array,
            "Weighted random pool for the OneBlock. 'weight' is relative — higher = more common. " +
                "Unknown block ids are skipped with a warning. Reload with /ob reload.",
        )
        try {
            Files.createDirectories(configDir)
            Files.writeString(file, root.toJson(true, true))
        } catch (e: Exception) {
            logger.error("Could not write default loottable.json5: {}", e.message)
        }
    }
}
