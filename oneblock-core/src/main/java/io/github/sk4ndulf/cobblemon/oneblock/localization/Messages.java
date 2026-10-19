package io.github.sk4ndulf.cobblemon.oneblock.localization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side translations (spec 03): clients have no OneBlock resource pack, so we resolve text on the server and
 * send literal components. Unknown languages fall back to {@code en_us}; a missing key falls back to {@code en_us},
 * then to the key itself (logged once).
 */
public final class Messages {
    public static final String FALLBACK = "en_us";
    public static final Set<String> LANGUAGES = Set.of("en_us", "de_de");

    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");
    private static final Set<String> REPORTED_MISSING = ConcurrentHashMap.newKeySet();

    private final Map<String, Map<String, String>> tables;

    private Messages(Map<String, Map<String, String>> tables) {
        this.tables = Map.copyOf(tables);
    }

    public static Messages loadBundled() {
        Map<String, Map<String, String>> tables = new HashMap<>();
        for (String language : LANGUAGES) {
            tables.put(language, load("/cobblemon_oneblock/lang/" + language + ".json"));
        }
        return new Messages(tables);
    }

    static Map<String, String> load(String resource) {
        try (InputStream in = Messages.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing language file " + resource);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> table = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                table.put(entry.getKey(), entry.getValue().getAsString());
            }
            return Map.copyOf(table);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Maps a client language code (e.g. "de_DE", "de_at") to a bundled language. */
    public static String resolveLanguage(String clientLanguage) {
        if (clientLanguage == null) {
            return FALLBACK;
        }
        String lang = clientLanguage.toLowerCase(Locale.ROOT);
        if (LANGUAGES.contains(lang)) {
            return lang;
        }
        if (lang.startsWith("de_")) {
            return "de_de";
        }
        return FALLBACK;
    }

    public String get(String language, String key, Object... args) {
        String pattern = tables.getOrDefault(resolveLanguage(language), Map.of()).get(key);
        if (pattern == null) {
            pattern = tables.get(FALLBACK).get(key);
        }
        if (pattern == null) {
            if (REPORTED_MISSING.add(key)) {
                LOG.warn("Missing translation key {}", key);
            }
            return key;
        }
        try {
            return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
        } catch (IllegalFormatException e) {
            LOG.warn("Bad arguments for translation key {}: {}", key, e.getMessage());
            return pattern;
        }
    }

    Map<String, String> table(String language) {
        return tables.get(language);
    }
}
