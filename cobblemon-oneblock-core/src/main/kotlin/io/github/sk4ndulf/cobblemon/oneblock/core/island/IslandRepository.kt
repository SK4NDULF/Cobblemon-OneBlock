package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.core.config.MainConfig
import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import java.sql.Statement
import java.util.UUID

/** Persistence for islands. Reads happen at startup (sync); all writes are async. */
class IslandRepository(private val database: Database) {

    /** Synchronous full load including memberships — startup path only. */
    fun loadAll(config: MainConfig): List<IslandData> = database.sync { connection ->
        val islands = ArrayList<IslandData>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT id, slot, owner_uuid, state, border_level, break_count, points, created_at, archived_at, " +
                    "allow_visitor_catch, allow_visitor_battle FROM islands",
            ).use { result ->
                while (result.next()) {
                    islands.add(
                        IslandData(
                            id = result.getLong("id"),
                            slot = result.getInt("slot"),
                            owner = UUID.fromString(result.getString("owner_uuid")),
                            state = IslandState.valueOf(result.getString("state")),
                            borderLevel = result.getInt("border_level"),
                            breakCount = result.getLong("break_count"),
                            points = result.getDouble("points"),
                            createdAt = result.getLong("created_at"),
                            archivedAt = result.getLong("archived_at").takeIf { !result.wasNull() },
                            allowVisitorCatch = result.getInt("allow_visitor_catch") != 0,
                            allowVisitorBattle = result.getInt("allow_visitor_battle") != 0,
                            config = config,
                        ),
                    )
                }
            }
        }
        val byId = islands.associateBy { it.id }
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT island_id, member_uuid FROM island_members").use { result ->
                while (result.next()) {
                    byId[result.getLong("island_id")]?.memberSet?.add(UUID.fromString(result.getString("member_uuid")))
                }
            }
        }
        islands
    }

    fun updateVisitorSettingsAsync(id: Long, allowCatch: Boolean, allowBattle: Boolean) {
        database.async { connection ->
            connection.prepareStatement(
                "UPDATE islands SET allow_visitor_catch = ?, allow_visitor_battle = ? WHERE id = ?",
            ).use {
                it.setInt(1, if (allowCatch) 1 else 0)
                it.setInt(2, if (allowBattle) 1 else 0)
                it.setLong(3, id)
                it.executeUpdate()
            }
        }
    }

    fun insertMemberAsync(islandId: Long, member: UUID) {
        database.async { connection ->
            connection.prepareStatement(
                "INSERT INTO island_members (island_id, member_uuid, added_at) VALUES (?, ?, ?)",
            ).use {
                it.setLong(1, islandId)
                it.setString(2, member.toString())
                it.setLong(3, System.currentTimeMillis())
                it.executeUpdate()
            }
        }
    }

    fun deleteMemberAsync(islandId: Long, member: UUID) {
        database.async { connection ->
            connection.prepareStatement(
                "DELETE FROM island_members WHERE island_id = ? AND member_uuid = ?",
            ).use {
                it.setLong(1, islandId)
                it.setString(2, member.toString())
                it.executeUpdate()
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

    fun updateProgressAsync(id: Long, breakCount: Long, points: Double, borderLevel: Int) {
        database.async { connection ->
            connection.prepareStatement(
                "UPDATE islands SET break_count = ?, points = ?, border_level = ? WHERE id = ?",
            ).use {
                it.setLong(1, breakCount)
                it.setDouble(2, points)
                it.setInt(3, borderLevel)
                it.setLong(4, id)
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
