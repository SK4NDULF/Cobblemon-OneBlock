package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island
import io.github.sk4ndulf.cobblemon.oneblock.api.loot.LootRegistry
import io.github.sk4ndulf.cobblemon.oneblock.api.loot.OneBlockLootProvider
import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState

/**
 * Addon-provided loot providers, consulted before the configured pool.
 * A provider that throws is logged and skipped — never breaks the OneBlock.
 */
object LootRegistryImpl : LootRegistry {

    private val providers = LinkedHashMap<ResourceLocation, OneBlockLootProvider>()

    override fun register(provider: OneBlockLootProvider) {
        val previous = providers.put(provider.id(), provider)
        if (previous != null) {
            OneBlockCore.LOGGER.info("Loot provider {} was replaced by a new registration.", provider.id())
        } else {
            OneBlockCore.LOGGER.info("Loot provider registered: {}", provider.id())
        }
    }

    override fun unregister(id: ResourceLocation): Boolean = providers.remove(id) != null

    override fun registered(): Set<ResourceLocation> = providers.keys.toSet()

    /** First provider that returns a block wins; null means "use the configured pool". */
    fun query(island: Island, level: ServerLevel): BlockState? {
        if (providers.isEmpty()) return null
        for (provider in providers.values) {
            val result = try {
                provider.nextBlock(island, level)
            } catch (e: Exception) {
                OneBlockCore.LOGGER.error("Loot provider {} threw — skipped for this break.", provider.id(), e)
                continue
            }
            if (result.isPresent) return result.get()
        }
        return null
    }
}
