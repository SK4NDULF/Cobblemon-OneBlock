package io.github.sk4ndulf.cobblemon.oneblock.core.command

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
    const val COMMAND_ROOT = "cobblemon_oneblock.command"
    const val COMMAND_CREATE = "cobblemon_oneblock.command.create"
    const val COMMAND_HOME = "cobblemon_oneblock.command.home"
    const val COMMAND_SPAWN = "cobblemon_oneblock.command.spawn"
    const val COMMAND_VISIT = "cobblemon_oneblock.command.visit"
    const val COMMAND_RESET = "cobblemon_oneblock.command.reset"
    const val COMMAND_DELETE = "cobblemon_oneblock.command.delete"
    const val COMMAND_PARTY = "cobblemon_oneblock.command.party"
    const val COMMAND_INFO = "cobblemon_oneblock.command.info"
    const val COMMAND_SETTINGS = "cobblemon_oneblock.command.settings"
    const val COMMAND_BIOME = "cobblemon_oneblock.command.biome"
    const val COMMAND_BAN = "cobblemon_oneblock.command.ban"

    // Admin commands (OP level 4 by default).
    const val ADMIN_RELOAD = "cobblemon_oneblock.admin.reload"
    const val ADMIN_SETUP = "cobblemon_oneblock.admin.setup"
    const val ADMIN_BUFF = "cobblemon_oneblock.admin.buff"
    const val ADMIN_MODERATE = "cobblemon_oneblock.admin.moderate"

    /** Protection bypass, checked in ProtectionManager rather than on a command. */
    const val ADMIN_BYPASS = "cobblemon_oneblock.admin.bypass"

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
