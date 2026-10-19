package io.github.sk4ndulf.cobblemon.oneblock.config;

/**
 * One problem in a config file. {@code line} and {@code column} are 1-based, or 0 if unknown.
 */
public record ConfigIssue(String file, String path, int line, int column, String message) {
    @Override
    public String toString() {
        String where = line > 0 ? file + ":" + line + ":" + column : file;
        return where + " " + (path.isEmpty() ? "" : "'" + path + "' ") + message;
    }
}
