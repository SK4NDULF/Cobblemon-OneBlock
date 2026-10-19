package io.github.sk4ndulf.cobblemon.oneblock.config;

import io.github.sk4ndulf.cobblemon.oneblock.util.AtomicFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns {@code config/oneblock/config.json5}.
 *
 * <ul>
 *   <li>First start: writes the commented default file; an existing file is never overwritten.</li>
 *   <li>Invalid file at boot: the mod keeps running on schema defaults but reports the config as invalid, so the
 *       bootstrap can refuse gameplay ({@code SETUP_PENDING}).</li>
 *   <li>Reload: validates the whole file first; on any error nothing changes. {@link ReloadClass#RESTART} values are
 *       kept until restart, a changed {@link ReloadClass#WORLD_LOCKED} value rejects the reload while the world is
 *       locked.</li>
 * </ul>
 */
public final class ConfigManager {
    public static final String FILE_NAME = "config.json5";
    private static final Logger LOG = LoggerFactory.getLogger("cobblemon_oneblock");

    /** Result of a load or reload. {@code applied} is false if nothing changed in memory. */
    public record Outcome(boolean applied, List<ConfigIssue> errors, List<ConfigIssue> warnings,
                          List<String> appliedFields, List<String> pendingRestartFields) {
        public Outcome {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
            appliedFields = List.copyOf(appliedFields);
            pendingRestartFields = List.copyOf(pendingRestartFields);
        }
    }

    private final Path file;
    private volatile OneBlockConfig current = OneBlockConfig.defaults();
    private volatile boolean valid;
    private volatile List<ConfigIssue> lastErrors = List.of();

    public ConfigManager(Path configDir) {
        this.file = configDir.resolve(FILE_NAME);
    }

    public Path file() {
        return file;
    }

    public OneBlockConfig current() {
        return current;
    }

    /** Whether {@link #current()} comes from a valid file (false: schema defaults after a failed boot load). */
    public boolean valid() {
        return valid;
    }

    public List<ConfigIssue> lastErrors() {
        return lastErrors;
    }

    /** Boot-time load. Never throws for a bad file; I/O problems while creating the default file are reported as errors. */
    public Outcome loadAtBoot() {
        if (!Files.exists(file)) {
            try {
                AtomicFiles.writeString(file, ConfigSchema.defaultFileContent());
                LOG.info("Created default config {}", file);
            } catch (IOException e) {
                ConfigIssue issue = new ConfigIssue(FILE_NAME, "", 0, 0, "cannot create default file: " + e.getMessage());
                valid = false;
                lastErrors = List.of(issue);
                return new Outcome(false, List.of(issue), List.of(), List.of(), List.of());
            }
        }
        ConfigLoader.Result result = ConfigLoader.load(file);
        if (!result.ok()) {
            valid = false;
            lastErrors = result.errors();
            result.errors().forEach(e -> LOG.error("Config error: {}", e));
            return new Outcome(false, result.errors(), result.warnings(), List.of(), List.of());
        }
        result.warnings().forEach(w -> LOG.warn("Config warning: {}", w));
        current = result.config();
        valid = true;
        lastErrors = List.of();
        return new Outcome(true, List.of(), result.warnings(), List.of(), List.of());
    }

    /** Reads and validates the config file without changing anything. Safe on any thread (does file I/O). */
    public ConfigLoader.Result read() {
        return ConfigLoader.load(file);
    }

    /**
     * Applies a result from {@link #read()} ({@code /ob reload}). Must be called on the server thread.
     *
     * @param worldLocked true once the first island exists; then WORLD_LOCKED values may not change
     */
    public Outcome apply(ConfigLoader.Result result, boolean worldLocked) {
        if (!result.ok()) {
            lastErrors = result.errors();
            return new Outcome(false, result.errors(), result.warnings(), List.of(), List.of());
        }
        OneBlockConfig loaded = result.config();
        OneBlockConfig base = current;
        List<ConfigIssue> errors = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (ConfigSchema.Field field : ConfigSchema.FIELDS) {
            if (Objects.equals(base.valueOf(field), loaded.valueOf(field))) {
                continue;
            }
            switch (field.reloadClass()) {
                case LIVE -> applied.add(field.path());
                case RESTART -> pending.add(field.path());
                case WORLD_LOCKED -> {
                    if (worldLocked) {
                        errors.add(new ConfigIssue(FILE_NAME, field.path(), 0, 0,
                                "cannot change after the first island exists (was " + base.valueOf(field) + ")"));
                    } else {
                        applied.add(field.path());
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            lastErrors = errors;
            return new Outcome(false, errors, result.warnings(), List.of(), List.of());
        }
        // RESTART fields keep their running value; everything else comes from the new file.
        OneBlockConfig merged = new OneBlockConfig(
                base.configVersion(),
                loaded.maxIslandSize(),
                loaded.hubRadius(),
                loaded.maxMembers(),
                loaded.publicServer(),
                loaded.bufferBlocks(),
                base.dbBusyTimeoutMs(),
                base.dbQueueCapacity(),
                base.shutdownTimeoutSeconds(),
                loaded.debugLogging());
        current = merged;
        valid = true;
        lastErrors = List.of();
        LOG.info("Config reloaded: applied {}, pending restart {}", applied, pending);
        return new Outcome(true, List.of(), result.warnings(), applied, pending);
    }
}
