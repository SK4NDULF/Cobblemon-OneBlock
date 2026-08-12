package io.github.sk4ndulf.oneblock.core.permission

import io.github.sk4ndulf.oneblock.core.OneBlockCore
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import io.github.sk4ndulf.oneblock.core.world.HubManager
import io.github.sk4ndulf.oneblock.core.world.OneBlockDimension
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.Level

/**
 * Central protection rule engine for the OneBlock world (deny-by-default):
 *
 *  - Hub circle: nobody modifies anything (admins may, if `hub_allow_building`).
 *  - Island footprint: owner/members may modify INSIDE the current border level area;
 *    outside the current border (but inside the footprint) is locked until level-up.
 *  - Visitors on foreign islands: enter and look — no breaking, placing, or interacting.
 *  - Void buffer between islands: fallback VOID owner, nobody modifies (deny-by-default
 *    for unregistered space).
 *  - Admins with op level >= 2 bypass island rules (`oneblock.admin.bypass`).
 *
 * Other dimensions are never policed. Explosion/fluid containment lives in the mixins,
 * sharing the same region logic via MixinHooks.
 */
object ProtectionManager {

    /** Why a modification was denied — maps to the message shown to the player. */
    enum class Denial(val langKey: String) {
        HUB("oneblock.hub.no_modify"),
        FOREIGN_ISLAND("oneblock.protect.no_permission"),
        OUTSIDE_BORDER("oneblock.protect.outside_border"),
        VOID_BUFFER("oneblock.protect.void_buffer"),
    }

    fun register() {
        PlayerBlockBreakEvents.BEFORE.register { level, player, pos, _, _ ->
            val denial = denialFor(level, player, pos)
            if (denial != null) {
                (player as? ServerPlayer)?.displayClientMessage(ServerLang.msg(denial.langKey), true)
                false
            } else {
                true
            }
        }

        UseBlockCallback.EVENT.register { player, level, hand, hitResult ->
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            val placing = player.getItemInHand(hand).item is BlockItem
            val targets = if (placing) {
                listOf(hitResult.blockPos, hitResult.blockPos.relative(hitResult.direction))
            } else {
                listOf(hitResult.blockPos)
            }
            for (target in targets) {
                val denial = denialFor(level, player, target)
                if (denial != null) {
                    player.displayClientMessage(ServerLang.msg(denial.langKey), true)
                    return@register InteractionResult.FAIL
                }
            }
            InteractionResult.PASS
        }
    }

    /** Null = allowed. Everything else names the reason for the denial. */
    fun denialFor(level: Level, player: Player, pos: BlockPos): Denial? {
        if (level.dimension() != OneBlockDimension.WORLD_KEY) return null

        if (HubManager.isInHub(level, pos)) {
            val config = OneBlockCore.configManager.mainConfig
            return if (config.hubAllowBuilding && player.hasPermissions(2)) null else Denial.HUB
        }

        val bypass = player.hasPermissions(2)
        val manager = OneBlockCore.islandManager
            ?: return if (bypass) null else Denial.VOID_BUFFER
        val island = manager.islandAt(pos)
            ?: return if (bypass) null else Denial.VOID_BUFFER

        if (!island.isMemberOrOwner(player.uuid)) {
            return if (bypass) null else Denial.FOREIGN_ISLAND
        }
        if (!manager.isWithinCurrentBorder(island, pos)) {
            return if (bypass) null else Denial.OUTSIDE_BORDER
        }
        return null
    }
}
