package io.github.sk4ndulf.cobblemon.oneblock.config;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {
    private static ConfigLoader.Result parse(String text) {
        return ConfigLoader.parse("config.json5", new StringReader(text));
    }

    private static String full(String setup) {
        return """
                {
                  configVersion: 1,
                  setup: { %s },
                  grid: { bufferBlocks: 1000 },
                  storage: { busyTimeoutMs: 5000, queueCapacity: 1024, shutdownTimeoutSeconds: 30 },
                  logging: { debug: false }
                }
                """.formatted(setup);
    }

    private static final String DEFAULT_SETUP = "maxIslandSize: 2049, hubRadius: 512, maxMembers: 4, publicServer: true";

    @Test
    void generatedDefaultFileParsesToDefaultsWithoutWarnings() {
        ConfigLoader.Result result = parse(ConfigSchema.defaultFileContent());
        assertTrue(result.ok(), () -> result.errors().toString());
        assertEquals(List.of(), result.warnings());
        assertEquals(OneBlockConfig.defaults(), result.config());
    }

    @Test
    void defaultFileListsEveryField() {
        String content = ConfigSchema.defaultFileContent();
        for (ConfigSchema.Field f : ConfigSchema.FIELDS) {
            assertTrue(content.contains(ConfigSchema.keyOf(f) + ":"), f.path());
        }
    }

    @Test
    void commentsAndUnquotedKeysAreAccepted() {
        ConfigLoader.Result result = parse("// header\n" + full("/* block */ maxIslandSize: 3001, # hash comment\n hubRadius: 600, maxMembers: 2, publicServer: false"));
        assertTrue(result.ok(), () -> result.errors().toString());
        assertEquals(3001, result.config().maxIslandSize());
        assertEquals(600, result.config().hubRadius());
        assertFalse(result.config().publicServer());
    }

    @Test
    void unknownKeyIsAnErrorWithPositionAndSuggestion() {
        ConfigLoader.Result result = parse(full(DEFAULT_SETUP + ", maxislandsize: 2049"));
        assertFalse(result.ok());
        ConfigIssue issue = result.errors().get(0);
        assertEquals("setup.maxislandsize", issue.path());
        assertTrue(issue.message().contains("did you mean 'setup.maxIslandSize'"), issue.message());
        assertEquals(3, issue.line());
    }

    @Test
    void outOfRangeIsAnErrorAndNothingIsReturned() {
        ConfigLoader.Result result = parse(full("maxIslandSize: 2049, hubRadius: 512, maxMembers: 21, publicServer: true"));
        assertFalse(result.ok());
        assertEquals("setup.maxMembers", result.errors().get(0).path());
        assertTrue(result.errors().get(0).message().contains("between 1 and 20"));
        assertEquals(null, result.config());
    }

    @Test
    void evenIslandSizeIsRejectedWithHint() {
        ConfigLoader.Result result = parse(full("maxIslandSize: 2048, hubRadius: 512, maxMembers: 4, publicServer: true"));
        assertFalse(result.ok());
        assertTrue(result.errors().get(0).message().contains("use 2047 or 2049"), result.errors().toString());
    }

    @Test
    void hubRadiusMustFitTheStride() {
        // 2049 + 1000 -> stride 3072 -> max hub radius 1536 - 500 = 1036
        ConfigLoader.Result result = parse(full("maxIslandSize: 2049, hubRadius: 1037, maxMembers: 4, publicServer: true"));
        assertFalse(result.ok());
        assertEquals("setup.hubRadius", result.errors().get(0).path());
        assertTrue(result.errors().get(0).message().contains("at most 1036"));
        assertTrue(parse(full("maxIslandSize: 2049, hubRadius: 1036, maxMembers: 4, publicServer: true")).ok());
    }

    @Test
    void fractionsTypesAndHugeNumbersAreRejected() {
        assertFalse(parse(full("maxIslandSize: 2049.5, hubRadius: 512, maxMembers: 4, publicServer: true")).ok());
        assertFalse(parse(full("maxIslandSize: \"2049\", hubRadius: 512, maxMembers: 4, publicServer: true")).ok());
        assertFalse(parse(full("maxIslandSize: 2049, hubRadius: 512, maxMembers: 4, publicServer: 1")).ok());
        assertFalse(parse(full("maxIslandSize: 99999999999999999999, hubRadius: 512, maxMembers: 4, publicServer: true")).ok());
        assertTrue(parse(full("maxIslandSize: 2049.0, hubRadius: 512, maxMembers: 4, publicServer: true")).ok());
    }

    @Test
    void duplicateKeyIsAnError() {
        ConfigLoader.Result result = parse(full(DEFAULT_SETUP + ", maxMembers: 5"));
        assertFalse(result.ok());
        assertTrue(result.errors().get(0).message().contains("duplicate key 'maxMembers'"), result.errors().toString());
    }

    @Test
    void syntaxErrorReportsLine() {
        ConfigLoader.Result result = parse("{\n  configVersion: 1,\n  setup: { maxMembers: }\n}");
        assertFalse(result.ok());
        assertEquals(3, result.errors().get(0).line(), result.errors().toString());
    }

    @Test
    void trailingCommaIsReportedNotIgnored() {
        ConfigLoader.Result result = parse(full(DEFAULT_SETUP + ","));
        assertFalse(result.ok());
    }

    @Test
    void missingKeysAreWarningsWithDefaults() {
        ConfigLoader.Result result = parse("{ configVersion: 1, setup: { maxMembers: 6 } }");
        assertTrue(result.ok(), () -> result.errors().toString());
        assertEquals(6, result.config().maxMembers());
        assertEquals(2049, result.config().maxIslandSize());
        assertTrue(result.warnings().stream().anyMatch(w -> w.path().equals("setup.maxIslandSize")));
    }

    @Test
    void newerConfigVersionIsRejected() {
        ConfigLoader.Result result = parse("{ configVersion: 2 }");
        assertFalse(result.ok());
        assertTrue(result.errors().get(0).message().contains("understands 1-1"), result.errors().toString());
    }

    @Test
    void topLevelMustBeAnObjectAndEmptyFileFails() {
        assertFalse(parse("[1, 2]").ok());
        assertFalse(parse("").ok());
        assertFalse(parse("{ } trailing").ok());
    }
}
