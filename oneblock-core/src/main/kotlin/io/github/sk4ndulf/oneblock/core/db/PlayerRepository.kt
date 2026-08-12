package io.github.sk4ndulf.oneblock.core.db

import java.util.UUID

/**
 * Persists player identity and last-seen timestamps.
 * `last_seen` feeds the island inactivity purge, so it is tracked from day one.
 */
class PlayerRepository(private val database: Database) {

    fun recordSeenAsync(uuid: UUID, name: String) {
        val now = System.currentTimeMillis()
        val sql = when (database.dialect) {
            SqlDialect.SQLITE ->
                "INSERT INTO players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen"
            SqlDialect.MYSQL ->
                "INSERT INTO players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)"
        }
        database.async { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, name)
                statement.setLong(3, now)
                statement.setLong(4, now)
                statement.executeUpdate()
            }
        }
    }
}
