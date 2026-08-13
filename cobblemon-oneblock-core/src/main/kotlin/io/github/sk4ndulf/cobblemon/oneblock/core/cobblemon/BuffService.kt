package io.github.sk4ndulf.cobblemon.oneblock.core.cobblemon

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import io.github.sk4ndulf.cobblemon.oneblock.core.db.SqlDialect

/**
 * Island buffs with restart persistence: state lives in memory for fast per-spawn lookups
 * and is written through to the DB, then reloaded at startup (expired rows are dropped).
 * Server-thread only.
 */
object BuffService {

    private val buffs = HashMap<Long, MutableMap<BuffType, IslandBuff>>()

    fun loadAll(database: Database) {
        buffs.clear()
        val now = System.currentTimeMillis()
        database.sync { connection ->
            connection.prepareStatement(
                "SELECT island_id, buff_type, value, expires_at FROM island_buffs WHERE expires_at > ?",
            ).use { statement ->
                statement.setLong(1, now)
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        val type = BuffType.parse(result.getString("buff_type")) ?: continue
                        val islandId = result.getLong("island_id")
                        buffs.getOrPut(islandId) { mutableMapOf() }[type] =
                            IslandBuff(type, result.getDouble("value"), result.getLong("expires_at"))
                    }
                }
            }
        }
        // Housekeeping: drop rows that expired while the server was down.
        database.async { connection ->
            connection.prepareStatement("DELETE FROM island_buffs WHERE expires_at <= ?").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
        }
        val count = buffs.values.sumOf { it.size }
        if (count > 0) OneBlockCore.LOGGER.info("Restored {} active island buffs.", count)
    }

    /** Grants (or replaces) a buff on the island for the given duration. */
    fun grant(islandId: Long, type: BuffType, value: Double, durationMillis: Long) {
        val expiresAt = System.currentTimeMillis() + durationMillis
        buffs.getOrPut(islandId) { mutableMapOf() }[type] = IslandBuff(type, value, expiresAt)

        val database = OneBlockCore.database ?: return
        val sql = when (database.dialect) {
            SqlDialect.SQLITE ->
                "INSERT INTO island_buffs (island_id, buff_type, value, expires_at) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(island_id, buff_type) DO UPDATE SET value = excluded.value, expires_at = excluded.expires_at"
            SqlDialect.MYSQL ->
                "INSERT INTO island_buffs (island_id, buff_type, value, expires_at) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE value = VALUES(value), expires_at = VALUES(expires_at)"
        }
        database.async { connection ->
            connection.prepareStatement(sql).use {
                it.setLong(1, islandId)
                it.setString(2, type.name)
                it.setDouble(3, value)
                it.setLong(4, expiresAt)
                it.executeUpdate()
            }
        }
    }

    /** The island's active buff of that type, or null when absent or expired. */
    fun active(islandId: Long, type: BuffType): IslandBuff? =
        buffs[islandId]?.get(type)?.takeIf { it.active }

    fun activeBuffs(islandId: Long): List<IslandBuff> =
        buffs[islandId]?.values?.filter { it.active } ?: emptyList()

    /** Drops expired entries from memory and the DB. Called by the hourly maintenance pass. */
    fun purgeExpired() {
        val now = System.currentTimeMillis()
        var removed = false
        for (islandBuffs in buffs.values) {
            if (islandBuffs.values.removeIf { it.expiresAtMillis <= now }) removed = true
        }
        buffs.values.removeIf { it.isEmpty() }
        if (!removed) return
        OneBlockCore.database?.async { connection ->
            connection.prepareStatement("DELETE FROM island_buffs WHERE expires_at <= ?").use {
                it.setLong(1, now)
                it.executeUpdate()
            }
        }
    }
}
