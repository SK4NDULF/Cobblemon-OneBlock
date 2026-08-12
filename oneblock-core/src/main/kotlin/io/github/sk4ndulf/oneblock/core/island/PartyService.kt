package io.github.sk4ndulf.oneblock.core.island

import com.mojang.authlib.GameProfile
import io.github.sk4ndulf.oneblock.api.event.PartyLeaveEvent
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.oneblock.core.world.HubManager
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Invite/accept/deny/kick/leave flow. Pure server-thread state; invites live in memory
 * (an invite not accepted before a restart simply has to be sent again).
 */
object PartyService {

    private data class Invite(val islandId: Long, val inviterName: String, val expiresAt: Long)

    /** invitee → pending invite (a newer invite replaces an older one). */
    private val invites = HashMap<UUID, Invite>()

    fun hasPendingInvite(player: UUID): Boolean =
        invites[player]?.let { it.expiresAt >= System.currentTimeMillis() } == true

    fun invite(owner: ServerPlayer, target: ServerPlayer) {
        val manager = OneBlockCore.islandManager ?: return
        val island = manager.islandDataOf(owner.uuid)
        if (island == null || island.owner() != owner.uuid) {
            owner.sendSystemMessage(ServerLang.msg(if (island == null) "oneblock.island.none" else "oneblock.island.only_owner"))
            return
        }
        if (target.uuid == owner.uuid) {
            owner.sendSystemMessage(ServerLang.msg("oneblock.party.invite_self"))
            return
        }
        if (manager.islandDataOf(target.uuid) != null) {
            owner.sendSystemMessage(ServerLang.msg("oneblock.party.target_has_island", target.gameProfile.name))
            return
        }
        val maxSize = OneBlockCore.configManager.mainConfig.maxPartySize
        if (island.memberSet.size + 1 >= maxSize) {
            owner.sendSystemMessage(ServerLang.msg("oneblock.party.full", maxSize))
            return
        }
        val timeoutSeconds = OneBlockCore.configManager.mainConfig.inviteTimeoutSeconds
        invites[target.uuid] = Invite(
            island.id, owner.gameProfile.name,
            System.currentTimeMillis() + timeoutSeconds * 1000L,
        )
        owner.sendSystemMessage(ServerLang.msg("oneblock.party.invited_sender", target.gameProfile.name, timeoutSeconds))
        target.sendSystemMessage(
            ServerLang.msg("oneblock.party.invited_target", owner.gameProfile.name)
                .withStyle(ChatFormatting.GOLD)
                .append(" ")
                .append(button("oneblock.party.button.accept", "/ob party accept", ChatFormatting.GREEN))
                .append(" ")
                .append(button("oneblock.party.button.decline", "/ob party deny", ChatFormatting.RED)),
        )
    }

    fun accept(player: ServerPlayer) {
        val manager = OneBlockCore.islandManager ?: return
        val invite = invites.remove(player.uuid)
        if (invite == null || invite.expiresAt < System.currentTimeMillis()) {
            player.sendSystemMessage(ServerLang.msg(if (invite == null) "oneblock.party.no_invite" else "oneblock.party.invite_expired"))
            return
        }
        val island = manager.islandDataById(invite.islandId)
        if (island == null || island.state != IslandState.ACTIVE) {
            player.sendSystemMessage(ServerLang.msg("oneblock.party.invite_expired"))
            return
        }
        if (manager.islandDataOf(player.uuid) != null) {
            player.sendSystemMessage(ServerLang.msg("oneblock.island.already_have"))
            return
        }
        val maxSize = OneBlockCore.configManager.mainConfig.maxPartySize
        if (island.memberSet.size + 1 >= maxSize) {
            player.sendSystemMessage(ServerLang.msg("oneblock.party.full", maxSize))
            return
        }
        manager.addMember(island, player.uuid)
        player.sendSystemMessage(
            ServerLang.msg("oneblock.party.joined_self", invite.inviterName).withStyle(ChatFormatting.GREEN),
        )
        broadcast(island, player.server, "oneblock.party.joined_broadcast", player.gameProfile.name, exclude = player.uuid)
    }

    fun deny(player: ServerPlayer) {
        val invite = invites.remove(player.uuid)
        if (invite == null) {
            player.sendSystemMessage(ServerLang.msg("oneblock.party.no_invite"))
            return
        }
        player.sendSystemMessage(ServerLang.msg("oneblock.party.denied"))
        player.server.playerList.players
            .firstOrNull { it.gameProfile.name == invite.inviterName }
            ?.sendSystemMessage(ServerLang.msg("oneblock.party.denied_owner", player.gameProfile.name))
    }

    fun kick(owner: ServerPlayer, target: GameProfile) {
        val manager = OneBlockCore.islandManager ?: return
        val island = manager.islandDataOf(owner.uuid)
        if (island == null || island.owner() != owner.uuid) {
            owner.sendSystemMessage(ServerLang.msg(if (island == null) "oneblock.island.none" else "oneblock.island.only_owner"))
            return
        }
        if (!manager.removeMember(island, target.id, PartyLeaveEvent.Reason.KICKED)) {
            owner.sendSystemMessage(ServerLang.msg("oneblock.party.not_member", target.name))
            return
        }
        owner.sendSystemMessage(ServerLang.msg("oneblock.party.kicked_owner", target.name))
        owner.server.playerList.getPlayer(target.id)?.let { online ->
            HubManager.sendToHub(online)
            online.sendSystemMessage(ServerLang.msg("oneblock.party.kicked_target", owner.gameProfile.name))
        }
    }

    fun leave(player: ServerPlayer) {
        val manager = OneBlockCore.islandManager ?: return
        val island = manager.islandDataOf(player.uuid)
        if (island == null) {
            player.sendSystemMessage(ServerLang.msg("oneblock.island.none"))
            return
        }
        if (island.owner() == player.uuid) {
            player.sendSystemMessage(ServerLang.msg("oneblock.party.owner_cant_leave"))
            return
        }
        manager.removeMember(island, player.uuid, PartyLeaveEvent.Reason.LEFT)
        HubManager.sendToHub(player)
        player.sendSystemMessage(ServerLang.msg("oneblock.party.left_self"))
        broadcast(island, player.server, "oneblock.party.left_broadcast", player.gameProfile.name, exclude = player.uuid)
    }

    fun list(player: ServerPlayer) {
        val manager = OneBlockCore.islandManager ?: return
        val island = manager.islandDataOf(player.uuid)
        if (island == null) {
            player.sendSystemMessage(ServerLang.msg("oneblock.island.none"))
            return
        }
        val server = player.server
        val maxSize = OneBlockCore.configManager.mainConfig.maxPartySize
        val ownerName = nameOf(server, island.owner())
        player.sendSystemMessage(
            ServerLang.msg("oneblock.party.list_header", ownerName, island.memberSet.size + 1, maxSize)
                .withStyle(ChatFormatting.GOLD),
        )
        player.sendSystemMessage(ServerLang.msg("oneblock.party.list_entry", ownerName, ServerLang.raw("oneblock.role.owner")))
        for (member in island.memberSet) {
            player.sendSystemMessage(
                ServerLang.msg("oneblock.party.list_entry", nameOf(server, member), ServerLang.raw("oneblock.role.member")),
            )
        }
    }

    private fun nameOf(server: net.minecraft.server.MinecraftServer, uuid: UUID): String =
        server.playerList.getPlayer(uuid)?.gameProfile?.name
            ?: server.profileCache?.get(uuid)?.map { it.name }?.orElse(null)
            ?: uuid.toString().substring(0, 8)

    private fun broadcast(
        island: IslandData,
        server: net.minecraft.server.MinecraftServer,
        langKey: String,
        vararg args: Any?,
        exclude: UUID? = null,
    ) {
        val recipients = island.memberSet + island.owner()
        for (uuid in recipients) {
            if (uuid == exclude) continue
            server.playerList.getPlayer(uuid)?.sendSystemMessage(ServerLang.msg(langKey, *args))
        }
    }

    private fun button(langKey: String, command: String, color: ChatFormatting): MutableComponent =
        ServerLang.msg(langKey).withStyle(color)
            .withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command)).withBold(true) }
}
