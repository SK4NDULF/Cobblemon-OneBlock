package io.github.sk4ndulf.cobblemon.oneblock.core.trigger

import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.ActiveTriggerEvent
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventContext
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventStatus
import io.github.sk4ndulf.cobblemon.oneblock.api.trigger.TriggerEventType
import io.github.sk4ndulf.cobblemon.oneblock.core.lang.ServerLang
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.world.BossEvent
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block

/**
 * A single boosted boss with a boss bar. Difficulty (= border level) drives the boss
 * species, its health and its damage. Rewards are FIXED (project rule).
 */
class BossFightEvent : TriggerEventType {

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath("cobblemon_oneblock", "boss_fight")

    override fun displayName(): String = ServerLang.raw("cobblemon_oneblock.event.type.boss_fight")

    override fun start(context: TriggerEventContext): ActiveTriggerEvent = Instance(context.difficulty())

    private class Instance(private val difficulty: Int) : ActiveTriggerEvent {

        private var boss: Mob? = null
        private var bossBar: ServerBossEvent? = null
        private var maxHealth = 1.0f

        override fun tick(context: TriggerEventContext): TriggerEventStatus {
            val current = boss
            if (current == null) {
                return if (spawnBoss(context)) TriggerEventStatus.RUNNING else TriggerEventStatus.FAILURE
            }
            if (!current.isAlive) {
                grantReward(context)
                return TriggerEventStatus.SUCCESS
            }
            bossBar?.let { bar ->
                bar.progress = (current.health / maxHealth).coerceIn(0.0f, 1.0f)
                bar.removeAllPlayers()
                context.participants().forEach { bar.addPlayer(it) }
            }
            return TriggerEventStatus.RUNNING
        }

        private fun spawnBoss(context: TriggerEventContext): Boolean {
            val type: EntityType<out Mob> = when {
                difficulty <= 2 -> EntityType.ZOMBIE
                difficulty <= 4 -> EntityType.VINDICATOR
                difficulty <= 6 -> EntityType.RAVAGER
                else -> EntityType.WITHER_SKELETON
            }
            val spawnedBoss = EventSpawning.spawnNear(context.level(), context.anchor(), type, radius = 3) { mob ->
                mob.getAttribute(Attributes.MAX_HEALTH)?.let { attribute ->
                    attribute.baseValue = attribute.baseValue * (2.0 + difficulty * 0.5)
                    mob.health = mob.maxHealth
                }
                mob.getAttribute(Attributes.ATTACK_DAMAGE)?.let { attribute ->
                    attribute.baseValue = attribute.baseValue * (1.0 + difficulty * 0.15)
                }
                mob.customName = ServerLang.msg("cobblemon_oneblock.event.boss_name", difficulty)
                mob.isCustomNameVisible = true
            } ?: return false

            maxHealth = spawnedBoss.maxHealth
            boss = spawnedBoss
            bossBar = ServerBossEvent(
                spawnedBoss.displayName, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS,
            ).also { bar -> context.participants().forEach { bar.addPlayer(it) } }
            return true
        }

        /** Fixed reward — identical on every border level, by design. */
        private fun grantReward(context: TriggerEventContext) {
            val anchor = context.anchor().above()
            Block.popResource(context.level(), anchor, ItemStack(Items.DIAMOND, 4))
            Block.popResource(context.level(), anchor, ItemStack(Items.NETHERITE_SCRAP, 1))
            Block.popResource(context.level(), anchor, ItemStack(Items.EXPERIENCE_BOTTLE, 12))
        }

        override fun cleanup(context: TriggerEventContext) {
            boss?.discard()
            boss = null
            bossBar?.removeAllPlayers()
            bossBar = null
        }
    }
}
