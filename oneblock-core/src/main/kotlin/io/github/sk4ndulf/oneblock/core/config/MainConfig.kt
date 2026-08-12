package io.github.sk4ndulf.oneblock.core.config

/**
 * Gameplay configuration. Field defaults are the wizard skip-defaults from the project plan.
 * All sizes are aligned to chunk boundaries (multiples of 16) on load.
 */
data class MainConfig(
    /** Whether the in-game setup wizard has been completed. Island creation is locked until then. */
    val setupCompleted: Boolean = false,

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

    /** Days an island stays restorable after /ob reset before it is purged. */
    val resetArchiveDays: Int = 7,

    /** Days of owner inactivity after which an island is archived/purged. 0 disables the purge. */
    val inactivityPurgeDays: Int = 90,

    /** Seconds until a pending party invite expires. */
    val inviteTimeoutSeconds: Int = 120,

    /** Cooldown in seconds between trigger events on the same island. */
    val eventCooldownSeconds: Int = 300,
) {

    companion object {
        /** Rounds up to the next multiple of 16 so borders and permission checks stay chunk-aligned. */
        fun chunkAlign(size: Int): Int = ((size + 15) / 16) * 16

        /** Fixed safety buffer between islands at full size; the grid spacing derives from it. */
        const val SPACING_BUFFER = 1024
    }

    /** Grid spacing between island anchor points. Single source of truth for the spacing formula. */
    val islandSpacing: Int
        get() = chunkAlign(maxIslandSize) + SPACING_BUFFER

    /** Returns a copy with every value clamped to its valid wizard range (chunk-aligned where relevant). */
    fun validated(): MainConfig = copy(
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
    )
}
