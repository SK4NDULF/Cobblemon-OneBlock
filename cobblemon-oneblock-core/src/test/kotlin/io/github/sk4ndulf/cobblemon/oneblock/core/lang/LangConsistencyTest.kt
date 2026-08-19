package io.github.sk4ndulf.cobblemon.oneblock.core.lang

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The language files, checked against each other.
 *
 * This exists because of a real defect class rather than tidiness: every message is rendered
 * with [String.format], so a translation whose placeholders differ from the English original
 * does not render oddly — it throws. `%d` handed a string is an
 * `IllegalFormatConversionException` at the moment a player triggers that message, on that
 * language only, which is exactly the kind of bug that ships.
 *
 * A missing key is milder (the English text is used) but still worth naming, because nobody
 * notices a German server quietly speaking English one line at a time.
 */
class LangConsistencyTest {

    private fun load(language: String): Map<String, String> {
        val stream = javaClass.getResourceAsStream("/assets/cobblemon_oneblock/lang/$language.json")
        requireNotNull(stream) { "lang file $language.json is not on the classpath" }
        return stream.reader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject.entrySet()
                .associate { (key, value) -> key to value.asString }
        }
    }

    /**
     * Which argument each template consumes, as position → conversion.
     *
     * Comparing the specifiers *in order* would be wrong, and the first version of this test
     * was: German word order often needs the arguments reordered, which Java expresses as
     * `%2$s ... %1$s`. That is correct and must pass. What actually has to match is the set of
     * positions and the type at each — so the comparison is by position, not by sequence.
     *
     * `%%` is a literal percent sign and consumes no argument. An explicit index does not
     * advance the implicit counter, which is Java's rule and matters for a mixed template.
     */
    private fun argumentTypes(template: String): Map<Int, Char> {
        val regex = Regex("%(?:(%)|(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z]))")
        var nextImplicit = 1
        val result = HashMap<Int, Char>()
        for (match in regex.findAll(template)) {
            if (match.groupValues[1] == "%") continue
            val explicit = match.groupValues[2].toIntOrNull()
            val position = explicit ?: nextImplicit++
            result[position] = match.groupValues[3].first().lowercaseChar()
        }
        return result
    }

    @Test
    fun `every english key exists in german`() {
        val english = load("en_us")
        val german = load("de_de")
        val missing = english.keys - german.keys
        assertTrue(
            missing.isEmpty(),
            "de_de is missing ${missing.size} key(s), so those lines fall back to English: " +
                missing.sorted().joinToString(", "),
        )
    }

    @Test
    fun `german has no keys english does not`() {
        val english = load("en_us")
        val german = load("de_de")
        val extra = german.keys - english.keys
        assertTrue(
            extra.isEmpty(),
            "de_de has ${extra.size} key(s) en_us does not, which usually means a typo: " +
                extra.sorted().joinToString(", "),
        )
    }

    @Test
    fun `translations take the same format arguments as the english original`() {
        val english = load("en_us")
        val german = load("de_de")
        val mismatched = english.keys.intersect(german.keys).filter { key ->
            argumentTypes(english.getValue(key)) != argumentTypes(german.getValue(key))
        }
        assertTrue(
            mismatched.isEmpty(),
            "argument mismatch — a translation wanting an argument the code does not pass throws " +
                "at format time, and one wanting fewer silently drops the detail: " +
                mismatched.sorted().joinToString(", ") { key ->
                    "$key (en ${argumentTypes(english.getValue(key))} vs de ${argumentTypes(german.getValue(key))})"
                },
        )
    }

    @Test
    fun `no key is defined twice`() {
        // Gson silently keeps the last duplicate, so a doubled key is invisible at runtime.
        for (language in listOf("en_us", "de_de")) {
            val raw = javaClass.getResourceAsStream("/assets/cobblemon_oneblock/lang/$language.json")!!
                .reader(Charsets.UTF_8).use { it.readText() }
            val declared = Regex("^\\s*\"([^\"]+)\"\\s*:", RegexOption.MULTILINE)
                .findAll(raw).map { it.groupValues[1] }.toList()
            assertEquals(
                declared.size, declared.distinct().size,
                "$language.json declares duplicate keys: " +
                    declared.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.joinToString(", "),
            )
        }
    }
}
