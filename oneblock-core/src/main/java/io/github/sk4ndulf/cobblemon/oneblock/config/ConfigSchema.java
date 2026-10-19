package io.github.sk4ndulf.cobblemon.oneblock.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every option of {@code config.json5}: type, default, range, reload class and meaning. The default file and
 * {@code docs/CONFIG.md} are derived from this list, so there are no magic numbers elsewhere in the code.
 */
public final class ConfigSchema {
    public static final int CURRENT_VERSION = 1;

    public enum Type {
        INT,
        BOOLEAN
    }

    /** {@code min}/{@code max} are only used for {@link Type#INT}. */
    public record Field(String path, Type type, Object defaultValue, long min, long max, ReloadClass reloadClass,
                        String description) {
    }

    public static final Field CONFIG_VERSION = new Field("configVersion", Type.INT, CURRENT_VERSION, 1, CURRENT_VERSION,
            ReloadClass.RESTART, "Format version of this file. Do not change by hand.");

    public static final Field MAX_ISLAND_SIZE = new Field("setup.maxIslandSize", Type.INT, 2049, 501, 10001,
            ReloadClass.WORLD_LOCKED, "Edge length of a level-8 island in blocks. Must be odd.");
    public static final Field HUB_RADIUS = new Field("setup.hubRadius", Type.INT, 512, 100, 10000,
            ReloadClass.LIVE, "Protection radius of the hub. At most stride/2 - 500.");
    public static final Field MAX_MEMBERS = new Field("setup.maxMembers", Type.INT, 4, 1, 20,
            ReloadClass.LIVE, "Maximum members per island, including the owner.");
    public static final Field PUBLIC_SERVER = new Field("setup.publicServer", Type.BOOLEAN, true, 0, 0,
            ReloadClass.LIVE, "Public server: stricter visitor defaults.");

    public static final Field BUFFER_BLOCKS = new Field("grid.bufferBlocks", Type.INT, 1000, 512, 10000,
            ReloadClass.WORLD_LOCKED, "Free space between neighbouring islands, added to maxIslandSize before rounding the stride up to 1024.");

    public static final Field DB_BUSY_TIMEOUT_MS = new Field("storage.busyTimeoutMs", Type.INT, 5000, 1000, 60000,
            ReloadClass.RESTART, "SQLite busy timeout in milliseconds.");
    public static final Field DB_QUEUE_CAPACITY = new Field("storage.queueCapacity", Type.INT, 1024, 16, 65536,
            ReloadClass.RESTART, "Maximum queued database tasks. When full, new writes fail visibly instead of piling up.");
    public static final Field SHUTDOWN_TIMEOUT_SECONDS = new Field("storage.shutdownTimeoutSeconds", Type.INT, 30, 5, 300,
            ReloadClass.RESTART, "How long shutdown waits for queued database tasks.");

    public static final Field DEBUG_LOGGING = new Field("logging.debug", Type.BOOLEAN, false, 0, 0,
            ReloadClass.LIVE, "Extra diagnostic log output.");

    public static final List<Field> FIELDS = List.of(
            CONFIG_VERSION,
            MAX_ISLAND_SIZE, HUB_RADIUS, MAX_MEMBERS, PUBLIC_SERVER,
            BUFFER_BLOCKS,
            DB_BUSY_TIMEOUT_MS, DB_QUEUE_CAPACITY, SHUTDOWN_TIMEOUT_SECONDS,
            DEBUG_LOGGING);

    public static final Map<String, Field> BY_PATH = FIELDS.stream()
            .collect(Collectors.toUnmodifiableMap(Field::path, Function.identity()));

    private ConfigSchema() {
    }

    /** Sections in file order; the top-level fields have the section "". */
    public static List<String> sections() {
        return FIELDS.stream().map(ConfigSchema::sectionOf).distinct().toList();
    }

    public static String sectionOf(Field field) {
        int dot = field.path().indexOf('.');
        return dot < 0 ? "" : field.path().substring(0, dot);
    }

    public static String keyOf(Field field) {
        int dot = field.path().indexOf('.');
        return dot < 0 ? field.path() : field.path().substring(dot + 1);
    }

    /** The commented default file written on first start. */
    public static String defaultFileContent() {
        StringBuilder out = new StringBuilder();
        out.append("// Cobblemon OneBlock configuration.\n");
        out.append("// Format: JSON5 subset. Comments and unquoted keys are allowed, trailing commas are not.\n");
        out.append("// Apply changes with /ob reload. [restart] = needs a server restart,\n");
        out.append("// [locked] = cannot be changed after the first island exists.\n");
        out.append("{\n");
        List<String> sections = sections();
        for (int s = 0; s < sections.size(); s++) {
            String section = sections.get(s);
            List<Field> fields = FIELDS.stream().filter(f -> sectionOf(f).equals(section)).toList();
            String indent = section.isEmpty() ? "  " : "    ";
            if (!section.isEmpty()) {
                out.append("  ").append(section).append(": {\n");
            }
            for (int i = 0; i < fields.size(); i++) {
                Field f = fields.get(i);
                out.append(indent).append("// ").append(f.description()).append(rangeNote(f)).append(classNote(f)).append('\n');
                out.append(indent).append(keyOf(f)).append(": ").append(f.defaultValue());
                out.append(i < fields.size() - 1 ? ",\n" : "\n");
            }
            if (!section.isEmpty()) {
                out.append("  }");
                out.append(s < sections.size() - 1 ? ",\n" : "\n");
            } else if (s < sections.size() - 1) {
                // close the last top-level field line with a comma before the next section
                out.setLength(out.length() - 1);
                out.append(",\n");
            }
        }
        out.append("}\n");
        return out.toString();
    }

    private static String rangeNote(Field f) {
        return f.type() == Type.INT && f != CONFIG_VERSION ? " Range " + f.min() + "-" + f.max() + "." : "";
    }

    private static String classNote(Field f) {
        return switch (f.reloadClass()) {
            case LIVE -> "";
            case RESTART -> " [restart]";
            case WORLD_LOCKED -> " [locked]";
        };
    }
}
