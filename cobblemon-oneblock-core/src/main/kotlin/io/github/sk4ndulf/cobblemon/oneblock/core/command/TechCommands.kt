package io.github.sk4ndulf.cobblemon.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.gui.TechMenu
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechCategory
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechNode
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechRequirement
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechService
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer

/**
 * `/ob tech` — the chat interface to the island tech tree.
 *
 * Chat first, chest menu later (PROGRESSION_REWORK.md §10 slice D). This is not a stopgap:
 * a text interface that exposes the whole model is how the model gets tested, and the menu is
 * then a presentation layer over something already known to work. Every listing line carries a
 * click event so buying from chat is one click, not retyping a node id.
 */
object TechCommands {

    /** Built here and attached by [ObCommands] so the whole `/ob` tree still lives in one place. */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("tech")
            .requires { ObPermissions.check(it, ObPermissions.COMMAND_TECH, 0) }
            .executes(::openMenu)
            .then(Commands.literal("chat").executes(::overview))
            .then(
                Commands.literal("list").then(
                    Commands.argument("category", StringArgumentType.word())
                        .suggests { _, builder ->
                            SharedSuggestionProvider.suggest(TechCategory.entries.map { it.key }, builder)
                        }
                        .executes(::listCategory)
                )
            )
            .then(
                Commands.literal("info").then(
                    Commands.argument("node", StringArgumentType.word())
                        .suggests(::suggestNodes)
                        .executes(::nodeInfo)
                )
            )
            .then(
                Commands.literal("unlock").then(
                    Commands.argument("node", StringArgumentType.word())
                        .suggests(::suggestNodes)
                        .executes(::unlock)
                )
            )
            .then(
                Commands.literal("delegate").then(
                    Commands.argument("player", EntityArgument.player()).then(
                        Commands.argument("allowed", BoolArgumentType.bool())
                            .executes(::delegate)
                    )
                )
            )
            .then(
                Commands.literal("grant")
                    .requires { ObPermissions.check(it, ObPermissions.ADMIN_TECH, 4) }
                    .then(
                        Commands.argument("player", EntityArgument.player()).then(
                            Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(::grant)
                        )
                    )
            )

    private fun suggestNodes(context: CommandContext<CommandSourceStack>, builder: com.mojang.brigadier.suggestion.SuggestionsBuilder) =
        SharedSuggestionProvider.suggest(TechService.tree.nodes.keys.sorted(), builder)

    // --- listings ----------------------------------------------------------------------------

    /** `/ob tech` — the chest menu. `/ob tech chat` keeps the text version for anyone who wants it. */
    private fun openMenu(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = islandOf(context, player) ?: return 0
        if (TechService.tree.nodes.isEmpty()) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.tech.empty"))
            return 0
        }
        TechMenu.openMain(player, island)
        return Command.SINGLE_SUCCESS
    }

    private fun overview(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = islandOf(context, player) ?: return 0
        val state = TechService.stateOf(island.id)
        val tree = TechService.tree

        player.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.tech.header", state.balance, state.lifetimeEarned)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
        )
        for (category in TechCategory.entries) {
            val nodes = tree.byCategory[category].orEmpty()
            if (nodes.isEmpty()) continue
            val owned = nodes.sumOf { state.levelOf(it.id) }
            val total = nodes.sumOf { it.maxLevel }
            player.sendSystemMessage(
                ServerLang.msg("cobblemon_oneblock.tech.category_line", category.key, owned, total)
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ob tech list ${category.key}")) },
            )
        }
        if (tree.nodes.isEmpty()) {
            player.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.tech.empty").withStyle(ChatFormatting.RED))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun listCategory(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = islandOf(context, player) ?: return 0
        val raw = StringArgumentType.getString(context, "category")
        val category = TechCategory.parse(raw)
        if (category == null) {
            context.source.sendFailure(
                ServerLang.msg(
                    "cobblemon_oneblock.tech.unknown_category", raw,
                    TechCategory.entries.joinToString(", ") { it.key },
                ),
            )
            return 0
        }
        val nodes = TechService.tree.byCategory[category].orEmpty()
        if (nodes.isEmpty()) {
            player.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.tech.category_empty", category.key))
            return Command.SINGLE_SUCCESS
        }
        player.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.tech.category_header", category.key)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
        )
        for (node in nodes) {
            player.sendSystemMessage(lineFor(island, node))
        }
        return Command.SINGLE_SUCCESS
    }

    /**
     * One line per node: name, level, and either the price or the reason it is unavailable.
     * Colour carries the state so the list is scannable without reading every word —
     * green buyable, grey maxed, red locked, yellow too expensive.
     */
    private fun lineFor(island: IslandData, node: TechNode): Component {
        val state = TechService.stateOf(island.id)
        val level = state.levelOf(node.id)
        // Always the rank, including for single-rank nodes: "1/1" reads as a talent rank the
        // way "[owned]" does not, and a mixed tree of x/1, x/3 and x/5 is only legible when
        // every row is written the same way.
        val progress = "$level/${node.maxLevel}"
        val cost = node.costOfNext(level)
        val unmet = TechService.unmetRequirements(island, node)

        val (suffix, colour) = when {
            !TechService.tree.isImplemented(node) ->
                ServerLang.raw("cobblemon_oneblock.tech.not_implemented_short") to ChatFormatting.DARK_PURPLE
            cost == null -> ServerLang.raw("cobblemon_oneblock.tech.maxed") to ChatFormatting.DARK_GRAY
            unmet.isNotEmpty() ->
                ServerLang.raw("cobblemon_oneblock.tech.locked_short", describe(unmet)) to ChatFormatting.RED
            state.balance < cost ->
                ServerLang.raw("cobblemon_oneblock.tech.price", cost) to ChatFormatting.YELLOW
            else -> ServerLang.raw("cobblemon_oneblock.tech.price", cost) to ChatFormatting.GREEN
        }

        val text = ServerLang.msg("cobblemon_oneblock.tech.node_line", node.displayName, progress, suffix)
            .withStyle(colour)
            .withStyle {
                it.withHoverEvent(
                    HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(node.description.ifBlank { node.id })),
                )
            }
        // Only offer the click when it would actually do something.
        return if (cost != null && unmet.isEmpty() && state.balance >= cost) {
            text.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ob tech unlock ${node.id}")) }
        } else {
            text.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ob tech info ${node.id}")) }
        }
    }

    private fun nodeInfo(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = islandOf(context, player) ?: return 0
        val nodeId = StringArgumentType.getString(context, "node")
        val node = TechService.tree.node(nodeId)
        if (node == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.tech.unknown_node", nodeId))
            return 0
        }
        val state = TechService.stateOf(island.id)
        val level = state.levelOf(node.id)

        player.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.tech.info_header", node.displayName, level, node.maxLevel)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
        )
        if (node.description.isNotBlank()) {
            player.sendSystemMessage(Component.literal(node.description).withStyle(ChatFormatting.GRAY))
        }
        val cost = node.costOfNext(level)
        if (cost == null) {
            player.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.tech.maxed").withStyle(ChatFormatting.DARK_GRAY))
        } else {
            player.sendSystemMessage(
                ServerLang.msg("cobblemon_oneblock.tech.info_next", level + 1, cost, state.balance)
                    .withStyle(ChatFormatting.YELLOW),
            )
        }
        val unmet = TechService.unmetRequirements(island, node)
        if (unmet.isNotEmpty()) {
            player.sendSystemMessage(
                ServerLang.msg("cobblemon_oneblock.tech.info_locked", describe(unmet)).withStyle(ChatFormatting.RED),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    // --- actions -----------------------------------------------------------------------------

    private fun unlock(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = islandOf(context, player) ?: return 0
        val nodeId = StringArgumentType.getString(context, "node")

        return when (val outcome = TechService.buy(island, player.uuid, nodeId)) {
            is TechService.Outcome.Bought -> {
                announceUnlock(island, player, outcome)
                Command.SINGLE_SUCCESS
            }
            TechService.Outcome.UnknownNode -> {
                context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.tech.unknown_node", nodeId)); 0
            }
            TechService.Outcome.AlreadyMaxed -> {
                context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.tech.maxed")); 0
            }
            TechService.Outcome.NotImplemented -> {
                context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.tech.not_implemented")); 0
            }
            TechService.Outcome.NotAllowed -> {
                context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.tech.not_allowed")); 0
            }
            is TechService.Outcome.NotEnoughPoints -> {
                context.source.sendFailure(
                    ServerLang.msg("cobblemon_oneblock.tech.too_expensive", outcome.needed, outcome.have),
                )
                0
            }
            is TechService.Outcome.Locked -> {
                context.source.sendFailure(
                    ServerLang.msg("cobblemon_oneblock.tech.info_locked", describe(outcome.unmet)),
                )
                0
            }
        }
    }

    /** The island paid for it together, so the island hears about it together. */
    private fun announceUnlock(island: IslandData, buyer: ServerPlayer, outcome: TechService.Outcome.Bought) {
        val server = buyer.server
        for (uuid in island.memberSet + island.owner()) {
            val member = server.playerList.getPlayer(uuid) ?: continue
            member.sendSystemMessage(
                ServerLang.msg(
                    "cobblemon_oneblock.tech.unlocked",
                    buyer.gameProfile.name, outcome.node.displayName, outcome.newLevel,
                    outcome.spent, outcome.balanceLeft,
                ).withStyle(ChatFormatting.GREEN),
            )
        }
    }

    private fun delegate(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = islandOf(context, player) ?: return 0
        if (island.owner() != player.uuid) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.tech.owner_only"))
            return 0
        }
        val target = EntityArgument.getPlayer(context, "player")
        if (!island.memberSet.contains(target.uuid)) {
            context.source.sendFailure(
                ServerLang.msg("cobblemon_oneblock.tech.not_a_member", target.gameProfile.name),
            )
            return 0
        }
        val allowed = BoolArgumentType.getBool(context, "allowed")
        TechService.setSpender(island, target.uuid, allowed)
        context.source.sendSuccess(
            {
                ServerLang.msg(
                    if (allowed) "cobblemon_oneblock.tech.delegated" else "cobblemon_oneblock.tech.undelegated",
                    target.gameProfile.name,
                ).withStyle(ChatFormatting.GREEN)
            },
            false,
        )
        target.sendSystemMessage(
            ServerLang.msg(
                if (allowed) "cobblemon_oneblock.tech.delegated_you" else "cobblemon_oneblock.tech.undelegated_you",
            ),
        )
        return Command.SINGLE_SUCCESS
    }

    private fun grant(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "player")
        val amount = LongArgumentType.getLong(context, "amount")
        val island = OneBlockCore.islandManager?.islandDataOf(target.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none_other", target.gameProfile.name))
            return 0
        }
        TechService.grant(island.id, amount)
        val balance = TechService.stateOf(island.id).balance
        context.source.sendSuccess(
            {
                ServerLang.msg("cobblemon_oneblock.tech.granted", amount, target.gameProfile.name, balance)
                    .withStyle(ChatFormatting.GREEN)
            },
            true,
        )
        return Command.SINGLE_SUCCESS
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun islandOf(context: CommandContext<CommandSourceStack>, player: ServerPlayer): IslandData? {
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid)
        if (island == null) context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
        return island
    }

    /** Human-readable requirement list — "Forest 2, 120 points earned, beat nether_king". */
    private fun describe(requirements: List<TechRequirement>): String =
        requirements.joinToString(", ") { requirement ->
            when (requirement) {
                is TechRequirement.Node -> {
                    val name = TechService.tree.node(requirement.nodeId)?.displayName ?: requirement.nodeId
                    "$name ${requirement.level}"
                }
                is TechRequirement.LifetimePoints ->
                    ServerLang.raw("cobblemon_oneblock.tech.req_points", requirement.total)
                is TechRequirement.CategorySpend ->
                    ServerLang.raw(
                        "cobblemon_oneblock.tech.req_spent", requirement.amount, requirement.category.key,
                    )
                is TechRequirement.Boss ->
                    ServerLang.raw("cobblemon_oneblock.tech.req_boss", requirement.bossId)
            }
        }
}
