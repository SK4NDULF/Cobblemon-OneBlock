package io.github.sk4ndulf.cobblemon.oneblock.core.progression

/**
 * What a node level actually does.
 *
 * Typed rather than a free-form string map, because an effect that cannot be validated at
 * load time becomes a silent no-op in production — the tree file is admin-editable, so a
 * typo in an effect name has to be a startup warning, not a mystery months later.
 *
 * **Not all of these have a consumer yet.** Slice A builds the model, the storage and the
 * spending; the payloads land in later slices (see PROGRESSION_REWORK.md §10). Effects
 * without a consumer are reported once at startup by [TechTree.reportUnconsumedEffects] so
 * the gap is visible instead of looking like a bug.
 */
sealed interface TechEffect {

    /** The kind name as written in techtree.json5. Used for logging and for the consumer check. */
    val type: String

    /** Adds a biome's tier-N block set to the island's OneBlock pool. */
    data class OneBlockBiome(val biome: String, val tier: Int) : TechEffect {
        override val type get() = TYPE_ONEBLOCK_BIOME
    }

    /** Chance that a break of a block from this biome yields its drop twice. */
    data class OneBlockYield(val biome: String, val chance: Double) : TechEffect {
        override val type get() = TYPE_ONEBLOCK_YIELD
    }

    /** Added to the island's treasure chest chance, on top of the configured base. */
    data class ChestChance(val added: Double) : TechEffect {
        override val type get() = TYPE_CHEST_CHANCE
    }

    /** Sets the island's border side length. Replaces automatic border levelling. */
    data class BorderSize(val size: Int) : TechEffect {
        override val type get() = TYPE_BORDER_SIZE
    }

    /** Per-island override of a value that is a server-wide config constant today. */
    data class IslandInt(val key: String, val value: Int) : TechEffect {
        override val type get() = TYPE_ISLAND_INT
    }

    /** A quality-of-life effect for members standing inside their own island border. */
    data class PlayerEffect(val key: String, val magnitude: Double) : TechEffect {
        override val type get() = TYPE_PLAYER_EFFECT
    }

    /** One axis of the labour system, for one Pokémon type. See PROGRESSION_REWORK.md §7. */
    data class PokemonWork(val pokemonType: String, val axis: String, val magnitude: Double) : TechEffect {
        override val type get() = TYPE_POKEMON_WORK
    }

    /** Makes a timed boost available, at this strength and duration. */
    data class BoostUnlock(val boost: String, val strength: Double, val durationMinutes: Int) : TechEffect {
        override val type get() = TYPE_BOOST_UNLOCK
    }

    /** A plain named flag, for anything that only needs a boolean. */
    data class UnlockFlag(val flag: String) : TechEffect {
        override val type get() = TYPE_UNLOCK_FLAG
    }

    companion object {
        const val TYPE_ONEBLOCK_BIOME = "oneblock_biome"
        const val TYPE_ONEBLOCK_YIELD = "oneblock_yield"
        const val TYPE_CHEST_CHANCE = "chest_chance"
        const val TYPE_BORDER_SIZE = "border_size"
        const val TYPE_ISLAND_INT = "island_int"
        const val TYPE_PLAYER_EFFECT = "player_effect"
        const val TYPE_POKEMON_WORK = "pokemon_work"
        const val TYPE_BOOST_UNLOCK = "boost_unlock"
        const val TYPE_UNLOCK_FLAG = "unlock_flag"

        /**
         * Effect types a slice has already shipped a consumer for. Everything else is parsed
         * and stored but does nothing yet. Move a constant in here in the same commit that
         * adds its consumer — this list is what keeps the startup report honest.
         */
        val CONSUMED: Set<String> = setOf(
            TYPE_ONEBLOCK_BIOME, // BiomePools.poolFor
            TYPE_ONEBLOCK_YIELD, // BiomePools.yieldChance
            TYPE_CHEST_CHANCE, // TechEffects.chestChanceBonus
        )

        val ALL_TYPES: Set<String> = setOf(
            TYPE_ONEBLOCK_BIOME, TYPE_ONEBLOCK_YIELD, TYPE_CHEST_CHANCE, TYPE_BORDER_SIZE,
            TYPE_ISLAND_INT, TYPE_PLAYER_EFFECT, TYPE_POKEMON_WORK, TYPE_BOOST_UNLOCK,
            TYPE_UNLOCK_FLAG,
        )

        /** Valid keys for [IslandInt] — a typo here would silently do nothing. */
        val ISLAND_INT_KEYS: Set<String> = setOf("max_biome_regions", "max_party_size")

        /** Valid keys for [PlayerEffect]. */
        val PLAYER_EFFECT_KEYS: Set<String> = setOf("hunger", "regen", "fall", "fly")

        /** Valid axes for [PokemonWork] — the four stat roles plus the two sleep knobs. */
        val WORK_AXES: Set<String> = setOf("speed", "hp", "resist", "yield", "threshold", "sleep_regen")
    }
}
