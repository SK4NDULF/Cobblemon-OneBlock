package io.github.sk4ndulf.oneblock.core.db

/**
 * Simple key/value store for world-bound state (e.g. whether the hub platform
 * was already generated). Values are strings; callers convert as needed.
 */
class MetaRepository(private val database: Database) {

    /** Synchronous read — intended for startup paths only, never during gameplay. */
    fun get(key: String): String? = database.sync { connection ->
        connection.prepareStatement("SELECT meta_value FROM meta WHERE meta_key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { result ->
                if (result.next()) result.getString(1) else null
            }
        }
    }

    fun setAsync(key: String, value: String) {
        val sql = when (database.dialect) {
            SqlDialect.SQLITE ->
                "INSERT INTO meta (meta_key, meta_value) VALUES (?, ?) " +
                    "ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value"
            SqlDialect.MYSQL ->
                "INSERT INTO meta (meta_key, meta_value) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)"
        }
        database.async { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, key)
                statement.setString(2, value)
                statement.executeUpdate()
            }
        }
    }
}
