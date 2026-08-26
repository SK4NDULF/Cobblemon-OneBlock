package io.github.sk4ndulf.cobblemon.oneblock.core.moderation

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.db.Database
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubManager
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.server.MinecraftServer
import java.util.UUID

/**
 * Per-island visitor bans. A banned player is bounced back to the hub whenever they
 * set foot on the island; owners and members can never be banned from their own island.
 */
object BanService {

    data class Ban(val bannedBy: UUID?, val reason: String, val createdAt: Long)

    private val bans = HashMap<Long, MutableMap<UUID, Ban>>()
    private var database: Database? = null

    fun reload(database: Database) {
        this.database = database
        bans.clear()
        database.sync { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT island_id, banned_uuid, banned_by, reason, created_at FROM island_bans",
                ).use { result ->
                    while (result.next()) {
                        val islandId = result.getLong("island_id")
                        val banned = UUID.fromString(result.getString("banned_uuid"))
                        val by = result.getString("banned_by")?.let(UUID::fromString)
                        bans.getOrPut(islandId) { mutableMapOf() }[banned] =
                            Ban(by, result.getString("reason"), result.getLong("created_at"))
                    }
                }
            }
        }
        val count = bans.values.sumOf { it.size }
        if (count > 0) OneBlockCore.LOGGER.info("Loaded {} island bans.", count)
    }

    fun isBanned(islandId: Long, player: UUID): Boolean = bans[islandId]?.containsKey(player) == true

    fun bansOf(islandId: Long): Map<UUID, Ban> = bans[islandId] ?: emptyMap()

    /** Returns false when the target is the owner or a member (those can't be banned). */
    fun ban(island: IslandData, target: UUID, bannedBy: UUID?, reason: String): Boolean {
        if (island.isMemberOrOwner(target)) return false
        val now = System.currentTimeMillis()
        bans.getOrPut(island.id) { mutableMapOf() }[target] = Ban(bannedBy, reason, now)

        database?.async { connection ->
            connection.prepareStatement(
                "INSERT INTO island_bans (island_id, banned_uuid, banned_by, reason, created_at) " +
                    "VALUES (?, ?, ?, ?, ?)",
            ).use {
                it.setLong(1, island.id)
                it.setString(2, target.toString())
                it.setString(3, bannedBy?.toString())
                it.setString(4, reason)
                it.setLong(5, now)
                it.executeUpdate()
            }
        }
        return true
    }

    fun unban(islandId: Long, target: UUID): Boolean {
        val removed = bans[islandId]?.remove(target) != null
        if (!removed) return false
        database?.async { connection ->
            connection.prepareStatement("DELETE FROM island_bans WHERE island_id = ? AND banned_uuid = ?").use {
                it.setLong(1, islandId)
                it.setString(2, target.toString())
                it.executeUpdate()
            }
        }
        return true
    }

    fun clearIsland(islandId: Long) {
        if (bans.remove(islandId) == null) return
        database?.async { connection ->
            connection.prepareStatement("DELETE FROM island_bans WHERE island_id = ?").use {
                it.setLong(1, islandId)
                it.executeUpdate()
            }
        }
    }

    /**
     * Bounces banned players off islands. Runs on a slow interval — a banned player
     * being on the island for up to a second is harmless, and this keeps it cheap.
     */
    fun enforce(server: MinecraftServer) {
        val manager = OneBlockCore.islandManager ?: return
        if (bans.isEmpty()) return
        for (player in server.playerList.players) {
            if (!OneBlockDimension.isOurs(player.level())) continue
            val island = manager.islandAt(player.blockPosition()) ?: continue
            if (!isBanned(island.id, player.uuid)) continue

            HubManager.sendToHub(player)
            player.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.ban.bounced"))
        }
    }
}
