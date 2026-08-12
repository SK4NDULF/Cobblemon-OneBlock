package io.github.sk4ndulf.oneblock.core.api

import io.github.sk4ndulf.oneblock.api.OneBlockAPI
import io.github.sk4ndulf.oneblock.api.event.OneBlockEventBus

class OneBlockAPIImpl(private val eventBus: OneBlockEventBus) : OneBlockAPI {

    companion object {
        const val API_VERSION = "0.1.0"
    }

    override fun eventBus(): OneBlockEventBus = eventBus

    override fun apiVersion(): String = API_VERSION
}
