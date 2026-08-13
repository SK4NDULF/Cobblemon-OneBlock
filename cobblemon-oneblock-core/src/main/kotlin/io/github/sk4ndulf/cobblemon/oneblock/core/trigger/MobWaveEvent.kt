package io.github.sk4ndulf.cobblemon.oneblock.core.trigger

import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.ActiveTriggerEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventContext
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventStatus
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventType
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import net.minecraft.ChatFormatting
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block

/**
 * Mob waves. Difficulty (= border level) drives wave count, mob count and which mobs
 * appear. Rewards are FIXED across all difficulties (project rule).
 */
class MobWaveEvent : TriggerEventType {

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_oneblock", "mob_wave")

    override fun displayName(): String = ServerLang.raw("cobblemon_oneblock.event.type.mob_wave")

    override fun start(context: TriggerEventContext): ActiveTriggerEvent = Instance(context.difficulty())

    private class Instance(difficulty: Int) : ActiveTriggerEvent {

        private val totalWaves = 2 + difficulty / 3
        private val mobsPerWave = 3 + (difficulty * 3) / 2
        private val difficultyLevel = difficulty

        private val spawned = ArrayList<Mob>()
        private var wave = 0
        private var ticks = 0

        override fun tick(context: TriggerEventContext): TriggerEventStatus {
            ticks++
            spawned.removeIf { !it.isAlive }

            if (spawned.isNotEmpty()) return TriggerEventStatus.RUNNING

            // Short breather between waves.
            if (wave > 0 && ticks % 60 != 0) return TriggerEventStatus.RUNNING

            if (wave >= totalWaves) {
                grantReward(context)
                return TriggerEventStatus.SUCCESS
            }

            wave++
            spawnWave(context)
            for (player in context.participants()) {
                player.displayClientMessage(
                    ServerLang.msg("cobblemon_oneblock.event.wave", wave, totalWaves).withStyle(ChatFormatting.RED), true,
                )
            }
            return TriggerEventStatus.RUNNING
        }

        private fun spawnWave(context: TriggerEventContext) {
            val level = context.level()
            val pool = mobPool()
            repeat(mobsPerWave) {
                val type = pool[level.random.nextInt(pool.size)]
                EventSpawning.spawnNear(level, context.anchor(), type, radius = 4) { mob ->
                    // Difficulty scales toughness, never the loot.
                    mob.getAttribute(Attributes.MAX_HEALTH)?.let { attribute ->
                        attribute.baseValue = attribute.baseValue * (1.0 + difficultyLevel * 0.12)
                        mob.health = mob.maxHealth
                    }
                }?.let { spawned.add(it) }
            }
        }

        private fun mobPool(): List<EntityType<out Mob>> = when {
            difficultyLevel <= 2 -> listOf(EntityType.ZOMBIE, EntityType.SKELETON)
            difficultyLevel <= 4 -> listOf(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK)
            difficultyLevel <= 6 -> listOf(
                EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK,
                EntityType.STRAY, EntityType.WITCH, EntityType.PILLAGER,
            )
            else -> listOf(
                EntityType.WITHER_SKELETON, EntityType.VINDICATOR, EntityType.WITCH,
                EntityType.PILLAGER, EntityType.BLAZE, EntityType.RAVAGER,
            )
        }

        /** Fixed reward — identical on every border level, by design. */
        private fun grantReward(context: TriggerEventContext) {
            val level = context.level()
            val anchor = context.anchor().above()
            Block.popResource(level, anchor, ItemStack(Items.DIAMOND, 2))
            Block.popResource(level, anchor, ItemStack(Items.GOLD_INGOT, 8))
            Block.popResource(level, anchor, ItemStack(Items.EXPERIENCE_BOTTLE, 6))
        }

        override fun cleanup(context: TriggerEventContext) {
            spawned.forEach { it.discard() }
            spawned.clear()
        }
    }
}
