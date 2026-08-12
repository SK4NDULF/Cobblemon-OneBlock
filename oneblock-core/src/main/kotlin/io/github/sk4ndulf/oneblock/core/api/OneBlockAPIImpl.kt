package io.github.sk4ndulf.oneblock.core.api

import io.github.sk4ndulf.oneblock.api.OneBlockAPI
import io.github.sk4ndulf.oneblock.api.event.OneBlockEventBus
import io.github.sk4ndulf.oneblock.api.island.IslandManager
import io.github.sk4ndulf.oneblock.api.permission.PermissionManager
import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.permission.PermissionManagerImpl

class OneBlockAPIImpl(private val eventBus: OneBlockEventBus) : OneBlockAPI {

    companion object {
        const val API_VERSION = "0.1.0"
    }

    private val permissionManager = PermissionManagerImpl()

    override fun eventBus(): OneBlockEventBus = eventBus

    override fun islandManager(): IslandManager =
        OneBlockCore.islandManager
            ?: throw IllegalStateException("IslandManager is not available before the server has started.")

    override fun permissionManager(): PermissionManager = permissionManager

    override fun apiVersion(): String = API_VERSION
}
