package io.github.sk4ndulf.cobblemon.oneblock.core.island

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import io.github.sk4ndulf.cobblemon.oneblock.core.compat.PolymerCompat
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.util.RandomSource
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.IceBlock
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockState
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * The OneBlock regeneration pool.
 *
 * Default mode `all_blocks`: every block registered by Minecraft AND every loaded mod
 * (Cobblemon!), uniformly weighted, filtered down to blocks that are safe as a lone
 * floating block:
 *  - no fluids, nothing unbreakable (bedrock, barrier, command block: destroy time < 0)
 *  - only blocks with an item form (skips technical blocks like piston heads, portals)
 *  - nothing replaceable (light, structure void) and no melting ice (would turn to water)
 *  - nothing that pops off without support (torches, flowers, rails, crops, ...) —
 *    checked with a real canSurvive test at a floating void position
 *  - leaves are placed persistent so they never decay
 *  - gravity blocks (sand, gravel, ...) ARE included; a mixin prevents them from
 *    falling while they sit on a OneBlock anchor
 *
 * Mode `custom` uses the weighted `entries` list from the config file instead.
 * Both modes honor the `blacklist`. Reload with /ob reload.
 */
class OneBlockLootTable(private val configDir: Path, private val logger: Logger) {

    private data class Entry(val state: BlockState, val weight: Int)

    private var mode: String = MODE_BIOMES
    private var blacklist: Set<String> = emptySet()
    private var customEntries: List<Pair<String, Int>> = emptyList()
    private var excludePolymer: Boolean = true

    private var entries: List<Entry> = emptyList()
    private var totalWeight: Long = 0

    private val file: Path get() = configDir.resolve("loottable.json5")

    companion object {
        /**
         * The default since the tech tree landed: the pool is the island's own, assembled from
         * the biome tiers it has unlocked. See [BiomePools].
         */
        const val MODE_BIOMES = "biomes"

        /**
         * Legacy. Every registered block, uniformly weighted — which is why it was replaced:
         * the output never changes and therefore never means anything. Kept because servers
         * running it should not have their world change under them on an update.
         */
        const val MODE_ALL_BLOCKS = "all_blocks"
        const val MODE_CUSTOM = "custom"

        /** Blocks that pass the generic filters but still misbehave as a lone block. */
        private val BUILTIN_BLACKLIST = setOf(
            "minecraft:scaffolding", // falls via its own tick logic, not FallingBlock
        )

        /** Example pool written into the config for admins switching to custom mode. */
        private val CUSTOM_EXAMPLE = listOf(
            "minecraft:grass_block" to 220, "minecraft:dirt" to 200, "minecraft:stone" to 200,
            "minecraft:oak_log" to 120, "minecraft:coal_ore" to 50, "minecraft:iron_ore" to 30,
            "minecraft:diamond_ore" to 4,
        )
    }

    /** Reads mode/blacklist/entries from the config file. Does not touch registries. */
    fun load() {
        if (!Files.exists(file)) {
            writeDefaults()
        }
        try {
            val json = Jankson.builder().build().load(file.toFile())
            mode = (json.get("mode") as? JsonPrimitive)?.asString() ?: MODE_ALL_BLOCKS
            blacklist = (json.get("blacklist") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.asString() }?.toSet() ?: emptySet()
            excludePolymer = (json.get("exclude_polymer") as? JsonPrimitive)?.asBoolean(true) ?: true
            customEntries = (json.get("entries") as? JsonArray)?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val block = (obj.get("block") as? JsonPrimitive)?.asString() ?: return@mapNotNull null
                val weight = (obj.get("weight") as? JsonPrimitive)?.asInt(0) ?: 0
                if (weight > 0) block to weight else null
            } ?: emptyList()
            loadChestSection(json)
        } catch (e: Exception) {
            logger.error("Could not read loottable.json5 ({}) — keeping previous settings.", e.message)
        }
    }

    /**
     * Reads the `chests` section. A file written before treasure chests existed has no such
     * section — that is not an error, the defaults apply, but say so once so the admin knows
     * the knobs exist at all.
     */
    private fun loadChestSection(json: JsonObject) {
        val chests = json.get("chests") as? JsonObject
        if (chests == null) {
            ChestLoot.configure(
                ChestLoot.DEFAULT_ENABLED,
                ChestLoot.DEFAULT_CHANCE,
                ChestLoot.DEFAULT_INCLUDE_MODDED,
                emptySet(),
                emptyList(),
            )
            logger.info(
                "loottable.json5 has no \"chests\" section — using defaults (enabled, {}% of breaks). " +
                    "Delete the file to regenerate it with the documented settings.",
                ChestLoot.DEFAULT_CHANCE * 100.0,
            )
            return
        }
        ChestLoot.configure(
            enabled = (chests.get("enabled") as? JsonPrimitive)?.asBoolean(ChestLoot.DEFAULT_ENABLED)
                ?: ChestLoot.DEFAULT_ENABLED,
            chance = (chests.get("chance") as? JsonPrimitive)?.asDouble(ChestLoot.DEFAULT_CHANCE)
                ?: ChestLoot.DEFAULT_CHANCE,
            includeModded = (chests.get("include_modded") as? JsonPrimitive)
                ?.asBoolean(ChestLoot.DEFAULT_INCLUDE_MODDED) ?: ChestLoot.DEFAULT_INCLUDE_MODDED,
            blacklist = (chests.get("blacklist") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.asString() }?.toSet() ?: emptySet(),
            extra = (chests.get("extra") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.asString() } ?: emptyList(),
        )
    }

    /**
     * Builds the actual block pool. Needs the running server for the canSurvive probe,
     * so it runs at SERVER_STARTED and after /ob reload — on the server thread.
     */
    fun buildPool(server: MinecraftServer) {
        val excluded = BUILTIN_BLACKLIST + blacklist
        // The biome sets are always built, whatever the mode: an admin who switches modes with
        // /ob reload should not also need a restart, and building them costs a registry lookup
        // per configured block.
        BiomePools.build(server, logger)
        entries = when (mode) {
            MODE_BIOMES -> emptyList() // per-island; nothing global to build
            MODE_CUSTOM -> buildCustomPool(excluded)
            else -> buildAllBlocksPool(server, excluded)
        }
        if (mode == MODE_BIOMES) {
            totalWeight = 0
            logger.info("OneBlock loot mode: biomes — each island draws from what its tech tree unlocked.")
            ChestLoot.buildPool(server, logger)
            return
        }
        totalWeight = entries.sumOf { it.weight.toLong() }
        logger.info(
            "OneBlock loot table built (mode {}): {} blocks, total weight {}{}.",
            mode, entries.size, totalWeight,
            if (excludePolymer && PolymerCompat.available) " (Polymer content excluded)" else "",
        )
        ChestLoot.buildPool(server, logger)
    }

    private fun buildCustomPool(excluded: Set<String>): List<Entry> {
        val pool = customEntries.mapNotNull { (blockId, weight) ->
            if (blockId in excluded) return@mapNotNull null
            val id = ResourceLocation.tryParse(blockId)
            val block = id?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null) }
            when {
                block == null -> {
                    logger.warn("loottable.json5: unknown block '{}' skipped.", blockId)
                    null
                }
                excludePolymer && PolymerCompat.isPolymerContent(block) -> {
                    logger.warn("loottable.json5: '{}' is Polymer content and was skipped.", blockId)
                    null
                }
                else -> Entry(safeState(block.defaultBlockState()), weight)
            }
        }
        if (pool.isEmpty()) {
            logger.error("Custom loot table is empty — falling back to minecraft:grass_block.")
            return listOf(Entry(Blocks.GRASS_BLOCK.defaultBlockState(), 1))
        }
        return pool
    }

    private fun buildAllBlocksPool(server: MinecraftServer, excluded: Set<String>): List<Entry> {
        val level = OneBlockDimension.level(server)
        // A guaranteed-air floating position for the canSurvive probe.
        val probePos = BlockPos(8, 200, 8)

        val pool = ArrayList<Entry>()
        for (block in BuiltInRegistries.BLOCK) {
            val id = BuiltInRegistries.BLOCK.getKey(block).toString()
            if (id in excluded) continue
            if (block == Blocks.AIR || block.asItem() === Items.AIR) continue // technical blocks
            // Polymer content only exists server-side; a client sees a stand-in block.
            if (excludePolymer && PolymerCompat.isPolymerContent(block)) continue
            if (block is LiquidBlock) continue // no fluids
            if (block.defaultDestroyTime() < 0f) continue // bedrock, barrier, command blocks, ...
            if (block is IceBlock) continue // melts into water in daylight
            val state = safeState(block.defaultBlockState())
            if (state.canBeReplaced()) continue // light, structure void, grass-like fillers
            if (level != null && !state.canSurvive(level, probePos)) continue // pops without support
            pool.add(Entry(state, 1))
        }
        if (level == null) {
            logger.warn("cobblemon_oneblock:world missing during loot pool build — support filter skipped.")
        }
        if (pool.isEmpty()) {
            logger.error("All-blocks loot pool came out empty — falling back to minecraft:grass_block.")
            return listOf(Entry(Blocks.GRASS_BLOCK.defaultBlockState(), 1))
        }
        return pool
    }

    /** Adjusts states that need tweaking to survive as a lone block (persistent leaves). */
    private fun safeState(state: BlockState): BlockState =
        if (state.hasProperty(LeavesBlock.PERSISTENT)) state.setValue(LeavesBlock.PERSISTENT, true) else state

    /**
     * The next block for this island's OneBlock.
     *
     * In `biomes` mode the island decides, which is the whole point of the tech tree; the
     * other two modes ignore it and keep their single global pool.
     */
    fun next(random: RandomSource, islandId: Long): BlockState {
        if (mode == MODE_BIOMES) return BiomePools.next(islandId, random)
        return next(random)
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
        root.put(
            "mode", JsonPrimitive(MODE_BIOMES),
            "\"biomes\" (default): each island draws from the biome tiers its tech tree has " +
                "unlocked. Edit the block sets in oneblock_biomes.json5 and the ladders that " +
                "unlock them in techtree.json5. " +
                "\"all_blocks\": legacy — every breakable, fluid-free, support-free block from " +
                "Minecraft and all mods, uniform chance. It never changes as an island progresses, " +
                "which is exactly why it was replaced. " +
                "\"custom\": one fixed weighted pool for every island, from the 'entries' list below.",
        )
        val blacklistArray = JsonArray()
        root.put(
            "blacklist", blacklistArray,
            "Block ids excluded in BOTH modes, e.g. \"minecraft:tnt\".",
        )
        root.put(
            "exclude_polymer", JsonPrimitive(true),
            "Skip blocks and items registered through Polymer. They exist only on the server " +
                "and vanilla clients see a stand-in block, so mining them is confusing. " +
                "Only set this to false if you know your Polymer content behaves like a real block.",
        )
        val chests = JsonObject()
        chests.put(
            "enabled", JsonPrimitive(ChestLoot.DEFAULT_ENABLED),
            "Let the OneBlock turn into a treasure chest instead of a plain block sometimes. " +
                "The chest is filled from a real chest loot table — mineshafts, dungeons, temples, " +
                "villages, strongholds, shipwrecks and whatever else this server has.",
        )
        chests.put(
            "chance", JsonPrimitive(ChestLoot.DEFAULT_CHANCE),
            "Share of breaks that produce a chest, as a fraction of 1. " +
                "1.0 = 100% (every break), 0.10 = 10%, 0.02 = 2% (default, about one chest every " +
                "50 blocks), 0.01 = 1%, 0.0 = never. Clamped to 0.0-1.0. Flat by design: it never " +
                "scales with border level, because difficulty scales on this project and rewards do not.",
        )
        chests.put(
            "include_modded", JsonPrimitive(ChestLoot.DEFAULT_INCLUDE_MODDED),
            "false = only Minecraft's own chest loot tables. true = every \"chests/...\" loot table " +
                "any mod or data pack registered. Modded structure loot can be far outside vanilla " +
                "balance, so this is off by default. The startup log reports how many were skipped.",
        )
        chests.put(
            "blacklist", JsonArray(),
            "Loot table ids never used, e.g. \"minecraft:chests/end_city_treasure\".",
        )
        chests.put(
            "extra", JsonArray(),
            "Loot table ids added to the pool verbatim, whatever their path. The \"chests/...\" " +
                "convention above is Minecraft's own; a mod is free to ignore it. Cobblemon does — " +
                "its structure chest loot lives under \"cobblemon:ruins/gilded_chests/ruins\", " +
                "\"cobblemon:shipwreck_coves/gilded_chests/big_treasure\" and similar, so list those " +
                "here if you want them. Ids that do not exist are reported in the log and skipped. " +
                "Note that Cobblemon already injects its items into the vanilla chest tables, so the " +
                "normal pool contains Cobblemon loot without any of this.",
        )
        root.put(
            "chests", chests,
            "Treasure chests from the OneBlock. Chest contents are vanilla's, not this mod's — " +
                "edit the loot tables with a data pack if you want different loot.",
        )
        val array = JsonArray()
        for ((blockId, weight) in CUSTOM_EXAMPLE) {
            val obj = JsonObject()
            obj.put("block", JsonPrimitive(blockId), null)
            obj.put("weight", JsonPrimitive(weight.toLong()), null)
            array.add(obj)
        }
        root.put(
            "entries", array,
            "Only used when mode is \"custom\". 'weight' is relative — higher = more common.",
        )
        try {
            Files.createDirectories(configDir)
            Files.writeString(file, root.toJson(true, true))
        } catch (e: Exception) {
            logger.error("Could not write default loottable.json5: {}", e.message)
        }
    }
}
