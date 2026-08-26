package io.github.sk4ndulf.cobblemon.oneblock.core.cobblemon

/**
 * Temporary Cobblemon buffs an island can carry. Applied when a Pokémon spawns inside
 * the island's border, so they affect newly spawned Pokémon only — never ones already
 * caught or spawned earlier.
 */
enum class BuffType {
    /** Absolute shiny chance (0.0-1.0) for Pokémon spawning on the island. */
    SHINY_RATE,

    /** Minimum IV each stat is raised to (0-31) for Pokémon spawning on the island. */
    IV_FLOOR,
    ;

    companion object {
        fun parse(raw: String): BuffType? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

data class IslandBuff(val type: BuffType, val value: Double, val expiresAtMillis: Long) {
    val active: Boolean get() = expiresAtMillis > System.currentTimeMillis()
}
