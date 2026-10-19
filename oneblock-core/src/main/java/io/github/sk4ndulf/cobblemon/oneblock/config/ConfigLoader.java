package io.github.sk4ndulf.cobblemon.oneblock.config;

import io.github.sk4ndulf.cobblemon.oneblock.domain.grid.GridGeometry;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses and validates {@code config.json5}. Pure function of the file content: no global state, no side effects.
 *
 * <p>Policy: unknown keys, wrong types, out-of-range values and inconsistent combinations are errors. Missing keys
 * are warnings and use the schema default.
 */
public final class ConfigLoader {
    public record Result(OneBlockConfig config, List<ConfigIssue> errors, List<ConfigIssue> warnings) {
        public Result {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        public boolean ok() {
            return errors.isEmpty() && config != null;
        }
    }

    private ConfigLoader() {
    }

    public static Result load(Path file) {
        String name = file.getFileName().toString();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new Result(null, List.of(new ConfigIssue(name, "", 0, 0, "cannot read file: " + e.getMessage())), List.of());
        }
        return parse(name, new StringReader(content));
    }

    public static Result parse(String fileName, Reader reader) {
        List<ConfigIssue> errors = new ArrayList<>();
        List<ConfigIssue> warnings = new ArrayList<>();
        Json5Tree.Node root;
        try {
            root = Json5Tree.parse(reader);
        } catch (Json5Tree.ParseException e) {
            errors.add(new ConfigIssue(fileName, "", e.line(), e.column(), e.getMessage()));
            return new Result(null, errors, warnings);
        }
        if (!(root.value() instanceof Map<?, ?>)) {
            errors.add(new ConfigIssue(fileName, "", root.line(), root.column(), "top level must be an object { ... }"));
            return new Result(null, errors, warnings);
        }

        Map<String, Object> values = new HashMap<>();
        Map<String, Json5Tree.Node> seen = new HashMap<>();
        collect(fileName, "", root, seen, errors);

        for (ConfigSchema.Field field : ConfigSchema.FIELDS) {
            Json5Tree.Node node = seen.get(field.path());
            if (node == null) {
                warnings.add(new ConfigIssue(fileName, field.path(), 0, 0,
                        "missing, using default " + field.defaultValue()));
                continue;
            }
            Object value = convert(fileName, field, node, errors);
            if (value != null) {
                values.put(field.path(), value);
            }
        }

        if (!errors.isEmpty()) {
            return new Result(null, errors, warnings);
        }
        OneBlockConfig config = OneBlockConfig.fromValues(values);
        crossCheck(fileName, config, seen, errors);
        return new Result(errors.isEmpty() ? config : null, errors, warnings);
    }

    /** Flattens objects to dotted paths and reports keys that are not in the schema. */
    private static void collect(String fileName, String prefix, Json5Tree.Node node, Map<String, Json5Tree.Node> out,
                                List<ConfigIssue> errors) {
        @SuppressWarnings("unchecked")
        Map<String, Json5Tree.Node> map = (Map<String, Json5Tree.Node>) node.value();
        for (Map.Entry<String, Json5Tree.Node> entry : map.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Json5Tree.Node child = entry.getValue();
            if (ConfigSchema.BY_PATH.containsKey(path)) {
                out.put(path, child);
            } else if (child.value() instanceof Map<?, ?> && isSection(path)) {
                collect(fileName, path, child, out, errors);
            } else {
                errors.add(new ConfigIssue(fileName, path, child.line(), child.column(),
                        "unknown key" + suggestion(path)));
            }
        }
    }

    private static boolean isSection(String path) {
        return ConfigSchema.sections().contains(path);
    }

    private static String suggestion(String path) {
        String lower = path.toLowerCase();
        for (ConfigSchema.Field f : ConfigSchema.FIELDS) {
            if (f.path().equalsIgnoreCase(path) || f.path().toLowerCase().endsWith("." + lower)) {
                return " (did you mean '" + f.path() + "'?)";
            }
        }
        return "";
    }

    private static Object convert(String fileName, ConfigSchema.Field field, Json5Tree.Node node, List<ConfigIssue> errors) {
        Object raw = node.value();
        switch (field.type()) {
            case BOOLEAN -> {
                if (raw instanceof Boolean b) {
                    return b;
                }
                errors.add(issue(fileName, field, node, "must be true or false"));
                return null;
            }
            case INT -> {
                if (!(raw instanceof Json5Tree.NumberLiteral literal)) {
                    errors.add(issue(fileName, field, node, "must be a whole number"));
                    return null;
                }
                BigDecimal number;
                try {
                    number = new BigDecimal(literal.text());
                } catch (NumberFormatException e) {
                    errors.add(issue(fileName, field, node, "is not a number: " + literal.text()));
                    return null;
                }
                if (number.stripTrailingZeros().scale() > 0) {
                    errors.add(issue(fileName, field, node, "must be a whole number, got " + literal.text()));
                    return null;
                }
                if (number.compareTo(BigDecimal.valueOf(field.min())) < 0 || number.compareTo(BigDecimal.valueOf(field.max())) > 0) {
                    String message = field == ConfigSchema.CONFIG_VERSION
                            ? "is " + literal.text() + ", but this mod version understands " + field.min() + "-" + field.max()
                            : "must be between " + field.min() + " and " + field.max() + ", got " + literal.text();
                    errors.add(issue(fileName, field, node, message));
                    return null;
                }
                return number.intValueExact();
            }
            default -> throw new IllegalStateException(field.type().name());
        }
    }

    private static void crossCheck(String fileName, OneBlockConfig config, Map<String, Json5Tree.Node> seen,
                                   List<ConfigIssue> errors) {
        if ((config.maxIslandSize() & 1) == 0) {
            errors.add(issue(fileName, ConfigSchema.MAX_ISLAND_SIZE, seen.get(ConfigSchema.MAX_ISLAND_SIZE.path()),
                    "must be odd so the OneBlock sits exactly in the centre, got " + config.maxIslandSize()
                            + " (use " + (config.maxIslandSize() - 1) + " or " + (config.maxIslandSize() + 1) + ")"));
            return;
        }
        GridGeometry grid = config.grid();
        if (config.hubRadius() > grid.maxHubRadius()) {
            errors.add(issue(fileName, ConfigSchema.HUB_RADIUS, seen.get(ConfigSchema.HUB_RADIUS.path()),
                    "is " + config.hubRadius() + ", but with stride " + grid.stride() + " the hub radius may be at most "
                            + grid.maxHubRadius() + " (stride/2 - " + GridGeometry.HUB_SAFETY_MARGIN + ")"));
        }
    }

    private static ConfigIssue issue(String fileName, ConfigSchema.Field field, Json5Tree.Node node, String message) {
        int line = node == null ? 0 : node.line();
        int column = node == null ? 0 : node.column();
        return new ConfigIssue(fileName, field.path(), line, column, message);
    }
}
