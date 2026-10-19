package io.github.sk4ndulf.cobblemon.oneblock.config;

import io.github.sk4ndulf.cobblemon.oneblock.domain.grid.GridGeometry;

import java.util.Map;

/** Validated, immutable config snapshot. Only {@link ConfigLoader} creates instances from files. */
public record OneBlockConfig(
        int configVersion,
        int maxIslandSize,
        int hubRadius,
        int maxMembers,
        boolean publicServer,
        int bufferBlocks,
        int dbBusyTimeoutMs,
        int dbQueueCapacity,
        int shutdownTimeoutSeconds,
        boolean debugLogging) {

    public static OneBlockConfig defaults() {
        return fromValues(Map.of());
    }

    /** Missing entries fall back to the schema default. Values must already be range-checked. */
    static OneBlockConfig fromValues(Map<String, Object> values) {
        return new OneBlockConfig(
                intValue(values, ConfigSchema.CONFIG_VERSION),
                intValue(values, ConfigSchema.MAX_ISLAND_SIZE),
                intValue(values, ConfigSchema.HUB_RADIUS),
                intValue(values, ConfigSchema.MAX_MEMBERS),
                boolValue(values, ConfigSchema.PUBLIC_SERVER),
                intValue(values, ConfigSchema.BUFFER_BLOCKS),
                intValue(values, ConfigSchema.DB_BUSY_TIMEOUT_MS),
                intValue(values, ConfigSchema.DB_QUEUE_CAPACITY),
                intValue(values, ConfigSchema.SHUTDOWN_TIMEOUT_SECONDS),
                boolValue(values, ConfigSchema.DEBUG_LOGGING));
    }

    /** Value of a schema field, for diffing two snapshots on reload. */
    public Object valueOf(ConfigSchema.Field field) {
        return switch (field.path()) {
            case "configVersion" -> configVersion;
            case "setup.maxIslandSize" -> maxIslandSize;
            case "setup.hubRadius" -> hubRadius;
            case "setup.maxMembers" -> maxMembers;
            case "setup.publicServer" -> publicServer;
            case "grid.bufferBlocks" -> bufferBlocks;
            case "storage.busyTimeoutMs" -> dbBusyTimeoutMs;
            case "storage.queueCapacity" -> dbQueueCapacity;
            case "storage.shutdownTimeoutSeconds" -> shutdownTimeoutSeconds;
            case "logging.debug" -> debugLogging;
            default -> throw new IllegalArgumentException("unknown field " + field.path());
        };
    }

    public GridGeometry grid() {
        return GridGeometry.of(maxIslandSize, bufferBlocks);
    }

    private static int intValue(Map<String, Object> values, ConfigSchema.Field field) {
        return ((Number) values.getOrDefault(field.path(), field.defaultValue())).intValue();
    }

    private static boolean boolValue(Map<String, Object> values, ConfigSchema.Field field) {
        return (Boolean) values.getOrDefault(field.path(), field.defaultValue());
    }
}
