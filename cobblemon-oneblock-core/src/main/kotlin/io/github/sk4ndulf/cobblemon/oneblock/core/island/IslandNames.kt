package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

/**
 * Island names: validation, sanitising, and the one place that decides what an island is
 * called when it has no name of its own.
 *
 * A player-chosen name is shown to *other* players — in the tech menus, in `/ob info`, and
 * when someone visits. That makes it untrusted input on a display path, so it is cleaned
 * rather than trusted:
 *
 *  - **§ is removed entirely.** It is Minecraft's legacy formatting marker, and a name
 *    containing it can turn the rest of a chat line a different colour, make it magic-obfuscated,
 *    or hide it. Several code paths still interpret it, so the only safe answer is that the
 *    character never reaches one.
 *  - **Control characters become a space**, so a name cannot forge extra lines while a
 *    newline the player meant as a separator still reads as one.
 *  - **Unicode bidi controls are removed** — they are not ISO control characters, and a
 *    right-to-left override reverses how the rest of the sentence renders.
 *  - **Length is capped**, because a chest menu title has a fixed width and a very long name
 *    pushes everything else out of view.
 *
 * Deliberately *not* done: uniqueness and profanity filtering. Two islands may share a name —
 * nothing looks an island up by name, so there is nothing to make ambiguous — and word
 * filtering belongs to whatever chat moderation the server already runs, not to this mod.
 */
object IslandNames {

    const val MAX_LENGTH = 32

    /** Why a proposed name was rejected, or that it was accepted and what it became. */
    sealed interface Result {
        data class Ok(val name: String) : Result
        data object TooLong : Result
        /** Nothing survived cleaning — the input was empty, whitespace, or only stripped characters. */
        data object NothingLeft : Result
    }

    /**
     * Cleans and checks a proposed name.
     *
     * Length is measured **after** cleaning, so padding a name with stripped characters to get
     * under the limit does not work.
     */
    fun validate(raw: String): Result {
        val cleaned = sanitise(raw)
        return when {
            cleaned.isEmpty() -> Result.NothingLeft
            cleaned.length > MAX_LENGTH -> Result.TooLong
            else -> Result.Ok(cleaned)
        }
    }

    /**
     * Two different treatments, because the characters mean two different things.
     *
     * Formatting markers — the section sign and the bidi controls — are *removed*: they are
     * invisible instructions to the renderer, and the text around them was meant to be
     * adjacent. Control characters are *replaced with a space*: a newline or a tab was a
     * separator in the player's head, so "Rocket\nBase" should read "Rocket Base" and not
     * "RocketBase". Runs collapse afterwards, so either path ends up tidy.
     */
    private fun sanitise(raw: String): String =
        raw.asSequence()
            .filterNot { it == SECTION_SIGN || it in BIDI_CONTROLS }
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(WHITESPACE_RUN, " ")
            .trim()

    /**
     * Unicode bidirectional formatting characters.
     *
     * Not covered by [Char.isISOControl], and worth removing separately: a right-to-left
     * override in the middle of a name reverses how the *rest of the line* renders, so an
     * island name could scramble the sentence it is embedded in.
     */
    private val BIDI_CONTROLS: Set<Char> = setOf(
        '\u200E', '\u200F',                               // LTR / RTL mark
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', // embedding, pop, override
        '\u2066', '\u2067', '\u2068', '\u2069',          // isolates
    )

    /**
     * What to call an island in any player-facing text.
     *
     * An island with no name falls back to its owner's name, which is why this is a function
     * and not a field: the owner may be offline, so it needs the profile cache, and the answer
     * has to be identical everywhere or the same island appears under two names.
     */
    fun displayName(island: IslandData, server: MinecraftServer): Component {
        island.name?.let { return Component.literal(it) }
        return ServerLang.msg("cobblemon_oneblock.menu.island_title", ownerName(island, server))
    }

    /** Plain-string form, for lang arguments that are formatted into a bigger sentence. */
    fun displayText(island: IslandData, server: MinecraftServer): String =
        island.name ?: ServerLang.raw("cobblemon_oneblock.menu.island_title", ownerName(island, server))

    private fun ownerName(island: IslandData, server: MinecraftServer): String =
        server.playerList.getPlayer(island.owner())?.gameProfile?.name
            ?: server.profileCache?.get(island.owner())?.orElse(null)?.name
            ?: "#${island.id}"

    private const val SECTION_SIGN = '§'
    private val WHITESPACE_RUN = Regex("\\s+")
}
