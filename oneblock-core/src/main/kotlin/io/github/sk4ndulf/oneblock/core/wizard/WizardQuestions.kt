package io.github.sk4ndulf.oneblock.core.wizard

import io.github.sk4ndulf.oneblock.core.config.MainConfig
import io.github.sk4ndulf.oneblock.core.lang.ServerLang

/** Result of validating a raw answer: either an updated config or a lang-key error. */
sealed interface AnswerResult {
    data class Ok(val config: MainConfig) : AnswerResult
    data class Error(val message: String) : AnswerResult
}

sealed interface QuestionKind {
    data class WholeNumber(val min: Int, val max: Int) : QuestionKind
    data class Decimal(val min: Double, val max: Double) : QuestionKind
    data object YesNo : QuestionKind
}

/**
 * One wizard question. `key` doubles as the identifier for `/ob setup set <key> <value>`,
 * `current` reads the value from a config, `apply` validates + writes a raw answer into it.
 */
class WizardQuestion(
    val key: String,
    val kind: QuestionKind,
    val current: (MainConfig) -> String,
    val apply: (MainConfig, String) -> AnswerResult,
) {
    val questionLangKey: String get() = "oneblock.question.$key"
    val labelLangKey: String get() = "oneblock.label.$key"
}

object WizardQuestions {

    private fun parseBool(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "yes", "ja", "y", "j", "true", "1" -> true
        "no", "nein", "n", "false", "0" -> false
        else -> null
    }

    fun boolText(value: Boolean): String =
        ServerLang.raw(if (value) "oneblock.value.yes" else "oneblock.value.no")

    private fun intQuestion(
        key: String, min: Int, max: Int,
        get: (MainConfig) -> Int, set: (MainConfig, Int) -> MainConfig,
    ) = WizardQuestion(key, QuestionKind.WholeNumber(min, max), { get(it).toString() }, { config, raw ->
        val value = raw.trim().toIntOrNull()
        when {
            value == null -> AnswerResult.Error(ServerLang.raw("oneblock.wizard.invalid_number", raw.trim()))
            value < min || value > max -> AnswerResult.Error(ServerLang.raw("oneblock.wizard.out_of_range", min, max))
            else -> AnswerResult.Ok(set(config, value))
        }
    })

    private fun doubleQuestion(
        key: String, min: Double, max: Double,
        get: (MainConfig) -> Double, set: (MainConfig, Double) -> MainConfig,
    ) = WizardQuestion(key, QuestionKind.Decimal(min, max), { get(it).toString() }, { config, raw ->
        val value = raw.trim().replace(',', '.').toDoubleOrNull()
        when {
            value == null -> AnswerResult.Error(ServerLang.raw("oneblock.wizard.invalid_number", raw.trim()))
            value < min || value > max -> AnswerResult.Error(ServerLang.raw("oneblock.wizard.out_of_range", min, max))
            else -> AnswerResult.Ok(set(config, value))
        }
    })

    private fun boolQuestion(
        key: String,
        get: (MainConfig) -> Boolean, set: (MainConfig, Boolean) -> MainConfig,
    ) = WizardQuestion(key, QuestionKind.YesNo, { boolText(get(it)) }, { config, raw ->
        when (val value = parseBool(raw)) {
            null -> AnswerResult.Error(ServerLang.raw("oneblock.wizard.invalid_bool"))
            else -> AnswerResult.Ok(set(config, value))
        }
    })

    /** The 8 wizard questions, in order — mirrors the table in PROJECT_PLAN.md section 6. */
    val ALL: List<WizardQuestion> = listOf(
        intQuestion("hub_radius", 100, 5000, { it.hubRadius }, { c, v -> c.copy(hubRadius = v) }),
        intQuestion("max_island_size", 64, 10000, { it.maxIslandSize },
            { c, v -> c.copy(maxIslandSize = MainConfig.chunkAlign(v)) }),
        intQuestion("max_party_size", 1, 20, { it.maxPartySize }, { c, v -> c.copy(maxPartySize = v) }),
        intQuestion("trigger_event_threshold", 10, 1000, { it.triggerEventThreshold },
            { c, v -> c.copy(triggerEventThreshold = v) }),
        intQuestion("max_biome_regions", 1, 50, { it.maxBiomeRegions }, { c, v -> c.copy(maxBiomeRegions = v) }),
        doubleQuestion("cobblemon_spawn_multiplier", 0.1, 5.0, { it.cobblemonSpawnMultiplier },
            { c, v -> c.copy(cobblemonSpawnMultiplier = v) }),
        boolQuestion("server_public", { it.serverPublic }, { c, v -> c.copy(serverPublic = v) }),
        boolQuestion("hub_allow_building", { it.hubAllowBuilding }, { c, v -> c.copy(hubAllowBuilding = v) }),
    )

    fun byKey(key: String): WizardQuestion? = ALL.firstOrNull { it.key == key }

    val validKeys: String get() = ALL.joinToString(", ") { it.key }
}
