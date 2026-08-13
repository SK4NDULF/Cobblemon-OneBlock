package io.github.sk4ndulf.cobblemon.oneblock.core.island

import io.github.sk4ndulf.cobblemon.oneblock.core.OneBlockCore
import io.github.sk4ndulf.cobblemon.oneblock.core.world.OneBlockDimension
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Sends what a broken OneBlock produced straight into the breaker's inventory.
 *
 * Without this the drops are simply lost. The replacement block is placed in the same tick,
 * the items spawn inside it, get pushed out sideways and fall past the anchor into the void.
 * Collecting them is also what makes the bedrock foundation under the anchor optional rather
 * than load-bearing.
 *
 * **Why a queue instead of doing this inside the break hook.** Fabric fires
 * `PlayerBlockBreakEvents.AFTER` at `Block#destroy`, and vanilla calls that *before*
 * `Block#playerDestroy` — the call that actually drops anything. At break time the items do
 * not exist yet. So the break only records what to look for, and the sweep runs at the end of
 * the tick, once vanilla has spawned them.
 *
 * **Why sweep the ground instead of computing the drops.** `Block#getDrops` would miss two
 * things this way catches for free: the contents of a broken treasure chest (those come from
 * `Containers#dropContents`, not from a loot table) and anything another mod adds on break.
 * Enchantments, Fortune and Silk Touch also stay vanilla's business, because vanilla still
 * does the dropping.
 *
 * Only the anchor is covered. Blocks a player places and re-breaks elsewhere on the island
 * behave normally — that is ordinary skyblock, and players expect it.
 */
object DropCollector {

    /** Half-width of the box searched around the anchor. Items barely move in two ticks. */
    private const val SWEEP_RADIUS = 1.5

    /**
     * Only entities this young are taken. Without it the sweep would also vacuum up items a
     * player deliberately dropped next to the anchor, or trigger event rewards lying nearby.
     */
    private const val MAX_AGE_TICKS = 4

    /** How many ticks one break stays watched. One is enough; two is insurance against ordering. */
    private const val TICKS_WATCHED = 2

    private data class Pending(val pos: BlockPos, val player: UUID, var ticksLeft: Int)

    private val pending = ArrayList<Pending>()

    /** Records a break to collect from. No-op when the feature is switched off. */
    fun queue(pos: BlockPos, player: ServerPlayer) {
        if (!OneBlockCore.configManager.mainConfig.oneBlockDropsToInventory) return
        pending.add(Pending(pos.immutable(), player.uuid, TICKS_WATCHED))
    }

    /** Called once per tick from the core. Cheap when nothing was broken: the list is empty. */
    fun tick(server: MinecraftServer) {
        if (pending.isEmpty()) return
        val level = OneBlockDimension.level(server)
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (level != null) {
                server.playerList.getPlayer(entry.player)?.let { sweep(level, entry.pos, it) }
            }
            if (--entry.ticksLeft <= 0) iterator.remove()
        }
    }

    /** Drops everything pending, e.g. on shutdown or when the island registry is rebuilt. */
    fun clear() = pending.clear()

    private fun sweep(level: ServerLevel, pos: BlockPos, player: ServerPlayer) {
        val box = AABB(pos).inflate(SWEEP_RADIUS)

        val items = level.getEntitiesOfClass(ItemEntity::class.java, box) {
            !it.isRemoved && it.tickCount <= MAX_AGE_TICKS && !it.item.isEmpty
        }
        for (item in items) {
            val stack = item.item
            val before = stack.count
            // add() mutates the stack in place, so what is left afterwards is the remainder.
            player.inventory.add(stack)
            val taken = before - stack.count
            if (taken <= 0) continue

            player.take(item, taken) // the little "item flies to you" animation
            playPickupSound(level, player)
            if (stack.isEmpty) {
                item.discard()
            } else {
                // Inventory full. Keep the rest, but at the player's feet rather than over the void.
                item.setPos(player.x, player.y, player.z)
                item.deltaMovement = Vec3.ZERO
            }
        }

        val orbs = level.getEntitiesOfClass(ExperienceOrb::class.java, box) {
            !it.isRemoved && it.tickCount <= MAX_AGE_TICKS
        }
        for (orb in orbs) {
            player.giveExperiencePoints(orb.value)
            orb.discard()
        }
    }

    /** Vanilla's own pickup sound, same pitch spread — otherwise the drops vanish silently. */
    private fun playPickupSound(level: ServerLevel, player: ServerPlayer) {
        val random = player.random
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
            0.2f, ((random.nextFloat() - random.nextFloat()) * 0.7f + 1.0f) * 2.0f,
        )
    }
}
