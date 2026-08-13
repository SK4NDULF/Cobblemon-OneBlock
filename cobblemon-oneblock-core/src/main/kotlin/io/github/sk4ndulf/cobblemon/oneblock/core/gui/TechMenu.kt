package io.github.sk4ndulf.cobblemon.oneblock.core.gui

import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechCategory
import io.github.sk4ndulf.cobblemon.oneblock.core.progression.TechService
import net.minecraft.ChatFormatting
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Opens the tech menus and routes their clicks.
 *
 * Two menus rather than one with tabs (owner's call, 2026-08-13): a main menu showing the
 * island and its six branches, and one menu per branch laid out as a row per tier. All the
 * decisions about *what* is shown live in [TechMenuLayout]; this file only opens containers
 * and reacts to clicks.
 */
object TechMenu {

    fun openMain(player: ServerPlayer, island: IslandData) {
        val tree = TechService.tree
        val entries = TechMenuLayout.main(island, ownerName(player.server, island), tree)
        StaticMenu.open(
            player = player,
            title = ServerLang.msg("cobblemon_oneblock.menu.main_title"),
            rows = TechMenuLayout.MAIN_ROWS,
            contents = entries.mapValues { it.value.stack },
        ) { slot ->
            when (val action = entries[slot]?.action) {
                is TechMenuLayout.Action.OpenCategory -> openCategory(player, island, action.category)
                else -> Unit
            }
        }
    }

    fun openCategory(player: ServerPlayer, island: IslandData, category: TechCategory) {
        val tree = TechService.tree
        val entries = TechMenuLayout.category(island, tree, category)
        StaticMenu.open(
            player = player,
            title = ServerLang.msg("cobblemon_oneblock.menu.category.${category.key}"),
            rows = TechMenuLayout.CATEGORY_ROWS,
            contents = entries.mapValues { it.value.stack },
        ) { slot ->
            when (val action = entries[slot]?.action) {
                is TechMenuLayout.Action.Back -> openMain(player, island)
                is TechMenuLayout.Action.Buy -> {
                    buy(player, island, action.nodeId)
                    // Reopen so the ranks, balance and any tier that just opened are current.
                    openCategory(player, island, category)
                }
                else -> Unit
            }
        }
    }

    /**
     * Runs a purchase from the menu. Failures are reported in chat rather than swallowed —
     * a click that silently does nothing reads as a broken GUI, and the reason ("you need
     * 15 points spent in this branch") is the most useful thing the menu can say.
     */
    private fun buy(player: ServerPlayer, island: IslandData, nodeId: String) {
        when (val outcome = TechService.buy(island, player.uuid, nodeId)) {
            is TechService.Outcome.Bought -> {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f)
                for (uuid in island.memberSet + island.owner()) {
                    val member = player.server.playerList.getPlayer(uuid) ?: continue
                    member.sendSystemMessage(
                        ServerLang.msg(
                            "cobblemon_oneblock.tech.unlocked",
                            player.gameProfile.name, outcome.node.displayName, outcome.newLevel,
                            outcome.spent, outcome.balanceLeft,
                        ).withStyle(ChatFormatting.GREEN),
                    )
                }
            }
            TechService.Outcome.AlreadyMaxed ->
                deny(player, ServerLang.msg("cobblemon_oneblock.tech.maxed"))
            TechService.Outcome.NotAllowed ->
                deny(player, ServerLang.msg("cobblemon_oneblock.tech.not_allowed"))
            TechService.Outcome.UnknownNode ->
                deny(player, ServerLang.msg("cobblemon_oneblock.tech.unknown_node", nodeId))
            is TechService.Outcome.NotEnoughPoints ->
                deny(
                    player,
                    ServerLang.msg("cobblemon_oneblock.tech.too_expensive", outcome.needed, outcome.have),
                )
            is TechService.Outcome.Locked ->
                deny(player, ServerLang.msg("cobblemon_oneblock.tech.locked_generic"))
        }
    }

    private fun deny(player: ServerPlayer, message: net.minecraft.network.chat.MutableComponent) {
        player.displayClientMessage(message.withStyle(ChatFormatting.RED), true)
        player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1.0f)
    }

    /** Islands have no name of their own yet, so the owner's name stands in. */
    private fun ownerName(server: MinecraftServer, island: IslandData): String =
        server.playerList.getPlayer(island.owner())?.gameProfile?.name
            ?: server.profileCache?.get(island.owner())?.orElse(null)?.name
            ?: "#${island.id}"
}
