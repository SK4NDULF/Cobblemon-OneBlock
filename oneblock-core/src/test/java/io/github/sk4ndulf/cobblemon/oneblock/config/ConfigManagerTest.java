package io.github.sk4ndulf.cobblemon.oneblock.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    @TempDir
    Path dir;

    private Path file() {
        return dir.resolve(ConfigManager.FILE_NAME);
    }

    private void write(String content) throws IOException {
        Files.writeString(file(), content, StandardCharsets.UTF_8);
    }

    @Test
    void firstBootWritesDefaultFileAndLeavesNoTempFiles() throws IOException {
        ConfigManager manager = new ConfigManager(dir);
        ConfigManager.Outcome outcome = manager.loadAtBoot();
        assertTrue(outcome.applied());
        assertTrue(manager.valid());
        assertEquals(ConfigSchema.defaultFileContent(), Files.readString(file()));
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(List.of(file()), files.toList());
        }
    }

    @Test
    void invalidFileAtBootIsKeptAndDefaultsAreUsed() throws IOException {
        String broken = "{ configVersion: 1, setup: { maxMembers: 99 } }";
        write(broken);
        ConfigManager manager = new ConfigManager(dir);
        ConfigManager.Outcome outcome = manager.loadAtBoot();
        assertFalse(outcome.applied());
        assertFalse(manager.valid());
        assertEquals(OneBlockConfig.defaults(), manager.current());
        assertEquals(broken, Files.readString(file()), "an invalid file must never be overwritten");
        assertFalse(manager.lastErrors().isEmpty());
    }

    @Test
    void failedReloadKeepsTheLastValidVersion() throws IOException {
        ConfigManager manager = new ConfigManager(dir);
        manager.loadAtBoot();
        write(ConfigSchema.defaultFileContent().replace("maxMembers: 4", "maxMembers: 7"));
        assertTrue(manager.apply(manager.read(), false).applied());
        assertEquals(7, manager.current().maxMembers());

        write(ConfigSchema.defaultFileContent().replace("maxMembers: 4", "maxMembers: 8").replace("hubRadius: 512", "hubRadius: 5"));
        ConfigManager.Outcome outcome = manager.apply(manager.read(), false);
        assertFalse(outcome.applied());
        assertEquals(7, manager.current().maxMembers(), "no partial update");
        assertTrue(manager.valid());
    }

    @Test
    void restartFieldsAreValidatedButNotApplied() throws IOException {
        ConfigManager manager = new ConfigManager(dir);
        manager.loadAtBoot();
        write(ConfigSchema.defaultFileContent().replace("queueCapacity: 1024", "queueCapacity: 2048").replace("debug: false", "debug: true"));
        ConfigManager.Outcome outcome = manager.apply(manager.read(), false);
        assertTrue(outcome.applied());
        assertEquals(List.of("logging.debug"), outcome.appliedFields());
        assertEquals(List.of("storage.queueCapacity"), outcome.pendingRestartFields());
        assertEquals(1024, manager.current().dbQueueCapacity());
        assertTrue(manager.current().debugLogging());
    }

    @Test
    void lockedFieldsCannotChangeOnceTheWorldIsLocked() throws IOException {
        ConfigManager manager = new ConfigManager(dir);
        manager.loadAtBoot();
        write(ConfigSchema.defaultFileContent().replace("maxIslandSize: 2049", "maxIslandSize: 3001").replace("maxMembers: 4", "maxMembers: 5"));
        ConfigManager.Outcome locked = manager.apply(manager.read(), true);
        assertFalse(locked.applied());
        assertEquals("setup.maxIslandSize", locked.errors().get(0).path());
        assertEquals(4, manager.current().maxMembers(), "nothing applied when a locked field changed");

        ConfigManager.Outcome unlocked = manager.apply(manager.read(), false);
        assertTrue(unlocked.applied());
        assertEquals(3001, manager.current().maxIslandSize());
    }

    @Test
    void validReloadAfterInvalidBootMakesConfigValid() throws IOException {
        write("{ configVersion: 1, setup: { maxMembers: 99 } }");
        ConfigManager manager = new ConfigManager(dir);
        manager.loadAtBoot();
        assertFalse(manager.valid());
        write(ConfigSchema.defaultFileContent());
        assertTrue(manager.apply(manager.read(), false).applied());
        assertTrue(manager.valid());
    }
}
