package io.github.sk4ndulf.oneblock.core.trigger

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType

/** Shared spawn helpers for the built-in trigger events. */
object EventSpawning {

    /**
     * Spawns a mob near the anchor. Mobs are marked persistent so they never despawn
     * mid-event; the event's cleanup is responsible for removing them again.
     */
    fun spawnNear(
        level: ServerLevel,
        anchor: BlockPos,
        type: EntityType<out Mob>,
        radius: Int,
        configure: (Mob) -> Unit = {},
    ): Mob? {
        val pos = randomNearby(level, anchor, radius)
        val mob = type.spawn(level, pos, MobSpawnType.EVENT) ?: return null
        mob.setPersistenceRequired()
        configure(mob)
        return mob
    }

    /** A position around the anchor, one block above it so mobs never spawn inside blocks. */
    fun randomNearby(level: ServerLevel, anchor: BlockPos, radius: Int): BlockPos {
        val effective = radius.coerceAtLeast(1)
        val dx = level.random.nextInt(effective * 2 + 1) - effective
        val dz = level.random.nextInt(effective * 2 + 1) - effective
        return anchor.offset(dx, 1, dz)
    }
}
