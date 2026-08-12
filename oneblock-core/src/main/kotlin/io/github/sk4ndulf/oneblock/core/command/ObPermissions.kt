package io.github.sk4ndulf.oneblock.core.command

import net.minecraft.commands.CommandSourceStack

/**
 * Permission abstraction. Every command checks a node here.
 *
 * Currently backed by vanilla OP levels so the mod works drag-and-drop without a
 * permission mod. A LuckPerms integration (node-based, optional) plugs into [check]
 * in a later phase without touching any command code.
 */
object ObPermissions {

    // Node constants — one node per command, defined the moment the command exists.
    const val COMMAND_ROOT = "oneblock.command"
    const val COMMAND_CREATE = "oneblock.command.create"
    const val COMMAND_HOME = "oneblock.command.home"
    const val COMMAND_SPAWN = "oneblock.command.spawn"
    const val COMMAND_RESET = "oneblock.command.reset"
    const val COMMAND_DELETE = "oneblock.command.delete"
    const val COMMAND_PARTY = "oneblock.command.party"
    const val COMMAND_INFO = "oneblock.command.info"
    const val COMMAND_SETTINGS = "oneblock.command.settings"
    const val ADMIN_BUFF = "oneblock.admin.buff"
    const val ADMIN_RELOAD = "oneblock.admin.reload"
    const val ADMIN_SETUP = "oneblock.admin.setup"

    /**
     * @param node    the permission node (used once LuckPerms integration lands)
     * @param opLevel vanilla fallback: required OP level when no permission mod is present
     */
    fun check(source: CommandSourceStack, node: String, opLevel: Int): Boolean {
        return source.hasPermission(opLevel)
    }
}
