package io.github.sk4ndulf.cobblemon.oneblock.core.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.cobblemon.BuffService
import io.github.sk4ndulf.cobblemon.oneblock.core.cobblemon.BuffType
import io.github.sk4ndulf.cobblemon.oneblock.core.biome.BiomeEditor
import io.github.sk4ndulf.cobblemon.oneblock.core.biome.BiomeService
import io.github.sk4ndulf.cobblemon.oneblock.core.cobblemon.CobblemonIntegration
import io.github.sk4ndulf.cobblemon.oneblock.core.island.IslandData
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import io.github.sk4ndulf.cobblemon.oneblock.core.island.PartyService
import io.github.sk4ndulf.cobblemon.oneblock.core.moderation.BanService
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.cobblemon.oneblock.core.wizard.SetupWizard
import io.github.sk4ndulf.cobblemon.oneblock.core.wizard.WizardQuestions
import io.github.sk4ndulf.cobblemon.oneblock.core.world.HubManager
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
                            .executes { requestDestructive(it, "reset", "cobblemon_oneblock.island.reset_confirm") }
                            .then(Commands.literal("confirm").executes { confirmReset(it) })
                    )
                    .then(
                        Commands.literal("delete")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_DELETE, 0) }
                            .executes { requestDestructive(it, "delete", "cobblemon_oneblock.island.delete_confirm") }
                            .then(Commands.literal("confirm").executes { confirmDelete(it) })
                    )
                    .then(
                        Commands.literal("visit")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_VISIT, 0) }
                            .then(
                                Commands.argument("player", GameProfileArgument.gameProfile())
                                    .executes(::visit)
                            )
                    )
                    .then(
                        Commands.literal("info")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_INFO, 0) }
                            .executes(::info)
                    )
                    .then(TechCommands.build())
                    .then(
                        Commands.literal("biome")
                            .requires { ObPermissions.check(it, ObPermissions.COMMAND_BIOME, 0) }
                            .then(Commands.literal("pos1")
                                .executes { BiomeService.setCorner(it.source.playerOrException, true); Command.SINGLE_SUCCESS })
                            .then(Commands.literal("pos2")
                                .executes { BiomeService.setCorner(it.source.playerOrException, false); Command.SINGLE_SUCCESS })
                            .then(
                                Commands.literal("set").then(
                                    Commands.argument("biome", StringArgumentType.string())
                                        .suggests { context, builder ->
                                            val level = OneBlockDimension.level(context.source.server)
                                            SharedSuggestionProvider.suggest(
                                                level?.let { BiomeEditor.knownBiomeIds(it) } ?: emptyList(), builder,
                                            )
                                        }
                                        .executes(::biomeSet)
                                )
                            )
                            .then(Commands.literal("list").executes(::biomeList))
                            .then(
                                Commands.literal("remove").then(
                                    Commands.argument("index", IntegerArgumentType.integer(1))
                                        .executes(::biomeRemove)
                                )
                            )
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
                    .then(ModerationCommands.banBranch())
                    .then(ModerationCommands.unbanBranch())
                    .then(ModerationCommands.bansBranch())
                    .then(ModerationCommands.adminBranch())
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
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.setup_pending"))
            return 0
        }
        if (manager == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.error.not_ready"))
            return 0
        }
        if (manager.islandDataOf(player.uuid) != null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.already_have"))
            return 0
        }
        manager.create(player)
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.island.created").withStyle(ChatFormatting.GREEN) }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun home(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return 0
        }
        OneBlockCore.islandManager?.sendHome(player, island)
        context.source.sendSuccess({ ServerLang.msg("cobblemon_oneblock.island.teleported_home") }, false)
        return Command.SINGLE_SUCCESS
    }

    /**
     * Teleports to another player's island. This is what makes the visitor role reachable
     * at all — the islands are thousands of blocks apart, so without it "public server"
     * and the visitor permissions would be theoretical.
     */
    private fun visit(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val profile = GameProfileArgument.getGameProfiles(context, "player").firstOrNull() ?: return 0
        val manager = OneBlockCore.islandManager
        val island = manager?.islandDataOf(profile.id)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none_other", profile.name))
            return 0
        }
        val isAdmin = ObPermissions.checkPlayer(player, ObPermissions.ADMIN_BYPASS, 2)

        // Members visit their own island freely; everyone else needs a public server.
        if (!island.isMemberOrOwner(player.uuid) && !isAdmin &&
            !OneBlockCore.configManager.mainConfig.serverPublic
        ) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.visit.server_private"))
            return 0
        }
        if (!isAdmin && BanService.isBanned(island.id, player.uuid)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.visit.banned"))
            return 0
        }

        manager.sendToIsland(player, island)
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.visit.arrived", profile.name).withStyle(ChatFormatting.GREEN) }, false,
        )
        // Let the island know someone dropped by — useful on a public server.
        context.source.server.playerList.getPlayer(island.owner())
            ?.takeIf { it.uuid != player.uuid }
            ?.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.visit.owner_notice", player.gameProfile.name))
        return Command.SINGLE_SUCCESS
    }

    private fun spawn(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        HubManager.sendToHub(player)
        context.source.sendSuccess({ ServerLang.msg("cobblemon_oneblock.island.teleported_spawn") }, false)
        return Command.SINGLE_SUCCESS
    }

    private fun info(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return 0
        }
        val config = OneBlockCore.configManager.mainConfig
        val size = io.github.sk4ndulf.cobblemon.oneblock.core.world.GridMath.borderSizeAt(island.borderLevel, config)
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.info.header", island.borderLevel, size, size).withStyle(ChatFormatting.GOLD),
        )
        context.source.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.info.breaks", island.breakCount))
        if (island.borderLevel < io.github.sk4ndulf.cobblemon.oneblock.core.island.ProgressionService.MAX_LEVEL) {
            val next = io.github.sk4ndulf.cobblemon.oneblock.core.island.ProgressionService.pointsRequiredFor(island.borderLevel + 1)
            context.source.sendSystemMessage(
                ServerLang.msg("cobblemon_oneblock.info.points", "%,.0f".format(island.points), "%,.0f".format(next)),
            )
        } else {
            context.source.sendSystemMessage(ServerLang.msg("cobblemon_oneblock.info.max_level"))
        }
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.info.party", island.memberSet.size + 1, config.maxPartySize),
        )
        return Command.SINGLE_SUCCESS
    }

    // --- biome editor ----------------------------------------------------------------------

    /** Shared guard: the player must be owner or member of an active island. */
    private fun islandForEditing(context: CommandContext<CommandSourceStack>): IslandData? {
        val player = context.source.playerOrException
        val island = OneBlockCore.islandManager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return null
        }
        return island
    }

    private fun biomeSet(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val island = islandForEditing(context) ?: return 0
        val biomeId = StringArgumentType.getString(context, "biome")

        return when (val result = BiomeService.applySelection(player, island, biomeId)) {
            is BiomeService.SetResult.Error -> {
                context.source.sendFailure(net.minecraft.network.chat.Component.literal(result.message))
                0
            }
            is BiomeService.SetResult.Success -> {
                context.source.sendSuccess(
                    {
                        ServerLang.msg(
                            "cobblemon_oneblock.biome.applied",
                            result.region.biomeId, result.region.sizeDescription, result.cells,
                        ).withStyle(ChatFormatting.GREEN)
                    },
                    false,
                )
                Command.SINGLE_SUCCESS
            }
        }
    }

    private fun biomeList(context: CommandContext<CommandSourceStack>): Int {
        val island = islandForEditing(context) ?: return 0
        val regions = BiomeService.regionsOf(island.id)
        val limit = OneBlockCore.configManager.mainConfig.maxBiomeRegions
        context.source.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.biome.list_header", regions.size, limit).withStyle(ChatFormatting.GOLD),
        )
        regions.forEachIndexed { index, region ->
            context.source.sendSystemMessage(
                ServerLang.msg(
                    "cobblemon_oneblock.biome.list_entry",
                    index + 1, region.biomeId, region.sizeDescription,
                    region.minX, region.minY, region.minZ,
                ),
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun biomeRemove(context: CommandContext<CommandSourceStack>): Int {
        val island = islandForEditing(context) ?: return 0
        val index = IntegerArgumentType.getInteger(context, "index")
        val removed = BiomeService.removeRegion(island, index)
        if (removed == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.biome.no_such_region", index))
            return 0
        }
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.biome.removed", removed.biomeId) }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun setVisitorSetting(context: CommandContext<CommandSourceStack>, catch: Boolean): Int {
        val player = context.source.playerOrException
        val manager = OneBlockCore.islandManager
        val island = manager?.islandDataOf(player.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return 0
        }
        if (island.owner() != player.uuid) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.only_owner"))
            return 0
        }
        val allowed = BoolArgumentType.getBool(context, "allowed")
        if (catch) island.allowVisitorCatch = allowed else island.allowVisitorBattle = allowed
        manager.persistVisitorSettings(island)

        val labelKey = if (catch) "cobblemon_oneblock.settings.visitor_catch" else "cobblemon_oneblock.settings.visitor_battle"
        val stateKey = if (allowed) "cobblemon_oneblock.value.yes" else "cobblemon_oneblock.value.no"
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.settings.saved", ServerLang.raw(labelKey), ServerLang.raw(stateKey)) }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun grantBuff(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "player")
        val island = OneBlockCore.islandManager?.islandDataOf(target.uuid)
        if (island == null) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none_other", target.gameProfile.name))
            return 0
        }
        val rawType = StringArgumentType.getString(context, "type")
        val type = BuffType.parse(rawType)
        if (type == null) {
            context.source.sendFailure(
                ServerLang.msg("cobblemon_oneblock.buff.unknown", rawType, BuffType.entries.joinToString(", ") { it.name.lowercase() }),
            )
            return 0
        }
        val value = DoubleArgumentType.getDouble(context, "value")
        val minutes = IntegerArgumentType.getInteger(context, "minutes")
        BuffService.grant(island.id, type, value, minutes * 60_000L)

        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.buff.granted", type.name.lowercase(), value, minutes, target.gameProfile.name) },
            true,
        )
        target.sendSystemMessage(
            ServerLang.msg("cobblemon_oneblock.buff.received", type.name.lowercase(), value, minutes)
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
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return 0
        }
        if (island.owner() != player.uuid) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.only_owner"))
            return 0
        }
        Confirmations.request(player.uuid, action)
        val message = ServerLang.msg(confirmLangKey, OneBlockCore.configManager.mainConfig.resetArchiveDays)
            .withStyle(ChatFormatting.GOLD)
            .append(" ")
            .append(
                ServerLang.msg("cobblemon_oneblock.confirm.button")
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
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.confirm.expired"))
            return 0
        }
        if (!manager.archive(player.uuid, player.server)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return 0
        }
        manager.create(player)
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.island.reset_done").withStyle(ChatFormatting.GREEN) }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun confirmDelete(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val manager = OneBlockCore.islandManager ?: return 0
        if (!Confirmations.consume(player.uuid, "delete")) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.confirm.expired"))
            return 0
        }
        if (!manager.archive(player.uuid, player.server)) {
            context.source.sendFailure(ServerLang.msg("cobblemon_oneblock.island.none"))
            return 0
        }
        HubManager.sendToHub(player)
        context.source.sendSuccess(
            { ServerLang.msg("cobblemon_oneblock.island.delete_done") }, false,
        )
        return Command.SINGLE_SUCCESS
    }

    private fun reload(context: CommandContext<CommandSourceStack>): Int {
        val source = context.source
        val server = source.server

        source.sendSuccess({ ServerLang.msg("cobblemon_oneblock.reload.start") }, true)
        OneBlockCore.configManager.loadAll()
        ServerLang.load(OneBlockCore.configManager.mainConfig.language, OneBlockCore.LOGGER)
        OneBlockCore.lootTable.load()
        OneBlockCore.loadTechTree()

        // Reconnecting the pool and testing the connection blocks — keep it off the server thread.
        CompletableFuture.supplyAsync { OneBlockCore.connectDatabase() }
            .thenAccept { error ->
                server.execute {
                    if (error == null) {
                        OneBlockCore.reloadIslands()
                        OneBlockCore.lootTable.buildPool(server)
                        CobblemonIntegration.applySpawnMultiplier()
                        source.sendSuccess({ ServerLang.msg("cobblemon_oneblock.reload.ok") }, true)
                    } else {
                        source.sendFailure(ServerLang.msg("cobblemon_oneblock.reload.failed", error))
                    }
                }
            }
        return Command.SINGLE_SUCCESS
    }
}
