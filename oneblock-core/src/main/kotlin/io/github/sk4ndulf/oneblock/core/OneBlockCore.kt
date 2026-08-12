package io.github.sk4ndulf.oneblock.core

import io.github.sk4ndulf.oneblock.api.internal.OneBlockAPIHolder
import io.github.sk4ndulf.oneblock.core.api.EventBusImpl
import io.github.sk4ndulf.oneblock.core.api.OneBlockAPIImpl
import io.github.sk4ndulf.oneblock.core.command.ObCommands
import io.github.sk4ndulf.oneblock.core.config.ConfigManager
import io.github.sk4ndulf.oneblock.core.db.Database
import io.github.sk4ndulf.oneblock.core.db.PlayerRepository
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
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

    val playerRepository: PlayerRepository?
        get() = database?.let { PlayerRepository(it) }

    override fun onInitialize() {
        LOGGER.info("Cobblemon OneBlock initializing...")

        configManager.loadAll()

        OneBlockAPIHolder.set(OneBlockAPIImpl(eventBus))
        ObCommands.register()

        ServerLifecycleEvents.SERVER_STARTING.register { _ ->
            connectDatabase()
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
            database?.close()
            database = null
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            playerRepository?.recordSeenAsync(player.uuid, player.gameProfile.name)
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
    fun connectDatabase(): String? {
        database?.close()
        database = null
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
