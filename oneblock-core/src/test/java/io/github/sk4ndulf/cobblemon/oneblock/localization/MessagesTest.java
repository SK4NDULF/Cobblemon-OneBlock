package io.github.sk4ndulf.cobblemon.oneblock.localization;

import io.github.sk4ndulf.cobblemon.oneblock.bootstrap.LifecycleState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessagesTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%s");
    private final Messages messages = Messages.loadBundled();

    @Test
    void everyLanguageHasTheSameKeysAndPlaceholders() {
        Map<String, String> en = messages.table("en_us");
        for (String lang : Messages.LANGUAGES) {
            Map<String, String> table = messages.table(lang);
            assertEquals(en.keySet(), table.keySet(), lang);
            for (Map.Entry<String, String> e : en.entrySet()) {
                assertEquals(count(e.getValue()), count(table.get(e.getKey())), lang + " " + e.getKey());
            }
        }
    }

    @Test
    void everyLifecycleStateHasNameAndDescription() {
        for (String lang : Messages.LANGUAGES) {
            for (LifecycleState s : LifecycleState.values()) {
                assertNotNull(messages.table(lang).get("state." + s.name()), lang + " " + s);
                assertNotNull(messages.table(lang).get("state.desc." + s.name()), lang + " " + s);
            }
        }
    }

    @Test
    void languageResolution() {
        assertEquals("de_de", Messages.resolveLanguage("de_DE"));
        assertEquals("de_de", Messages.resolveLanguage("de_at"));
        assertEquals("en_us", Messages.resolveLanguage("fr_fr"));
        assertEquals("en_us", Messages.resolveLanguage(null));
    }

    @Test
    void formattingAndFallbacks() {
        assertEquals("Zustand: bereit", messages.get("de_de", "status.state", "bereit"));
        assertEquals("State: ready", messages.get("ja_jp", "status.state", "ready"));
        assertEquals("no.such.key", messages.get("de_de", "no.such.key"));
    }

    private static int count(String s) {
        Matcher m = PLACEHOLDER.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
