package io.github.sk4ndulf.cobblemon.oneblock.core.progression

/**
 * The six branches of the island tech tree. The order here is the order they are shown in,
 * both in chat and later as the tab row of the chest menu, so it is deliberate: the two
 * categories that change the core loop come first.
 *
 * See PROGRESSION_REWORK.md §6 for what each one covers.
 */
enum class TechCategory {
    ONEBLOCK,
    ISLAND,
    PLAYERS,
    POKEMON,
    BOOSTS,
    SPECIAL,
    ;

    /** Lowercase form used in the config file, in commands and in lang keys. */
    val key: String get() = name.lowercase()

    companion object {
        fun parse(raw: String): TechCategory? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}
