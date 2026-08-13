package io.github.sk4ndulf.cobblemon.oneblock.core.progression

import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import io.github.sk4ndulf.cobblemon.oneblock.core.db.SqlDialect
import java.util.UUID

/**
 * Persistence for the tech tree. Reads happen at startup (sync); all writes are async,
 * matching [io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandRepository].
 *
 * The one exception is [claimSync]: a point claim is triggered by a battle or an advancement
 * and must not be able to double-fire while an async write is still in flight, so the insert
 * is synchronous and the database's primary key is the thing that decides who won. See
 * [TechPointService] for why that matters.
 */
class TechRepository(private val database: Database) {

    /** Full load of every island's tech state — startup path only. */
    fun loadAll(): Map<Long, IslandTechState> = database.sync { connection ->
        val states = HashMap<Long, IslandTechState>()

        fun state(islandId: Long) = states.getOrPut(islandId) { IslandTechState(islandId) }

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, tech_points, tech_points_earned FROM islands").use { result ->
                while (result.next()) {
                    state(result.getLong("id")).apply {
                        balance = result.getLong("tech_points")
                        lifetimeEarned = result.getLong("tech_points_earned")
                    }
                }
            }
            statement.executeQuery("SELECT island_id, node_id, level FROM island_tech").use { result ->
                while (result.next()) {
                    val level = result.getInt("level")
                    if (level > 0) state(result.getLong("island_id")).levels[result.getString("node_id")] = level
                }
            }
            statement.executeQuery("SELECT island_id, source_id FROM island_claims").use { result ->
                while (result.next()) {
                    state(result.getLong("island_id")).claims += result.getString("source_id")
                }
            }
            statement.executeQuery(
                "SELECT island_id, member_uuid FROM island_members WHERE may_spend_tech <> 0",
            ).use { result ->
                while (result.next()) {
                    state(result.getLong("island_id")).spenders += UUID.fromString(result.getString("member_uuid"))
                }
            }
        }
        states
    }

    fun updatePointsAsync(islandId: Long, balance: Long, lifetimeEarned: Long) {
        database.async { connection ->
            connection.prepareStatement(
                "UPDATE islands SET tech_points = ?, tech_points_earned = ? WHERE id = ?",
            ).use {
                it.setLong(1, balance)
                it.setLong(2, lifetimeEarned)
                it.setLong(3, islandId)
                it.executeUpdate()
            }
        }
    }

    fun upsertNodeAsync(islandId: Long, nodeId: String, level: Int) {
        val sql = when (database.dialect) {
            SqlDialect.SQLITE ->
                "INSERT INTO island_tech (island_id, node_id, level, unlocked_at) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(island_id, node_id) DO UPDATE SET level = excluded.level"
            SqlDialect.MYSQL ->
                "INSERT INTO island_tech (island_id, node_id, level, unlocked_at) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE level = VALUES(level)"
        }
        database.async { connection ->
            connection.prepareStatement(sql).use {
                it.setLong(1, islandId)
                it.setString(2, nodeId)
                it.setInt(3, level)
                it.setLong(4, System.currentTimeMillis())
                it.executeUpdate()
            }
        }
    }

    /**
     * Inserts a claim and reports whether this call was the one that created it.
     *
     * Synchronous and relying on the primary key rather than on a prior SELECT: two members
     * of the same island finishing the same gym battle in the same tick would both pass an
     * in-memory check, and the island would bank the points twice. Letting the unique
     * constraint arbitrate is the only version that cannot race.
     */
    fun claimSync(islandId: Long, sourceId: String, claimedBy: UUID): Boolean = database.sync { connection ->
        val sql = when (database.dialect) {
            SqlDialect.SQLITE ->
                "INSERT OR IGNORE INTO island_claims (island_id, source_id, claimed_by, claimed_at) VALUES (?, ?, ?, ?)"
            SqlDialect.MYSQL ->
                "INSERT IGNORE INTO island_claims (island_id, source_id, claimed_by, claimed_at) VALUES (?, ?, ?, ?)"
        }
        connection.prepareStatement(sql).use {
            it.setLong(1, islandId)
            it.setString(2, sourceId)
            it.setString(3, claimedBy.toString())
            it.setLong(4, System.currentTimeMillis())
            it.executeUpdate() > 0
        }
    }

    fun setSpenderAsync(islandId: Long, member: UUID, allowed: Boolean) {
        database.async { connection ->
            connection.prepareStatement(
                "UPDATE island_members SET may_spend_tech = ? WHERE island_id = ? AND member_uuid = ?",
            ).use {
                it.setInt(1, if (allowed) 1 else 0)
                it.setLong(2, islandId)
                it.setString(3, member.toString())
                it.executeUpdate()
            }
        }
    }

    /**
     * Wipes an island's tech progress. Called from the island reset and delete paths, so a
     * fresh island can claim every source again (PROGRESSION_REWORK.md §4.3).
     */
    fun clearAsync(islandId: Long) {
        database.async { connection ->
            for (sql in listOf(
                "DELETE FROM island_tech WHERE island_id = ?",
                "DELETE FROM island_claims WHERE island_id = ?",
            )) {
                connection.prepareStatement(sql).use {
                    it.setLong(1, islandId)
                    it.executeUpdate()
                }
            }
            connection.prepareStatement(
                "UPDATE islands SET tech_points = 0, tech_points_earned = 0 WHERE id = ?",
            ).use {
                it.setLong(1, islandId)
                it.executeUpdate()
            }
        }
    }
}
