package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang

/**
 * Turns a typed effect into the line a player reads.
 *
 * Written from the payload rather than from the node's prose, because the two drift: an admin
 * who retunes `chance` from 0.10 to 0.35 in `techtree.json5` will not remember to rewrite the
 * description, and then the tree lies about what it sells. Reading the number back out of the
 * effect means the displayed value is the value that will actually apply.
 *
 * Ranks are cumulative, so a node's next rank is described by the effects **that rank adds**,
 * not by everything it will then have.
 */
object TechEffectText {

    /** One line per effect, in the order the node lists them. Empty when a rank adds nothing. */
    fun describe(effects: List<TechEffect>): List<String> = effects.map(::describe)

    fun describe(effect: TechEffect): String = when (effect) {
        is TechEffect.OneBlockBiome ->
            ServerLang.raw("cobblemon_oneblock.effect.oneblock_biome", biomeName(effect.biome), effect.tier)
        is TechEffect.OneBlockYield ->
            ServerLang.raw("cobblemon_oneblock.effect.oneblock_yield", percent(effect.chance), biomeName(effect.biome))
        is TechEffect.ChestChance ->
            ServerLang.raw("cobblemon_oneblock.effect.chest_chance", percent(effect.added))
        is TechEffect.BorderSize ->
            ServerLang.raw("cobblemon_oneblock.effect.border_size", effect.size, effect.size)
        is TechEffect.IslandInt ->
            ServerLang.raw("cobblemon_oneblock.effect.island_int.${effect.key}", effect.value)
        is TechEffect.PlayerEffect ->
            ServerLang.raw("cobblemon_oneblock.effect.player.${effect.key}", percent(effect.magnitude))
        is TechEffect.PokemonWork ->
            ServerLang.raw(
                "cobblemon_oneblock.effect.pokemon_work.${effect.axis}",
                typeName(effect.pokemonType), percent(effect.magnitude),
            )
        is TechEffect.BoostUnlock ->
            ServerLang.raw(
                "cobblemon_oneblock.effect.boost.${effect.boost}",
                trimZeros(effect.strength), effect.durationMinutes,
            )
        is TechEffect.UnlockFlag ->
            ServerLang.raw("cobblemon_oneblock.effect.unlock_flag", effect.flag)
    }

    /**
     * A percentage without trailing noise: 0.35 reads "35%", 0.075 reads "7.5%".
     *
     * Rounded to one decimal because a lore line is not a spreadsheet, and formatted through
     * [String.format] with an explicit root locale — a German server would otherwise print
     * "7,5%" into a string the rest of which is English.
     */
    private fun percent(value: Double): String = trimZeros(value * 100.0) + "%"

    private fun trimZeros(value: Double): String {
        val rounded = String.format(java.util.Locale.ROOT, "%.1f", value)
        return rounded.removeSuffix(".0")
    }

    /** Biome and type ids are lowercase keys; a lang entry gives them a proper name if it exists. */
    private fun biomeName(id: String): String = named("cobblemon_oneblock.biome_name.$id", id)

    private fun typeName(id: String): String = named("cobblemon_oneblock.type_name.$id", id)

    private fun named(key: String, fallback: String): String {
        val translated = ServerLang.raw(key)
        // ServerLang returns the key itself when there is no entry, which is a usable signal
        // here: it means nobody has named this id, so title-case the id instead of showing it.
        return if (translated == key) fallback.replaceFirstChar(Char::titlecase) else translated
    }
}
