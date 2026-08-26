package io.github.sk4ndulf.cobblemon.oneblock.core.api

import io.github.sk4ndulf.cobblemon.oneblock.api.OneBlockAPI
import io.github.sk4ndulf.cobblemon.oneblock.api.event.OneBlockEventBus
import io.github.sk4ndulf.cobblemon.oneblock.api.island.IslandManager
import io.github.sk4ndulf.cobblemon.oneblock.api.loot.LootRegistry
import io.github.sk4ndulf.cobblemon.oneblock.core.island.LootRegistryImpl
import io.github.sk4ndulf.cobblemon.oneblock.api.party.PartyManager
import io.github.sk4ndulf.cobblemon.oneblock.api.permission.PermissionManager
import io.github.sk4ndulf.cobblemon.oneblock.api.progression.ProgressionManager
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.island.PartyManagerImpl
import io.github.sk4ndulf.cobblemon.oneblock.core.island.ProgressionManagerImpl
import io.github.sk4ndulf.cobblemon.oneblock.core.permission.PermissionManagerImpl

class OneBlockAPIImpl(private val eventBus: OneBlockEventBus) : OneBlockAPI {

    companion object {
        const val API_VERSION = "0.1.0"
    }

    private val permissionManager = PermissionManagerImpl()
    private val partyManager = PartyManagerImpl()
    private val progressionManager = ProgressionManagerImpl()

    override fun eventBus(): OneBlockEventBus = eventBus

    override fun islandManager(): IslandManager =
        OneBlockCore.islandManager
            ?: throw IllegalStateException("IslandManager is not available before the server has started.")

    override fun permissionManager(): PermissionManager = permissionManager

    override fun partyManager(): PartyManager = partyManager

    override fun progressionManager(): ProgressionManager = progressionManager


    override fun lootRegistry(): LootRegistry = LootRegistryImpl

    override fun apiVersion(): String = API_VERSION
}
