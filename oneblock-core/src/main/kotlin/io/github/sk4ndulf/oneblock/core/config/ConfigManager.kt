package io.github.sk4ndulf.oneblock.core.config

import blue.endless.jankson.Jankson
import blue.endless.jankson.JsonArray
import blue.endless.jankson.JsonObject
import blue.endless.jankson.JsonPrimitive
import blue.endless.jankson.api.SyntaxError
import org.slf4j.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads and saves the two config files under `config/oneblock/`:
 * `main.json5` (gameplay, wizard-managed) and `database.json5` (storage, file-only).
 *
 * Files are written with comments so admins can edit them without documentation.
 * Unknown keys are preserved on load but dropped on save (save is a full rewrite).
 */
class ConfigManager(val configDir: Path, private val logger: Logger) {

    private val jankson = Jankson.builder().build()

    @Volatile
    var mainConfig: MainConfig = MainConfig()
        private set

    @Volatile
    var databaseConfig: DatabaseConfig = DatabaseConfig()
        private set

    private val mainFile: Path get() = configDir.resolve("main.json5")
    private val databaseFile: Path get() = configDir.resolve("database.json5")

    fun loadAll() {
        Files.createDirectories(configDir)
        mainConfig = loadMain()
        databaseConfig = loadDatabase()
        // Rewrite both files so new fields and comments show up after updates.
        saveMain(mainConfig)
        saveDatabase(databaseConfig)
    }

    fun updateMain(config: MainConfig) {
        mainConfig = config.validated()
        saveMain(mainConfig)
    }

    private fun parse(file: Path): JsonObject? {
        if (!Files.exists(file)) return null
        return try {
            jankson.load(file.toFile())
        } catch (e: SyntaxError) {
            logger.error("Config file {} has a syntax error, using previous/default values: {}", file.fileName, e.completeMessage)
            null
        } catch (e: IOException) {
            logger.error("Config file {} could not be read, using previous/default values: {}", file.fileName, e.message)
            null
        }
    }

    private fun loadMain(): MainConfig {
        val json = parse(mainFile) ?: return MainConfig().validated()
        val defaults = MainConfig()
        return MainConfig(
            setupCompleted = json.bool("setup_completed", defaults.setupCompleted),
            language = json.string("language", defaults.language),
            hubPlatformRadius = json.int("hub_platform_radius", defaults.hubPlatformRadius),
            hubRadius = json.int("hub_radius", defaults.hubRadius),
            maxIslandSize = json.int("max_island_size", defaults.maxIslandSize),
            maxPartySize = json.int("max_party_size", defaults.maxPartySize),
            triggerEventThreshold = json.int("trigger_event_threshold", defaults.triggerEventThreshold),
            maxBiomeRegions = json.int("max_biome_regions", defaults.maxBiomeRegions),
            cobblemonSpawnMultiplier = json.double("cobblemon_spawn_multiplier", defaults.cobblemonSpawnMultiplier),
            serverPublic = json.bool("server_public", defaults.serverPublic),
            hubAllowBuilding = json.bool("hub_allow_building", defaults.hubAllowBuilding),
            resetArchiveDays = json.int("reset_archive_days", defaults.resetArchiveDays),
            inactivityPurgeDays = json.int("inactivity_purge_days", defaults.inactivityPurgeDays),
            inviteTimeoutSeconds = json.int("invite_timeout_seconds", defaults.inviteTimeoutSeconds),
            eventCooldownSeconds = json.int("event_cooldown_seconds", defaults.eventCooldownSeconds),
            eventTimeoutSeconds = json.int("event_timeout_seconds", defaults.eventTimeoutSeconds),
            pointsPerBreak = json.double("points_per_break", defaults.pointsPerBreak),
            partyDiminishingReturns = json.double("party_diminishing_returns", defaults.partyDiminishingReturns),
            borderLevelThresholds = json.doubleList("border_level_thresholds", defaults.borderLevelThresholds),
            borderLevelSizes = json.intList("border_level_sizes", defaults.borderLevelSizes),
            legendarySpecies = json.stringList("legendary_species", defaults.legendarySpecies),
        ).validated()
    }

    private fun loadDatabase(): DatabaseConfig {
        val json = parse(databaseFile) ?: return DatabaseConfig()
        val defaults = DatabaseConfig()
        val mysql = json.get("mysql") as? JsonObject ?: JsonObject()
        return DatabaseConfig(
            storage = json.string("storage", defaults.storage),
            mysqlHost = mysql.string("host", defaults.mysqlHost),
            mysqlPort = mysql.int("port", defaults.mysqlPort),
            mysqlDatabase = mysql.string("database", defaults.mysqlDatabase),
            mysqlUser = mysql.string("user", defaults.mysqlUser),
            mysqlPassword = mysql.string("password", defaults.mysqlPassword),
            poolSize = mysql.int("pool_size", defaults.poolSize),
        )
    }

    private fun saveMain(config: MainConfig) {
        val json = JsonObject()
        json.put("setup_completed", JsonPrimitive(config.setupCompleted),
            "Set by the in-game wizard. Island creation stays locked while this is false.")
        json.put("language", JsonPrimitive(config.language),
            "Language for all player-facing messages: \"en_us\" or \"de_de\".")
        json.put("hub_platform_radius", JsonPrimitive(config.hubPlatformRadius.toLong()),
            "Radius of the physical hub platform (the visible circle). Range 4-64. " +
                "Only used the first time the platform is generated.")
        json.put("hub_radius", JsonPrimitive(config.hubRadius.toLong()),
            "Hub protection radius in blocks (full spawn protection). Range 100-5000.")
        json.put("max_island_size", JsonPrimitive(config.maxIslandSize.toLong()),
            "Island size at border level 8, e.g. 1024 = 1024x1024. Range 64-10000, chunk-aligned. " +
                "Grid spacing is derived automatically (+${MainConfig.SPACING_BUFFER} buffer).")
        json.put("max_party_size", JsonPrimitive(config.maxPartySize.toLong()),
            "Maximum party members per island. Range 1-20.")
        json.put("trigger_event_threshold", JsonPrimitive(config.triggerEventThreshold.toLong()),
            "OneBlock breaks until a trigger event fires. Range 10-1000.")
        json.put("max_biome_regions", JsonPrimitive(config.maxBiomeRegions.toLong()),
            "Maximum biome regions per island. Range 1-50.")
        json.put("cobblemon_spawn_multiplier", JsonPrimitive(config.cobblemonSpawnMultiplier),
            "Cobblemon spawn intensity multiplier. Range 0.1-5.0.")
        json.put("server_public", JsonPrimitive(config.serverPublic),
            "Whether this server is public.")
        json.put("hub_allow_building", JsonPrimitive(config.hubAllowBuilding),
            "If true, admins may build inside the hub radius. Everyone else never can.")
        json.put("reset_archive_days", JsonPrimitive(config.resetArchiveDays.toLong()),
            "Days a reset island stays restorable before it is purged.")
        json.put("inactivity_purge_days", JsonPrimitive(config.inactivityPurgeDays.toLong()),
            "Days of owner inactivity after which an island is archived/purged. 0 disables the purge.")
        json.put("invite_timeout_seconds", JsonPrimitive(config.inviteTimeoutSeconds.toLong()),
            "Seconds until a pending party invite expires.")
        json.put("event_cooldown_seconds", JsonPrimitive(config.eventCooldownSeconds.toLong()),
            "Cooldown in seconds between trigger events on the same island.")
        json.put("event_timeout_seconds", JsonPrimitive(config.eventTimeoutSeconds.toLong()),
            "Hard timeout for a running trigger event. On timeout it fails and everything it spawned is cleaned up.")
        json.put("points_per_break", JsonPrimitive(config.pointsPerBreak),
            "Base progression points per OneBlock break (before party scaling).")
        json.put("party_diminishing_returns", JsonPrimitive(config.partyDiminishingReturns),
            "Diminishing returns per additional party member: factor = 1/(1+(n-1)*value). 0 disables.")
        json.put("border_level_thresholds", doubleArray(config.borderLevelThresholds),
            "Cumulative points required for border levels 2-8 (7 ascending values). " +
                "Invalid lists fall back to the defaults.")
        json.put("border_level_sizes", intArray(config.borderLevelSizes),
            "Optional manual border side lengths for levels 1-8 (8 values, chunk-aligned). " +
                "Empty = exponential interpolation from 16 to max_island_size.")
        json.put("legendary_species", stringArray(config.legendarySpecies),
            "Species the legendary encounter event can spawn. Any Cobblemon species name works.")
        write(mainFile, json)
    }

    private fun saveDatabase(config: DatabaseConfig) {
        val json = JsonObject()
        json.put("storage", JsonPrimitive(config.storage),
            "\"sqlite\" (default, embedded, zero setup - data lives in config/oneblock/data.db) " +
                "or \"mysql\" (also covers MariaDB; fill in the mysql block below).")
        val mysql = JsonObject()
        mysql.put("host", JsonPrimitive(config.mysqlHost), null)
        mysql.put("port", JsonPrimitive(config.mysqlPort.toLong()), null)
        mysql.put("database", JsonPrimitive(config.mysqlDatabase), null)
        mysql.put("user", JsonPrimitive(config.mysqlUser), null)
        mysql.put("password", JsonPrimitive(config.mysqlPassword), null)
        mysql.put("pool_size", JsonPrimitive(config.poolSize.toLong()), null)
        json.put("mysql", mysql, "Only used when storage is \"mysql\".")
        write(databaseFile, json)
    }

    private fun write(file: Path, json: JsonObject) {
        try {
            Files.writeString(file, json.toJson(true, true))
        } catch (e: IOException) {
            logger.error("Could not write config file {}: {}", file.fileName, e.message)
        }
    }

    private fun JsonObject.int(key: String, default: Int): Int =
        (get(key) as? JsonPrimitive)?.asInt(default) ?: default

    private fun JsonObject.double(key: String, default: Double): Double =
        (get(key) as? JsonPrimitive)?.asDouble(default) ?: default

    private fun JsonObject.bool(key: String, default: Boolean): Boolean =
        (get(key) as? JsonPrimitive)?.asBoolean(default) ?: default

    private fun JsonObject.string(key: String, default: String): String =
        (get(key) as? JsonPrimitive)?.asString() ?: default

    private fun JsonObject.doubleList(key: String, default: List<Double>): List<Double> =
        (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.asDouble(Double.NaN) }
            ?.filter { !it.isNaN() } ?: default

    private fun JsonObject.intList(key: String, default: List<Int>): List<Int> =
        (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.asInt(Int.MIN_VALUE) }
            ?.filter { it != Int.MIN_VALUE } ?: default

    private fun doubleArray(values: List<Double>): JsonArray =
        JsonArray().apply { values.forEach { add(JsonPrimitive(it)) } }

    private fun intArray(values: List<Int>): JsonArray =
        JsonArray().apply { values.forEach { add(JsonPrimitive(it.toLong())) } }

    private fun JsonObject.stringList(key: String, default: List<String>): List<String> =
        (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.asString() } ?: default

    private fun stringArray(values: List<String>): JsonArray =
        JsonArray().apply { values.forEach { add(JsonPrimitive(it)) } }
}
