package io.github.sk4ndulf.oneblock.core.command

import net.minecraft.commands.CommandSourceStack

/**
 * Permission checks for every command.
 *
 * Resolution order:
 *  1. LuckPerms (or any provider it fronts), when installed — an explicitly set node
 *     wins, whether it grants or denies.
 *  2. Vanilla OP level, when the node is undefined or no permission mod is present.
 *
 * That keeps the mod drag-and-drop (works with zero setup) while giving networks with
 * LuckPerms full per-group control. Nodes are documented in ADMIN.md.
 */
object ObPermissions {

    // Player commands (OP level 0 = everyone by default).
    const val COMMAND_ROOT = "oneblock.command"
    const val COMMAND_CREATE = "oneblock.command.create"
    const val COMMAND_HOME = "oneblock.command.home"
    const val COMMAND_SPAWN = "oneblock.command.spawn"
    const val COMMAND_VISIT = "oneblock.command.visit"
    const val COMMAND_RESET = "oneblock.command.reset"
    const val COMMAND_DELETE = "oneblock.command.delete"
    const val COMMAND_PARTY = "oneblock.command.party"
    const val COMMAND_INFO = "oneblock.command.info"
    const val COMMAND_SETTINGS = "oneblock.command.settings"
    const val COMMAND_BIOME = "oneblock.command.biome"
    const val COMMAND_BAN = "oneblock.command.ban"

    // Admin commands (OP level 4 by default).
    const val ADMIN_RELOAD = "oneblock.admin.reload"
    const val ADMIN_SETUP = "oneblock.admin.setup"
    const val ADMIN_BUFF = "oneblock.admin.buff"
    const val ADMIN_MODERATE = "oneblock.admin.moderate"

    /** Protection bypass, checked in ProtectionManager rather than on a command. */
    const val ADMIN_BYPASS = "oneblock.admin.bypass"

    /**
     * @param node    the permission node
     * @param opLevel vanilla fallback level when the node is undefined
     */
    fun check(source: CommandSourceStack, node: String, opLevel: Int): Boolean {
        val player = source.player ?: return source.hasPermission(opLevel) // console / command blocks
        return when (LuckPermsBridge.check(player, node)) {
            LuckPermsBridge.Result.ALLOW -> true
            LuckPermsBridge.Result.DENY -> false
            LuckPermsBridge.Result.UNDEFINED -> source.hasPermission(opLevel)
        }
    }

    /** Same resolution for non-command checks (protection bypass). */
    fun checkPlayer(player: net.minecraft.server.level.ServerPlayer, node: String, opLevel: Int): Boolean =
        when (LuckPermsBridge.check(player, node)) {
            LuckPermsBridge.Result.ALLOW -> true
            LuckPermsBridge.Result.DENY -> false
            LuckPermsBridge.Result.UNDEFINED -> player.hasPermissions(opLevel)
        }
}
