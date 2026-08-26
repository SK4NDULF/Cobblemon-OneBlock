package io.github.sk4ndulf.cobblemon.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.moderation.AuditLog
import io.github.sk4ndulf.cobblemon.oneblock.core.moderation.BanService
import io.github.sk4ndulf.cobblemon.oneblock.core.world.GridMath
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubManager
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.network.chat.ClickEvent
import java.util.UUID

/**
 * Island bans (owner-facing) and the admin toolbox. Kept in its own file so the
 * main `/ob` tree stays readable.
 */
object ModerationCommands {

    // --- owner-facing bans ------------------------------------------------------------------

    fun banBranch(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("ban")
            .requires { ObPermissions.check(it, ObPermissions.COMMAND_BAN, 0) }
            .then(
                Commands.argument("player", GameProfileArgument.gameProfile())
                    .executes { ban(it, null) }
                    .then(
                        Commands.argument("reason", StringArgumentType.greedyString())
                            .executes { ban(it, StringArgumentType.getString(it, "reason")) }
                    )
            )

    fun unbanBranch(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("unban")
            .requires { ObPermissions.check(it, ObPermissions.COMMAND_BAN, 0) }
            .then(
                Commands.argument("player", GameProfileArgument.gameProfile())
                    .executes(::unban)
            )

    fun bansBranch(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("bans")
            .requires { ObPermissions.check(it, ObPermissions.COMMAND_BAN, 0) }
            .executes(::listBans)

    private fun ownedIsland(context: CommandContext<CommandSourceStack>): IslandData? {
        val player = context.source.playerOrException
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return null
        }
        if (island.owner() != player.uuid && !player.hasPermissions(2)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.only_owner"))
            return null
        }
        return island
    }

    private fun ban(context: CommandContext<CommandSourceStack>, reason: String?): Int {
        val island = ownedIsland(context) ?: return 0
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return 0
        val actor = context.source.player

        val effectiveReason = reason ?: ServerLang.raw("cobblemon_oneblock.ban.no_reason")
        if (!BanService.ban(island, profile.id, actor?.uuid, effectiveReason)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.ban.cannot_ban_member", profile.name))
            return 0
        }
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.ban.banned", profile.name).withStyle(ChatFormatting.GREEN) }, false,
        )
        context.source.server.playerList.getPlayer(profile.id)?.let { online ->
            if (OneBlockCore.islandManager?.islandAt(online.blockPosition()) === island) {
                HubManager.sendToHub(online)
            }
            online.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.ban.you_were_banned", effectiveReason))
        }
        AuditLog.record("island.ban", context.source, profile.id, "island=${island.id} reason=$effectiveReason")
        return Command.SINGLE_SUCCESS
    }

    private fun unban(context: CommandContext<CommandSourceStack>): Int {
        val island = ownedIsland(context) ?: return 0
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return 0
        if (!BanService.unban(island.id, profile.id)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.ban.not_banned", profile.name))
            return 0
        }
        context.source.sendSuccess({ ServerLang.msg("cobblemon_oneblock.ban.unbanned", profile.name) }, false)
        AuditLog.record("island.unban", context.source, profile.id, "island=${island.id}")
        return Command.SINGLE_SUCCESS
    }

    private fun listBans(context: CommandContext<CommandSourceStack>): Int {
        val island = ownedIsland(context) ?: return 0
        val entries = BanService.bansOf(island.id)
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.ban.list_header", entries.size).withStyle(ChatFormatting.GOLD),
        )
        for ((uuid, ban) in entries) {
            context.source.sendSystemMessage(
                ServerLang.msg("cobblemon_oneblock.ban.list_entry", nameOf(context, uuid), ban.reason),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun nameOf(context: CommandContext<CommandSourceStack>, uuid: UUID): String {
        val server = context.source.server
        return server.playerList.getPlayer(uuid)?.gameProfile?.name
            ?: server.profileCache?.get(uuid)?.map { it.name }?.orElse(null)
            ?: uuid.toString().substring(0, 8)
    }

    // --- admin toolbox ------------------------------------------------------------------------

    fun adminBranch(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("admin")
            .requires { ObPermissions.check(it, ObPermissions.ADMIN_MODERATE, 4) }
            .then(
                Commands.literal("kick").then(
                    Commands.argument("player", GameProfileArgument.gameProfile()).executes(::adminKick)
                )
            )
            .then(
                Commands.literal("info").then(
                    Commands.argument("player", GameProfileArgument.gameProfile()).executes(::adminInfo)
                )
            )
            .then(
                Commands.literal("reset")
                    .then(
                        Commands.argument("player", GameProfileArgument.gameProfile())
                            .executes { requestForce(it, "admin_reset") }
                            .then(Commands.literal("confirm").executes { confirmForce(it, reset = true) })
                    )
            )
            .then(
                Commands.literal("delete")
                    .then(
                        Commands.argument("player", GameProfileArgument.gameProfile())
                            .executes { requestForce(it, "admin_delete") }
                            .then(Commands.literal("confirm").executes { confirmForce(it, reset = false) })
                    )
            )
            // Hub travel lives in its own file; it hangs here because it is admin work and
            // shares this branch's permission node.
            .then(HubCommands.portalBranch())
            .then(HubCommands.unlockBranch())

    private fun targetIsland(context: CommandContext<CommandSourceStack>): Pair<IslandData, UUID>? {
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return null
        val island = OneBlockCore.islandManager?.islandDataOf(profile.id)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none_other", profile.name))
            return null
        }
        return island to profile.id
    }

    private fun adminKick(context: CommandContext<CommandSourceStack>): Int {
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return 0
        val online = context.source.server.playerList.getPlayer(profile.id)
        if (online == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.admin.not_online", profile.name))
            return 0
        }
        HubManager.sendToHub(online)
        online.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.admin.kicked_to_hub"))
        context.source.sendSuccess({ ServerLang.msg("cobblemon_oneblock.admin.kick_done", profile.name) }, true)
        AuditLog.record("admin.kick", context.source, profile.id, "sent to hub")
        return Command.SINGLE_SUCCESS
    }

    private fun adminInfo(context: CommandContext<CommandSourceStack>): Int {
        val (island, _) = targetIsland(context) ?: return 0
        val config = OneBlockCore.configManager.mainConfig
        val size = GridMath.borderSizeAt(island.borderLevel, config)
        val anchor = island.oneBlockPos()
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.admin.info_header", island.id, island.slot()).withStyle(ChatFormatting.GOLD),
        )
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.admin.info_location", anchor.x, anchor.y, anchor.z),
        )
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.admin.info_progress", island.borderLevel, size, island.breakCount),
        )
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.admin.info_party", island.memberSet.size + 1, config.maxPartySize),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun requestForce(context: CommandContext<CommandSourceStack>, action: String): Int {
        val (island, ownerId) = targetIsland(context) ?: return 0
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return 0
        // Console has no UUID — use a fixed key so console confirmations work too.
        val key = context.source.player?.uuid ?: CONSOLE_KEY
        Confirmations.request(key, "$action:$ownerId")

        val verb = if (action == "admin_reset") "reset" else "delete"
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.admin.confirm_prompt", verb, profile.name, island.id)
                .withStyle(ChatFormatting.RED)
                .append(" ")
                .append(
                    ServerLang.msg("cobblemon_oneblock.confirm.button")
                        .withStyle(ChatFormatting.RED)
                        .withStyle {
                            it.withClickEvent(
                                ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ob admin $verb ${profile.name} confirm"),
                            ).withBold(true)
                        },
                ),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun confirmForce(context: CommandContext<CommandSourceStack>, reset: Boolean): Int {
        val (island, ownerId) = targetIsland(context) ?: return 0
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return 0
        val action = if (reset) "admin_reset" else "admin_delete"
        val key = context.source.player?.uuid ?: CONSOLE_KEY
        if (!Confirmations.consume(key, "$action:$ownerId")) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.confirm.expired"))
            return 0
        }
        val manager = OneBlockCore.islandManager ?: return 0
        val server = context.source.server
        if (!manager.archive(ownerId, server)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none_other", profile.name))
            return 0
        }
        BanService.clearIsland(island.id)

        if (reset) {
            val online = server.playerList.getPlayer(ownerId)
            if (online != null) {
                manager.create(online)
            } else {
                context.source.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.admin.reset_offline", profile.name))
            }
        } else {
            server.playerList.getPlayer(ownerId)?.let { HubManager.sendToHub(it) }
        }
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.admin.force_done", if (reset) "reset" else "delete", profile.name) }, true,
        )
        AuditLog.record(if (reset) "admin.force_reset" else "admin.force_delete",
            context.source, ownerId, "island=${island.id}")
        return Command.SINGLE_SUCCESS
    }

    private val CONSOLE_KEY: UUID = UUID(0L, 0L)
}
