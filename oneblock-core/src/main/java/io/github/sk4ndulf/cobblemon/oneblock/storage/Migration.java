package io.github.sk4ndulf.cobblemon.oneblock.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * One versioned schema migration stored as a classpath SQL script. The checksum is computed over the script with
 * normalised line endings, so a checkout with CRLF line endings does not look like a modified migration.
 */
public record Migration(int version, String name, String resource) {
    public static final String RESOURCE_DIR = "/cobblemon_oneblock/db/migrations/";

    public String sql() {
        try (InputStream in = Migration.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("migration resource missing: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String checksum() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sql().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Splits a script into statements at semicolons that end a line. Line comments ({@code --}) are removed.
     * Our migrations never contain semicolons inside string literals or triggers; {@code MigrationsTest} checks that.
     */
    public static List<String> statements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : script.split("\n", -1)) {
            String line = stripComment(rawLine).stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            current.append(line).append('\n');
            if (line.endsWith(";")) {
                String statement = current.toString().strip();
                out.add(statement.substring(0, statement.length() - 1).strip());
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            throw new IllegalArgumentException("script does not end with ';': " + current.toString().strip());
        }
        return out;
    }

    private static String stripComment(String line) {
        int idx = line.indexOf("--");
        return idx < 0 ? line : line.substring(0, idx);
    }
}
