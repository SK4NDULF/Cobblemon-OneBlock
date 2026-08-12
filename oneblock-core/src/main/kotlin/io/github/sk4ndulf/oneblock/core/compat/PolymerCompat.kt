package io.github.sk4ndulf.oneblock.core.compat

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

/**
 * Detects content registered through [Polymer](https://github.com/Patbox/polymer).
 *
 * Polymer content is server-side only: the block a vanilla client sees is a stand-in, not
 * the registered block. Such blocks appear in the vanilla registry, so the "all blocks"
 * loot pool would happily pick them — and players would mine something that does not
 * behave, drop or look like what the server thinks it is. They are excluded by default.
 *
 * Detection is reflective on purpose: this mod must not depend on Polymer, and on a server
 * without it the check simply never matches.
 */
object PolymerCompat {

    private val BLOCK_INTERFACES = listOf(
        "eu.pb4.polymer.core.api.block.PolymerBlock",   // Polymer 0.5+ (current)
        "eu.pb4.polymer.api.block.PolymerBlock",        // legacy layout
    )

    private val ITEM_INTERFACES = listOf(
        "eu.pb4.polymer.core.api.item.PolymerItem",
        "eu.pb4.polymer.api.item.PolymerItem",
    )

    private var initialized = false
    private var blockClasses: List<Class<*>> = emptyList()
    private var itemClasses: List<Class<*>> = emptyList()

    /** True when Polymer is on the classpath and content can be identified. */
    val available: Boolean
        get() {
            init()
            return blockClasses.isNotEmpty() || itemClasses.isNotEmpty()
        }

    private fun init() {
        if (initialized) return
        initialized = true
        blockClasses = BLOCK_INTERFACES.mapNotNull { resolve(it) }
        itemClasses = ITEM_INTERFACES.mapNotNull { resolve(it) }
        if (blockClasses.isNotEmpty() || itemClasses.isNotEmpty()) {
            OneBlockCore.LOGGER.info("Polymer detected — Polymer blocks and items are excluded from the loot pool.")
        }
    }

    private fun resolve(name: String): Class<*>? = try {
        Class.forName(name, false, PolymerCompat::class.java.classLoader)
    } catch (_: Throwable) {
        null
    }

    /** True when the block itself, or the item it drops as, is Polymer content. */
    fun isPolymerContent(block: Block): Boolean {
        init()
        if (blockClasses.any { it.isInstance(block) }) return true
        val item: Item = block.asItem()
        return itemClasses.any { it.isInstance(item) }
    }
}
