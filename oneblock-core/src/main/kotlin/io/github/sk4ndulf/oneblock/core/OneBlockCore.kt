package io.github.sk4ndulf.oneblock.core

import io.github.sk4ndulf.oneblock.api.internal.OneBlockAPIHolder
import io.github.sk4ndulf.oneblock.core.api.EventBusImpl
import io.github.sk4ndulf.oneblock.core.api.OneBlockAPIImpl
import io.github.sk4ndulf.oneblock.core.command.ObCommands
import io.github.sk4ndulf.oneblock.core.config.ConfigManager
import io.github.sk4ndulf.oneblock.core.db.Database
import io.github.sk4ndulf.oneblock.core.db.PlayerRepository
import io.github.sk4ndulf.oneblock.core.island.IslandManagerImpl
import io.github.sk4ndulf.oneblock.core.island.IslandRepository
import io.github.sk4ndulf.oneblock.core.island.OneBlockLootTable
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.oneblock.core.biome.BiomeRepository
import io.github.sk4ndulf.oneblock.core.biome.BiomeService
import io.github.sk4ndulf.oneblock.core.cobblemon.BuffService
import io.github.sk4ndulf.oneblock.core.cobblemon.CobblemonIntegration
import io.github.sk4ndulf.oneblock.core.cobblemon.LegendaryEncounterEvent
import io.github.sk4ndulf.oneblock.core.moderation.BanService
import io.github.sk4ndulf.oneblock.core.permission.ProtectionManager
import io.github.sk4ndulf.oneblock.core.trigger.BossFightEvent
import io.github.sk4ndulf.oneblock.core.trigger.MobWaveEvent
import io.github.sk4ndulf.oneblock.core.trigger.ResourceBurstEvent
import io.github.sk4ndulf.oneblock.core.trigger.TriggerEventService
import io.github.sk4ndulf.oneblock.core.world.HubManager
import io.github.sk4ndulf.oneblock.core.world.OneBlockDimension
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object OneBlockCore : ModInitializer {

    const val MOD_ID = "oneblock"
    val LOGGER: Logger = LoggerFactory.getLogger("CobblemonOneBlock")

    val eventBus = EventBusImpl(LOGGER)
    val configManager = ConfigManager(FabricLoader.getInstance().configDir.resolve(MOD_ID), LOGGER)

    @Volatile
    var database: Database? = null
        private set

    @Volatile
    var islandManager: IslandManagerImpl? = null
        private set

    val lootTable = OneBlockLootTable(FabricLoader.getInstance().configDir.resolve(MOD_ID), LOGGER)

    val playerRepository: PlayerRepository?
        get() = database?.let { PlayerRepository(it) }

    /** Hourly maintenance interval in ticks (20 t/s * 3600 s). */
    private const val MAINTENANCE_INTERVAL_TICKS = 72_000
    private var maintenanceTickCounter = 0

    /** How often wandering Pokémon are pulled back inside island borders (5 s). */
    private const val POKEMON_CONTAINMENT_INTERVAL_TICKS = 100
    private var containmentTickCounter = 0

    override fun onInitialize() {
        LOGGER.info("Cobblemon OneBlock initializing...")

        configManager.loadAll()
        ServerLang.load(configManager.mainConfig.language, LOGGER)
        lootTable.load()

        OneBlockAPIHolder.set(OneBlockAPIImpl(eventBus))
        ObCommands.register()
        ProtectionManager.register()

        // Built-in trigger event types. Addons register their own via
        // OneBlockAPI.get().eventManager().registerEventType(...) during their init.
        TriggerEventService.registerType(MobWaveEvent())
        TriggerEventService.registerType(BossFightEvent())
        TriggerEventService.registerType(ResourceBurstEvent())
        TriggerEventService.registerType(LegendaryEncounterEvent())

        CobblemonIntegration.register()

        PlayerBlockBreakEvents.AFTER.register { level, player, pos, state, _ ->
            if (level is ServerLevel && player is ServerPlayer) {
                islandManager?.handleBreak(level, player, pos, state)
            }
        }

        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            connectDatabase()
            reloadIslands()
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            HubManager.onServerStarted(server)
            lootTable.buildPool(server)
            CobblemonIntegration.checkVersion()
            CobblemonIntegration.applySpawnMultiplier()
            OneBlockDimension.level(server)?.let { BiomeService.reapplyAll(it) }
            islandManager?.runMaintenance(server)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            TriggerEventService.tick(server)
            if (++containmentTickCounter >= POKEMON_CONTAINMENT_INTERVAL_TICKS) {
                containmentTickCounter = 0
                CobblemonIntegration.containWanderingPokemon(server)
                BanService.enforce(server)
            }
            if (++maintenanceTickCounter >= MAINTENANCE_INTERVAL_TICKS) {
                maintenanceTickCounter = 0
                islandManager?.runMaintenance(server)
                BuffService.purgeExpired()
            }
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            TriggerEventService.shutdown(server)
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
            database?.close()
            database = null
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            playerRepository?.recordSeenAsync(player.uuid, player.gameProfile.name)?.thenAccept { firstJoin ->
                server.execute {
                    if (player.hasDisconnected()) return@execute
                    if (firstJoin) {
                        HubManager.sendToHub(player)
                    } else {
                        rescueStrandedPlayer(player)
                    }
                }
            }
            if (!configManager.mainConfig.setupCompleted && player.hasPermissions(4)) {
                player.sendSystemMessage(
                    ServerLang.msg("oneblock.welcome.op")
                        .withStyle(ChatFormatting.GOLD)
                        .withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ob setup")) },
                )
            }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val player = handler.player
            playerRepository?.recordSeenAsync(player.uuid, player.gameProfile.name)
        }

        LOGGER.info("Cobblemon OneBlock initialized (API v{}).", OneBlockAPIImpl.API_VERSION)
    }

    /**
     * (Re-)connects the database from the current database config.
     * Returns null on success, otherwise a human-readable error message.
     */
    /**
     * Sends a player back to the hub when they log in inside the OneBlock world without
     * owning or belonging to an island there — for example after their island was
     * archived by the inactivity purge while they were away. Without this they would
     * stand in dead space, unable to build, with no explanation.
     */
    private fun rescueStrandedPlayer(player: ServerPlayer) {
        if (player.level().dimension() != OneBlockDimension.WORLD_KEY) return
        if (islandManager?.islandDataOf(player.uuid) != null) return
        if (HubManager.isInHubArea(player.blockPosition())) return

        HubManager.sendToHub(player)
        player.sendSystemMessage(
            ServerLang.msg("oneblock.island.stranded").withStyle(ChatFormatting.GOLD),
        )
    }

    /** (Re-)creates the island registry from the database. Server thread only. */
    fun reloadIslands() {
        val db = database
        if (db == null) {
            islandManager = null
            return
        }
        islandManager = IslandManagerImpl(IslandRepository(db)).also { it.loadAll() }
        BuffService.loadAll(db)
        BiomeService.reload(BiomeRepository(db))
        BanService.reload(db)
    }

    fun connectDatabase(): String? {
        database?.close()
        database = null
        islandManager = null
        return try {
            val db = Database.connect(configManager.databaseConfig, configManager.configDir, LOGGER)
            db.runMigrations()
            database = db
            LOGGER.info("Database connected ({}), schema version {}.", db.dialect, db.schemaVersion())
            null
        } catch (e: Exception) {
            LOGGER.error("Database connection failed: {}", e.message)
            "Database connection failed: ${e.message}"
        }
    }
}
