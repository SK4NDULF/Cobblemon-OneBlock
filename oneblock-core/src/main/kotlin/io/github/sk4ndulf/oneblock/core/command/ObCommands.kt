package io.github.sk4ndulf.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.oneblock.core.wizard.SetupWizard
import io.github.sk4ndulf.oneblock.core.wizard.WizardQuestions
import io.github.sk4ndulf.oneblock.core.world.HubManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ClickEvent
import java.util.concurrent.CompletableFuture

/**
 * The `/ob` command tree. Feature phases attach their subcommands here so the whole
 * tree lives in one place and naming stays consistent.
 */
object ObCommands {

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                Commands.literal("ob")
                    .requires { ObPermissions.check(it, ObPermissions.COMMAND_ROOT, 0) }
                    .then(
                        Commands.literal("create")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_CREATE, 0) }
                            .executes(::create)
                    )
                    .then(
                        Commands.literal("home")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_HOME, 0) }
                            .executes(::home)
                    )
                    .then(
                        Commands.literal("spawn")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_SPAWN, 0) }
                            .executes(::spawn)
                    )
                    .then(
                        Commands.literal("reset")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_RESET, 0) }
                            .executes { requestDestructive(it, "reset", "oneblock.island.reset_confirm") }
                            .then(Commands.literal("confirm").executes { confirmReset(it) })
                    )
                    .then(
                        Commands.literal("delete")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_DELETE, 0) }
                            .executes { requestDestructive(it, "delete", "oneblock.island.delete_confirm") }
                            .then(Commands.literal("confirm").executes { confirmDelete(it) })
                    )
                    .then(
                        Commands.literal("reload")
                            .requires { ObPermissions.check(it, ObPermissions.ADMIN_RELOAD, 4) }
                            .executes(::reload)
                    )
                    .then(
                        Commands.literal("setup")
                            .requires { ObPermissions.check(it, ObPermissions.ADMIN_SETUP, 4) }
                            .executes { SetupWizard.start(it.source); Command.SINGLE_SUCCESS }
                            .then(
                                Commands.literal("answer").then(
                                    Commands.argument("value", StringArgumentType.greedyString())
                                        .executes {
                                            SetupWizard.answer(it.source, StringArgumentType.getString(it, "value"))
                                            Command.SINGLE_SUCCESS
                                        }
                                )
                            )
                            .then(Commands.literal("skip")
                                .executes { SetupWizard.skip(it.source); Command.SINGLE_SUCCESS })
                            .then(Commands.literal("cancel")
                                .executes { SetupWizard.cancel(it.source); Command.SINGLE_SUCCESS })
                            .then(Commands.literal("confirm")
                                .executes { SetupWizard.confirm(it.source); Command.SINGLE_SUCCESS })
                            .then(
                                Commands.literal("set").then(
                                    Commands.argument("key", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            SharedSuggestionProvider.suggest(
                                                WizardQuestions.ALL.map { it.key }, builder,
                                            )
                                        }
                                        .then(
                                            Commands.argument("value", StringArgumentType.greedyString())
                                                .executes {
                                                    SetupWizard.setDirect(
                                                        it.source,
                                                        StringArgumentType.getString(it, "key"),
                                                        StringArgumentType.getString(it, "value"),
                                                    )
                                                    Command.SINGLE_SUCCESS
                                                }
                                        )
                                )
                            )
                    )
            )
        }
    }

    // --- island commands -------------------------------------------------------------------

    private fun create(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val manager = OneBlockCore.islandManager
        if (!OneBlockCore.configManager.mainConfig.setupCompleted) {
            context.source.sendFailure(ServerLang.msg("oneblock.setup_pending"))
            return 0
        }
        if (manager == null) {
            context.source.sendFailure(ServerLang.msg("oneblock.error.not_ready"))
            return 0
        }
        if (manager.islandDataOf(player.uuid) != null) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.already_have"))
            return 0
        }
        manager.create(player)
        context.source.sendSuccess(
            { ServerLang.msg("oneblock.island.created").withStyle(ChatFormatting.GREEN) }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun home(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.none"))
            return 0
        }
        OneBlockCore.islandManager?.sendHome(player, island)
        context.source.sendSuccess({ ServerLang.msg("oneblock.island.teleported_home") }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun spawn(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        HubManager.sendToHub(player)
        context.source.sendSuccess({ ServerLang.msg("oneblock.island.teleported_spawn") }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun requestDestructive(
        context: CommandContext<CommandSourceStack>,
        action: String,
        confirmLangKey: String,
    ): Int {
        val player = context.source.playerOrException
        val manager = OneBlockCore.islandManager
        val island = manager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.none"))
            return 0
        }
        if (island.owner() != player.uuid) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.only_owner"))
            return 0
        }
        Confirmations.request(player.uuid, action)
        val message = ServerLang.msg(confirmLangKey, OneBlockCore.configManager.mainConfig.resetArchiveDays)
            .withStyle(ChatFormatting.GOLD)
            .append(" ")
            .append(
                ServerLang.msg("oneblock.confirm.button")
                    .withStyle(ChatFormatting.RED)
                    .withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ob $action confirm")).withBold(true) },
            )
        context.source.sendSystemMessage(message)
        return Command.SINGLE_SUCCESS
    }

    private fun confirmReset(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val manager = OneBlockCore.islandManager ?: return 0
        if (!Confirmations.consume(player.uuid, "reset")) {
            context.source.sendFailure(ServerLang.msg("oneblock.confirm.expired"))
            return 0
        }
        if (!manager.archive(player.uuid)) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.none"))
            return 0
        }
        manager.create(player)
        context.source.sendSuccess(
            { ServerLang.msg("oneblock.island.reset_done").withStyle(ChatFormatting.GREEN) }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun confirmDelete(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val manager = OneBlockCore.islandManager ?: return 0
        if (!Confirmations.consume(player.uuid, "delete")) {
            context.source.sendFailure(ServerLang.msg("oneblock.confirm.expired"))
            return 0
        }
        if (!manager.archive(player.uuid)) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.none"))
            return 0
        }
        HubManager.sendToHub(player)
        context.source.sendSuccess(
            { ServerLang.msg("oneblock.island.delete_done") }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = source.server

        source.sendSuccess({ ServerLang.msg("oneblock.reload.start") }, true)
        OneBlockCore.configManager.loadAll()
        ServerLang.load(OneBlockCore.configManager.mainConfig.language, OneBlockCore.LOGGER)
        OneBlockCore.lootTable.load()

        // Reconnecting the pool and testing the connection blocks — keep it off the server thread.
        CompletableFuture.supplyAsync { OneBlockCore.connectDatabase() }
            .thenAccept { error ->
                server.execute {
                    if (error == null) {
                        OneBlockCore.reloadIslands()
                        source.sendSuccess({ ServerLang.msg("oneblock.reload.ok") }, true)
                    } else {
                        source.sendFailure(ServerLang.msg("oneblock.reload.failed", error))
                    }
                }
            }
        return Command.SINGLE_SUCCESS
    }
}
