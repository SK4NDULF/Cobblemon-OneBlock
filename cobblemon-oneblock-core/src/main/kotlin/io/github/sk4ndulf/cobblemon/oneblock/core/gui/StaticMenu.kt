package io.github.sk4ndulf.cobblemon.oneblock.core.gui

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

/**
 * A read-only chest menu: it shows items and reports clicks, and nothing can ever be taken
 * out of it or put into it.
 *
 * This is the whole reason a server-side mod can have a GUI on **vanilla clients** — a chest
 * menu is plain vanilla protocol, so no client mod is involved. What it costs is that every
 * vanilla inventory interaction has to be suppressed by hand: [clicked] is overridden to run
 * our handler instead of vanilla's item movement, and [quickMoveStack] returns empty so
 * shift-clicking cannot drag an icon into the player's inventory.
 */
class StaticMenu(
    syncId: Int,
    playerInventory: Inventory,
    private val rows: Int,
    container: SimpleContainer,
    private val onClick: (slot: Int) -> Unit,
) : ChestMenu(menuTypeFor(rows), syncId, playerInventory, container, rows) {

    private val menuSize = rows * SLOTS_PER_ROW

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        // Deliberately does not call super: vanilla would move items around.
        if (slotId in 0 until menuSize) {
            onClick(slotId)
        }
        // The client predicted a change that never happened on the server. Resync — unless
        // the handler already opened a different menu, which sends its own open packet.
        if (player is ServerPlayer && player.containerMenu === this) {
            sendAllDataToRemote()
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    override fun stillValid(player: Player): Boolean = true

    companion object {
        const val SLOTS_PER_ROW = 9

        private fun menuTypeFor(rows: Int): MenuType<*> = when (rows) {
            1 -> MenuType.GENERIC_9x1
            2 -> MenuType.GENERIC_9x2
            3 -> MenuType.GENERIC_9x3
            4 -> MenuType.GENERIC_9x4
            5 -> MenuType.GENERIC_9x5
            else -> MenuType.GENERIC_9x6
        }

        /**
         * Opens a menu built from [contents] (slot → stack) and routes clicks to [onClick].
         *
         * The open is scheduled onto the next server tick rather than run inline, because this
         * is normally called *from* a click handler: replacing a menu while the packet for the
         * previous one is still being processed leaves the client showing a screen the server
         * has already forgotten.
         */
        fun open(
            player: ServerPlayer,
            title: Component,
            rows: Int,
            contents: Map<Int, ItemStack>,
            onClick: (slot: Int) -> Unit,
        ) {
            val container = SimpleContainer(rows * SLOTS_PER_ROW)
            for ((slot, stack) in contents) {
                if (slot in 0 until rows * SLOTS_PER_ROW) container.setItem(slot, stack)
            }
            player.server.execute {
                if (player.hasDisconnected()) return@execute
                player.openMenu(
                    SimpleMenuProvider(
                        { syncId, inventory, _ -> StaticMenu(syncId, inventory, rows, container, onClick) },
                        title,
                    ),
                )
            }
        }
    }
}
