package io.github.sk4ndulf.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.cobblemon.BuffService
import io.github.sk4ndulf.oneblock.core.cobblemon.BuffType
import io.github.sk4ndulf.oneblock.core.cobblemon.CobblemonIntegration
import io.github.sk4ndulf.oneblock.core.island.PartyService
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.oneblock.core.trigger.TriggerEventService
import io.github.sk4ndulf.oneblock.core.wizard.SetupWizard
import io.github.sk4ndulf.oneblock.core.wizard.WizardQuestions
import io.github.sk4ndulf.oneblock.core.world.HubManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.GameProfileArgument
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
                        Commands.literal("info")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_INFO, 0) }
                            .executes(::info)
                    )
                    .then(
                        Commands.literal("settings")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_SETTINGS, 0) }
                            .then(
                                Commands.literal("visitor-catch").then(
                                    Commands.argument("allowed", BoolArgumentType.bool())
                                        .executes { setVisitorSetting(it, catch = true) }
                                )
                            )
                            .then(
                                Commands.literal("visitor-battle").then(
                                    Commands.argument("allowed", BoolArgumentType.bool())
                                        .executes { setVisitorSetting(it, catch = false) }
                                )
                            )
                    )
                    .then(
                        Commands.literal("buff")
                            .requires { ObPermissions.check(it, ObPermissions.ADMIN_BUFF, 4) }
                            .then(
                                Commands.argument("player", EntityArgument.player())
                                    .then(
                                        Commands.argument("type", StringArgumentType.word())
                                            .suggests { _, builder ->
                                                SharedSuggestionProvider.suggest(
                                                    BuffType.entries.map { it.name.lowercase() }, builder,
                                                )
                                            }
                                            .then(
                                                Commands.argument("value", DoubleArgumentType.doubleArg(0.0))
                                                    .then(
                                                        Commands.argument("minutes", IntegerArgumentType.integer(1))
                                                            .executes(::grantBuff)
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("party")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_PARTY, 0) }
                            .then(
                                Commands.literal("invite").then(
                                    Commands.argument("player", EntityArgument.player())
                                        .executes {
                                            PartyService.invite(
                                                it.source.playerOrException,
                                                EntityArgument.getPlayer(it, "player"),
                                            )
                                            Command.SINGLE_SUCCESS
                                        }
                                )
                            )
                            .then(Commands.literal("accept")
                                .executes { PartyService.accept(it.source.playerOrException); Command.SINGLE_SUCCESS })
                            .then(Commands.literal("deny")
                                .executes { PartyService.deny(it.source.playerOrException); Command.SINGLE_SUCCESS })
                            .then(
                                Commands.literal("kick").then(
                                    Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes { context ->
                                            val profiles = GameProfileArgument.getGameProfiles(context, "player")
                                            profiles.forEach { PartyService.kick(context.source.playerOrException, it) }
                                            Command.SINGLE_SUCCESS
                                        }
                                )
                            )
                            .then(Commands.literal("leave")
                                .executes { PartyService.leave(it.source.playerOrException); Command.SINGLE_SUCCESS })
                            .then(Commands.literal("list")
                                .executes { PartyService.list(it.source.playerOrException); Command.SINGLE_SUCCESS })
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

    private fun info(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.none"))
            return 0
        }
        val config = OneBlockCore.configManager.mainConfig
        val size = io.github.sk4ndulf.oneblock.core.world.GridMath.borderSizeAt(island.borderLevel, config)
        context.source.sendSystemMessage(
            ServerLang.msg("oneblock.info.header", island.borderLevel, size, size).withStyle(ChatFormatting.GOLD),
        )
        context.source.sendSystemMessage(ServerLang.msg("oneblock.info.breaks", island.breakCount))
        if (island.borderLevel < io.github.sk4ndulf.oneblock.core.island.ProgressionService.MAX_LEVEL) {
            val next = io.github.sk4ndulf.oneblock.core.island.ProgressionService.pointsRequiredFor(island.borderLevel + 1)
            context.source.sendSystemMessage(
                ServerLang.msg("oneblock.info.points", "%,.0f".format(island.points), "%,.0f".format(next)),
            )
        } else {
            context.source.sendSystemMessage(ServerLang.msg("oneblock.info.max_level"))
        }
        context.source.sendSystemMessage(
            ServerLang.msg("oneblock.info.party", island.memberSet.size + 1, config.maxPartySize),
        )
        TriggerEventService.activeTypeOn(island.id)?.let { eventId ->
            context.source.sendSystemMessage(ServerLang.msg("oneblock.info.event_active", eventId.toString()))
        }
        return Command.SINGLE_SUCCESS
    }

    private fun setVisitorSetting(context: CommandContext<CommandSourceStack>, catch: Boolean): Int {
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
        val allowed = BoolArgumentType.getBool(context, "allowed")
        if (catch) island.allowVisitorCatch = allowed else island.allowVisitorBattle = allowed
        manager.persistVisitorSettings(island)

        val labelKey = if (catch) "oneblock.settings.visitor_catch" else "oneblock.settings.visitor_battle"
        val stateKey = if (allowed) "oneblock.value.yes" else "oneblock.value.no"
        context.source.sendSuccess(
            { ServerLang.msg("oneblock.settings.saved", ServerLang.raw(labelKey), ServerLang.raw(stateKey)) }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun grantBuff(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "player")
        val island = OneBlockCore.islandManager?.islandDataOf(target.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("oneblock.island.none_other", target.gameProfile.name))
            return 0
        }
        val rawType = StringArgumentType.getString(context, "type")
        val type = BuffType.parse(rawType)
        if (type == null) {
            context.source.sendFailure(
                ServerLang.msg("oneblock.buff.unknown", rawType, BuffType.entries.joinToString(", ") { it.name.lowercase() }),
            )
            return 0
        }
        val value = DoubleArgumentType.getDouble(context, "value")
        val minutes = IntegerArgumentType.getInteger(context, "minutes")
        BuffService.grant(island.id, type, value, minutes * 60_000L)

        context.source.sendSuccess(
            { ServerLang.msg("oneblock.buff.granted", type.name.lowercase(), value, minutes, target.gameProfile.name) },
            true,
        )
        target.sendSystemMessage(
            ServerLang.msg("oneblock.buff.received", type.name.lowercase(), value, minutes)
                .withStyle(ChatFormatting.LIGHT_PURPLE),
        )
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
        if (!manager.archive(player.uuid, player.server)) {
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
        if (!manager.archive(player.uuid, player.server)) {
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
                        OneBlockCore.lootTable.buildPool(server)
                        CobblemonIntegration.applySpawnMultiplier()
                        source.sendSuccess({ ServerLang.msg("oneblock.reload.ok") }, true)
                    } else {
                        source.sendFailure(ServerLang.msg("oneblock.reload.failed", error))
                    }
                }
            }
        return Command.SINGLE_SUCCESS
    }
}
