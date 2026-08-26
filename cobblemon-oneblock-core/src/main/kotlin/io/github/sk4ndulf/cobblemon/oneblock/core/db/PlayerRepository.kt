package io.github.sk4ndulf.cobblemon.oneblock.core.db

import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Persists player identity and last-seen timestamps.
 * `last_seen` feeds the island inactivity purge, so it is tracked from day one.
 */
class PlayerRepository(private val database: Database) {

    /**
     * Upserts the player row and reports whether this was the player's very first join
     * (used to teleport new players to the hub).
     */
    fun recordSeenAsync(uuid: UUID, name: String): CompletableFuture<Boolean> {
        val now = System.currentTimeMillis()
        val upsert = when (database.dialect) {
            SqlDialect.SQLITE ->
                "INSERT INTO players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen"
            SqlDialect.MYSQL ->
                "INSERT INTO players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE name = VALUES(name), last_seen = VALUES(last_seen)"
        }
        return database.async { connection ->
            val known = connection.prepareStatement("SELECT 1 FROM players WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.executeQuery().use { it.next() }
            }
            connection.prepareStatement(upsert).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, name)
                statement.setLong(3, now)
                statement.setLong(4, now)
                statement.executeUpdate()
            }
            !known
        }
    }
}
