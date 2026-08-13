package io.github.sk4ndulf.cobblemon.oneblock.core.config

/**
 * Gameplay configuration. Field defaults are the wizard skip-defaults from the project plan.
 * All sizes are aligned to chunk boundaries (multiples of 16) on load.
 */
data class MainConfig(
    /** Whether the in-game setup wizard has been completed. Island creation is locked until then. */
    val setupCompleted: Boolean = false,

    /** Language for all player-facing messages ("en_us", "de_de"). */
    val language: String = "en_us",

    /** Radius of the physical hub platform in blocks (visual platform, not the protection zone). */
    val hubPlatformRadius: Int = 16,

    /** Hub protection radius in blocks (full spawn protection). Wizard question 1. Range 100..5000. */
    val hubRadius: Int = 1000,

    /** Maximum island size at border level 8 (e.g. 1024 = 1024x1024). Wizard question 2. Range 64..10000. */
    val maxIslandSize: Int = 1024,

    /** Maximum party size per island. Wizard question 3. Range 1..20. */
    val maxPartySize: Int = 4,

    /** OneBlock breaks until a trigger event fires. Wizard question 4. Range 10..1000. */
    val triggerEventThreshold: Int = 100,

    /** Maximum biome regions per island. Wizard question 5. Range 1..50. */
    val maxBiomeRegions: Int = 10,

    /** Cobblemon spawn intensity multiplier. Wizard question 6. Range 0.1..5.0. */
    val cobblemonSpawnMultiplier: Double = 1.0,

    /** Whether the server is public. Wizard question 7. */
    val serverPublic: Boolean = true,

    /** Whether admins may build inside the hub radius. Wizard question 8. */
    val hubAllowBuilding: Boolean = false,

    /**
     * Send everything a broken OneBlock produced straight into the breaker's inventory.
     *
     * On by default because the alternative is losing it: the anchor floats over the void, the
     * replacement block is placed in the same tick, and the drops get pushed out of it and fall.
     * Turning this off restores vanilla dropping, which on a OneBlock island means the player
     * needs something under the anchor to catch it — see [anchorBedrockFoundation].
     */
    val oneBlockDropsToInventory: Boolean = true,

    /**
     * Keep an indestructible bedrock block one below every anchor.
     *
     * Off by default: it is a visible slab under what should look like a single floating block,
     * and with [oneBlockDropsToInventory] on, nothing depends on it any more. It remains a
     * safety net for the rare case where the anchor ends up empty — an explosion, a stray
     * command, another mod — and a player standing there drops into the void before the 60 s
     * repair sweep restores it. Switching it back on re-places it; switching it off removes the
     * bedrock again, and only bedrock, so a block a player put there themselves is left alone.
     */
    val anchorBedrockFoundation: Boolean = false,

    /** Days an island stays restorable after /ob reset before it is purged. */
    val resetArchiveDays: Int = 7,

    /** Days of owner inactivity after which an island is archived/purged. 0 disables the purge. */
    val inactivityPurgeDays: Int = 90,

    /** Seconds until a pending party invite expires. */
    val inviteTimeoutSeconds: Int = 120,

    /** Cooldown in seconds between trigger events on the same island. */
    val eventCooldownSeconds: Int = 300,

    /** Hard timeout in seconds after which a running trigger event fails and cleans up. */
    val eventTimeoutSeconds: Int = 300,

    /** Base points one OneBlock break is worth (before party scaling). */
    val pointsPerBreak: Double = 100.0,

    /**
     * Diminishing returns per additional party member: factor = 1 / (1 + (n-1) * value).
     * 0.0 disables scaling (every break worth full points regardless of party size).
     */
    val partyDiminishingReturns: Double = 0.25,

    /**
     * Cumulative points required for border levels 2-8 (7 ascending values).
     * With 100 points/break these defaults mean 500 / 1250 / 2500 / 5000 / 9000 /
     * 15000 / 25000 solo breaks.
     */
    val borderLevelThresholds: List<Double> = DEFAULT_THRESHOLDS,

    /**
     * Optional manual override of the border side lengths for levels 1-8 (8 values,
     * chunk-aligned on load). Empty = exponential interpolation from 16 to max_island_size.
     */
    val borderLevelSizes: List<Int> = emptyList(),

    /** Species pool for the legendary encounter trigger event. */
    val legendarySpecies: List<String> = DEFAULT_LEGENDARY_SPECIES,

    /** Optional Discord webhook that mirrors the audit log. Empty disables it. */
    val discordWebhookUrl: String = "",
) {

    companion object {
        /** Rounds up to the next multiple of 16 so borders and permission checks stay chunk-aligned. */
        fun chunkAlign(size: Int): Int = ((size + 15) / 16) * 16

        /** Fixed safety buffer between islands at full size; the grid spacing derives from it. */
        const val SPACING_BUFFER = 1024

        /** Cumulative point thresholds for levels 2-8 (see borderLevelThresholds). */
        val DEFAULT_THRESHOLDS: List<Double> =
            listOf(50_000.0, 125_000.0, 250_000.0, 500_000.0, 900_000.0, 1_500_000.0, 2_500_000.0)

        /** Legendary/mythical species the legendary encounter can pick from. */
        val DEFAULT_LEGENDARY_SPECIES: List<String> = listOf(
            "articuno", "zapdos", "moltres", "raikou", "entei", "suicune",
            "regirock", "regice", "registeel", "latias", "latios",
            "uxie", "mesprit", "azelf", "heatran", "cresselia",
            "cobalion", "terrakion", "virizion", "tornadus", "thundurus", "landorus",
        )
    }

    /** Grid spacing between island anchor points. Single source of truth for the spacing formula. */
    val islandSpacing: Int
        get() = chunkAlign(maxIslandSize) + SPACING_BUFFER

    /** Returns a copy with every value clamped to its valid wizard range (chunk-aligned where relevant). */
    fun validated(): MainConfig = copy(
        hubPlatformRadius = hubPlatformRadius.coerceIn(4, 64),
        hubRadius = hubRadius.coerceIn(100, 5000),
        maxIslandSize = chunkAlign(maxIslandSize.coerceIn(64, 10000)),
        maxPartySize = maxPartySize.coerceIn(1, 20),
        triggerEventThreshold = triggerEventThreshold.coerceIn(10, 1000),
        maxBiomeRegions = maxBiomeRegions.coerceIn(1, 50),
        cobblemonSpawnMultiplier = cobblemonSpawnMultiplier.coerceIn(0.1, 5.0),
        resetArchiveDays = resetArchiveDays.coerceIn(0, 365),
        inactivityPurgeDays = inactivityPurgeDays.coerceIn(0, 3650),
        inviteTimeoutSeconds = inviteTimeoutSeconds.coerceIn(10, 3600),
        eventCooldownSeconds = eventCooldownSeconds.coerceIn(0, 86400),
        eventTimeoutSeconds = eventTimeoutSeconds.coerceIn(30, 3600),
        pointsPerBreak = pointsPerBreak.coerceIn(0.01, 1_000_000.0),
        partyDiminishingReturns = partyDiminishingReturns.coerceIn(0.0, 1.0),
        borderLevelThresholds = validatedThresholds(),
        borderLevelSizes = validatedSizes(),
        legendarySpecies = legendarySpecies.map { it.trim().lowercase() }.filter { it.isNotEmpty() },
    )

    /** 7 strictly ascending positive values, else the defaults (misconfig must not brick leveling). */
    private fun validatedThresholds(): List<Double> {
        if (borderLevelThresholds.size != 7) return DEFAULT_THRESHOLDS
        if (borderLevelThresholds.any { it <= 0 }) return DEFAULT_THRESHOLDS
        if (borderLevelThresholds.zipWithNext().any { (a, b) -> b <= a }) return DEFAULT_THRESHOLDS
        return borderLevelThresholds
    }

    /** Empty (= interpolation) or exactly 8 ascending sizes, chunk-aligned and capped at max size. */
    private fun validatedSizes(): List<Int> {
        if (borderLevelSizes.isEmpty()) return emptyList()
        if (borderLevelSizes.size != 8) return emptyList()
        val aligned = borderLevelSizes.map { chunkAlign(it.coerceIn(16, chunkAlign(maxIslandSize))) }
        if (aligned.zipWithNext().any { (a, b) -> b < a }) return emptyList()
        return aligned
    }
}
