package io.github.sk4ndulf.oneblock.core.island

import io.github.sk4ndulf.oneblock.core.config.MainConfig
import io.github.sk4ndulf.oneblock.core.db.Database
import java.sql.Statement
import java.util.UUID

/** Persistence for islands. Reads happen at startup (sync); all writes are async. */
class IslandRepository(private val database: Database) {

    /** Synchronous full load — startup path only. */
    fun loadAll(config: MainConfig): List<IslandData> = database.sync { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, slot, owner_uuid, state, border_level, break_count, created_at, archived_at FROM islands",
            ).use { result ->
                val islands = ArrayList<IslandData>()
                while (result.next()) {
                    islands.add(
                        IslandData(
                            id = result.getLong("id"),
                            slot = result.getInt("slot"),
                            owner = UUID.fromString(result.getString("owner_uuid")),
                            state = IslandState.valueOf(result.getString("state")),
                            borderLevel = result.getInt("border_level"),
                            breakCount = result.getLong("break_count"),
                            createdAt = result.getLong("created_at"),
                            archivedAt = result.getLong("archived_at").takeIf { !result.wasNull() },
                            config = config,
                        ),
                    )
                }
                islands
            }
        }
    }

    /** Synchronous insert returning the generated id — runs during command handling, kept fast. */
    fun insert(slot: Int, owner: UUID, createdAt: Long): Long = database.sync { connection ->
        connection.prepareStatement(
            "INSERT INTO islands (slot, owner_uuid, state, border_level, break_count, created_at) " +
                "VALUES (?, ?, 'ACTIVE', 1, 0, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setInt(1, slot)
            statement.setString(2, owner.toString())
            statement.setLong(3, createdAt)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                check(keys.next()) { "No generated key for island insert" }
                keys.getLong(1)
            }
        }
    }

    fun updateStateAsync(id: Long, state: IslandState, archivedAt: Long?) {
        database.async { connection ->
            connection.prepareStatement("UPDATE islands SET state = ?, archived_at = ? WHERE id = ?").use {
                it.setString(1, state.name)
                if (archivedAt != null) it.setLong(2, archivedAt) else it.setNull(2, java.sql.Types.BIGINT)
                it.setLong(3, id)
                it.executeUpdate()
            }
        }
    }

    fun updateBreakCountAsync(id: Long, breakCount: Long) {
        database.async { connection ->
            connection.prepareStatement("UPDATE islands SET break_count = ? WHERE id = ?").use {
                it.setLong(1, breakCount)
                it.setLong(2, id)
                it.executeUpdate()
            }
        }
    }

    /** Owners whose last_seen is older than the cutoff — feeds the inactivity purge. */
    fun findInactiveOwnersAsync(cutoffMillis: Long): java.util.concurrent.CompletableFuture<Set<UUID>> =
        database.async { connection ->
            connection.prepareStatement(
                "SELECT p.uuid FROM players p JOIN islands i ON i.owner_uuid = p.uuid " +
                    "WHERE i.state = 'ACTIVE' AND p.last_seen < ?",
            ).use { statement ->
                statement.setLong(1, cutoffMillis)
                statement.executeQuery().use { result ->
                    val owners = HashSet<UUID>()
                    while (result.next()) owners.add(UUID.fromString(result.getString(1)))
                    owners
                }
            }
        }
}
