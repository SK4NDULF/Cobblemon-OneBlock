package io.github.sk4ndulf.cobblemon.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.moderation.AuditLog
import io.github.sk4ndulf.cobblemon.oneblock.core.unlock.Unlocks
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubPortals
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.GameProfileArgument
import java.util.UUID

/**
 * `/ob admin portal …` and `/ob admin unlock …` — the two halves of hub travel.
 *
 * The portal half is deliberately a click and not a coordinate argument: a portal is a shape
 * an admin built by hand, and clicking the thing you mean is both easier to type and harder
 * to get wrong than describing its corners.
 */
object HubCommands {

    // --- /ob admin portal ---------------------------------------------------------------------

    fun portalBranch(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("portal")
            .then(
                Commands.literal("link").then(
                    Commands.argument("target", StringArgumentType.word())
                        .suggests { _, builder ->
                            SharedSuggestionProvider.suggest(OneBlockDimension.SHORT_NAMES, builder)
                        }
                        .executes(::armLink)
                )
            )
            .then(Commands.literal("unlink").executes(::armUnlink))
            .then(Commands.literal("list").executes(::listPortals))

    private fun armLink(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val raw = StringArgumentType.getString(context, "target")
        val target = OneBlockDimension.byShortName(raw)
        if (target == null) {
            context.source.sendFailure(
                ServerLang.msg(
                    "cobblemon_oneblock.portal.unknown_target", raw, OneBlockDimension.SHORT_NAMES.joinToString(", "),
                ),
            )
            return 0
        }
        HubPortals.arm(player, target)
        context.source.sendSystemMessage(
            ServerLang.msg(
                "cobblemon_oneblock.portal.link_armed",
                OneBlockDimension.displayName(target), HubPortals.SELECTION_TIMEOUT_SECONDS,
            ).withStyle(ChatFormatting.GOLD),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun armUnlink(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        HubPortals.arm(player, null)
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.portal.unlink_armed", HubPortals.SELECTION_TIMEOUT_SECONDS)
                .withStyle(ChatFormatting.GOLD),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun listPortals(context: CommandContext<CommandSourceStack>): Int {
        val bindings = HubPortals.all()
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.portal.list_header", bindings.size).withStyle(ChatFormatting.GOLD),
        )
        bindings.forEachIndexed { index, binding ->
            context.source.sendSystemMessage(
                ServerLang.msg(
                    "cobblemon_oneblock.portal.list_entry",
                    index + 1,
                    OneBlockDimension.displayName(binding.dimension),
                    binding.box.minX(), binding.box.minY(), binding.box.minZ(),
                    OneBlockDimension.displayName(binding.target),
                ),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    // --- /ob admin unlock ---------------------------------------------------------------------

    fun unlockBranch(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("unlock")
            .then(
                Commands.literal("grant").then(
                    Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(keyArgument { context, uuid, name, key -> grant(context, uuid, name, key) })
                )
            )
            .then(
                Commands.literal("revoke").then(
                    Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(keyArgument { context, uuid, name, key -> revoke(context, uuid, name, key) })
                )
            )
            .then(
                Commands.literal("list").then(
                    Commands.argument("player", GameProfileArgument.gameProfile()).executes(::listUnlocks)
                )
            )

    private fun keyArgument(action: (CommandContext<CommandSourceStack>, UUID, String, String) -> Int) =
        Commands.argument("key", StringArgumentType.word())
            .suggests { _, builder -> SharedSuggestionProvider.suggest(Unlocks.KNOWN, builder) }
            .executes { context ->
                val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull()
                    ?: return@executes 0
                val key = StringArgumentType.getString(context, "key")
                if (key !in Unlocks.KNOWN) {
                    // A typo here is invisible otherwise: the unlock would be stored, and the
                    // gate it was meant to open would stay shut with nothing to show why.
                    context.source.sendFailure(
                        ServerLang.msg("cobblemon_oneblock.unlock.unknown", key, Unlocks.KNOWN.joinToString(", ")),
                    )
                    return@executes 0
                }
                action(context, profile.id, profile.name, key)
            }

    private fun grant(context: CommandContext<CommandSourceStack>, uuid: UUID, name: String, key: String): Int {
        if (!Unlocks.grant(uuid, key)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.unlock.already", name, key))
            return 0
        }
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.unlock.granted", name, key).withStyle(ChatFormatting.GREEN) }, true,
        )
        context.source.server.playerList.getPlayer(uuid)?.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.unlock.received", key).withStyle(ChatFormatting.GREEN),
        )
        AuditLog.record("unlock.grant", context.source, uuid, "key=$key")
        return Command.SINGLE_SUCCESS
    }

    private fun revoke(context: CommandContext<CommandSourceStack>, uuid: UUID, name: String, key: String): Int {
        if (!Unlocks.revoke(uuid, key)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.unlock.not_held", name, key))
            return 0
        }
        context.source.sendSuccess({ ServerLang.msg("cobblemon_oneblock.unlock.revoked", key, name) }, true)
        AuditLog.record("unlock.revoke", context.source, uuid, "key=$key")
        return Command.SINGLE_SUCCESS
    }

    private fun listUnlocks(context: CommandContext<CommandSourceStack>): Int {
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return 0
        val keys = Unlocks.keysOf(profile.id)
        if (keys.isEmpty()) {
            context.source.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.unlock.list_empty", profile.name))
            return Command.SINGLE_SUCCESS
        }
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.unlock.list_header", profile.name, keys.size)
                .withStyle(ChatFormatting.GOLD),
        )
        for (key in keys) {
            context.source.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.unlock.list_entry", key))
        }
        return Command.SINGLE_SUCCESS
    }
}
