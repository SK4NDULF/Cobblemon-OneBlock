package io.github.sk4ndulf.oneblock.core.lang

import com.google.gson.JsonParser
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.slf4j.Logger

/**
 * Server-side translations. Client lang files don't help a server-side mod (vanilla clients
 * don't have our keys), so all player-facing text is resolved on the server from the bundled
 * lang JSONs and sent as literal components.
 *
 * The language is set via `language` in main.json5; unknown keys fall back to en_us, then to
 * the key itself so a missing translation is visible instead of silent.
 */
object ServerLang {

    private var fallback: Map<String, String> = emptyMap()
    private var active: Map<String, String> = emptyMap()

    fun load(language: String, logger: Logger) {
        fallback = read("en_us", logger)
        active = if (language == "en_us") fallback else read(language, logger)
        if (active.isEmpty() && language != "en_us") {
            logger.warn("Language '{}' not found, falling back to en_us.", language)
            active = fallback
        }
    }

    private fun read(language: String, logger: Logger): Map<String, String> {
        val path = "/assets/oneblock/lang/$language.json"
        val stream = javaClass.getResourceAsStream(path) ?: return emptyMap()
        return try {
            stream.reader(Charsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader).asJsonObject.entrySet()
                    .associate { (key, value) -> key to value.asString }
            }
        } catch (e: Exception) {
            logger.error("Could not parse language file {}: {}", path, e.message)
            emptyMap()
        }
    }

    fun raw(key: String, vararg args: Any?): String {
        val template = active[key] ?: fallback[key] ?: return key
        return if (args.isEmpty()) template else template.format(*args)
    }

    fun msg(key: String, vararg args: Any?): MutableComponent = Component.literal(raw(key, *args))
}
