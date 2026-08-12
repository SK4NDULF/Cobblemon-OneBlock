package io.github.sk4ndulf.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
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
            )
        }
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = source.server

        source.sendSuccess({ Component.literal("[OneBlock] Reloading configuration...") }, true)
        OneBlockCore.configManager.loadAll()

        // Reconnecting the pool and testing the connection blocks — keep it off the server thread.
        CompletableFuture.supplyAsync { OneBlockCore.connectDatabase() }
            .thenAccept { error ->
                server.execute {
                    if (error == null) {
                        source.sendSuccess(
                            { Component.literal("[OneBlock] Reload complete, database connection OK.") },
                            true,
                        )
                    } else {
                        source.sendFailure(Component.literal("[OneBlock] Reload finished with errors: $error"))
                    }
                }
            }
        return Command.SINGLE_SUCCESS
    }
}
