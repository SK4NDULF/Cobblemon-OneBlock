package io.github.sk4ndulf.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.oneblock.core.wizard.SetupWizard
import io.github.sk4ndulf.oneblock.core.wizard.WizardQuestions
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
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

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = source.server

        source.sendSuccess({ ServerLang.msg("oneblock.reload.start") }, true)
        OneBlockCore.configManager.loadAll()
        ServerLang.load(OneBlockCore.configManager.mainConfig.language, OneBlockCore.LOGGER)

        // Reconnecting the pool and testing the connection blocks — keep it off the server thread.
        CompletableFuture.supplyAsync { OneBlockCore.connectDatabase() }
            .thenAccept { error ->
                server.execute {
                    if (error == null) {
                        source.sendSuccess({ ServerLang.msg("oneblock.reload.ok") }, true)
                    } else {
                        source.sendFailure(ServerLang.msg("oneblock.reload.failed", error))
                    }
                }
            }
        return Command.SINGLE_SUCCESS
    }
}
