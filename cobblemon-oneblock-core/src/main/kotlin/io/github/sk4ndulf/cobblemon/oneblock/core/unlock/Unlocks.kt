package io.github.sk4ndulf.cobblemon.oneblock.core.unlock

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import io.github.sk4ndulf.cobblemon.oneblock.core.db.SqlDialect
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * What a player has earned the right to do.
 *
 * Today the only thing gated is hub travel, and the only way to grant an unlock is
 * `/ob admin unlock grant`. That is on purpose: the condition is meant to be a quest, the
 * quest system does not exist yet, and an unlock that is silently handed out for free would
 * be a gate in name only. When quests arrive they call [grant] and nothing else changes.
 *
 * Unlocks are per player, not per island: this answers "may *you* go there", and a party
 * member who joins an island has not been anywhere.
 *
 * In memory for every player the database knows, not just the online ones — an admin grants
 * unlocks to offline players, and the whole set is a few hundred rows on a busy server.
 * Server-thread only.
 */
object Unlocks {

    /** Travel to the Nether hub. */
    const val HUB_NETHER = "hub.nether"

    /** Travel to the End hub. */
    const val HUB_END = "hub.end"

    /** Every unlock the mod itself checks. Addons and quests may use their own keys. */
    val KNOWN: List<String> = listOf(HUB_NETHER, HUB_END)

    private val granted = HashMap<UUID, MutableSet<String>>()
    private var database: Database? = null

    /**
     * The unlock a hub requires, or null when it is open to everyone.
     *
     * The Overworld hub is the world spawn and the place the mod drops players who have
     * nowhere else to be, so gating it could strand somebody.
     */
    fun hubUnlockFor(dimension: ResourceKey<Level>): String? = when (dimension) {
        OneBlockDimension.NETHER_KEY -> HUB_NETHER
        OneBlockDimension.END_KEY -> HUB_END
        else -> null
    }

    fun reload(database: Database) {
        this.database = database
        granted.clear()
        database.sync { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT uuid, unlock_key FROM player_unlocks").use { result ->
                    while (result.next()) {
                        val uuid = UUID.fromString(result.getString("uuid"))
                        granted.getOrPut(uuid) { mutableSetOf() }.add(result.getString("unlock_key"))
                    }
                }
            }
        }
        val count = granted.values.sumOf { it.size }
        if (count > 0) OneBlockCore.LOGGER.info("Loaded {} player unlocks.", count)
    }

    fun has(player: UUID, key: String): Boolean = granted[player]?.contains(key) == true

    fun keysOf(player: UUID): Set<String> = granted[player]?.toSortedSet() ?: emptySet()

    /** Returns false when the player already had it. */
    fun grant(player: UUID, key: String): Boolean {
        if (!granted.getOrPut(player) { mutableSetOf() }.add(key)) return false
        val sql = when (database?.dialect) {
            SqlDialect.MYSQL ->
                "INSERT INTO player_unlocks (uuid, unlock_key, granted_at) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE granted_at = granted_at"
            // Both dialects need the conflict clause: the in-memory set says the player does
            // not have it, but a row can survive a revoke that failed to reach the database.
            else ->
                "INSERT INTO player_unlocks (uuid, unlock_key, granted_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid, unlock_key) DO NOTHING"
        }
        database?.async { connection ->
            connection.prepareStatement(sql).use {
                it.setString(1, player.toString())
                it.setString(2, key)
                it.setLong(3, System.currentTimeMillis())
                it.executeUpdate()
            }
        }
        return true
    }

    /** Returns false when the player did not have it. */
    fun revoke(player: UUID, key: String): Boolean {
        val keys = granted[player] ?: return false
        if (!keys.remove(key)) return false
        if (keys.isEmpty()) granted.remove(player)
        database?.async { connection ->
            connection.prepareStatement("DELETE FROM player_unlocks WHERE uuid = ? AND unlock_key = ?").use {
                it.setString(1, player.toString())
                it.setString(2, key)
                it.executeUpdate()
            }
        }
        return true
    }
}
