package io.github.sk4ndulf.oneblock.core.trigger

import io.github.sk4ndulf.oneblock.api.trigger.ActiveTriggerEvent
import io.github.sk4ndulf.oneblock.api.trigger.TriggerEventContext
import io.github.sk4ndulf.oneblock.api.trigger.TriggerEventStatus
import io.github.sk4ndulf.oneblock.api.trigger.TriggerEventType
import io.github.sk4ndulf.oneblock.core.lang.ServerLang
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block

/**
 * A calm event: resources rain onto the island for a few seconds. No difficulty scaling
 * at all — it is the guaranteed-success breather between the combat events.
 */
class ResourceBurstEvent : TriggerEventType {

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath("oneblock", "resource_burst")

    override fun displayName(): String = ServerLang.raw("oneblock.event.type.resource_burst")

    override fun start(context: TriggerEventContext): ActiveTriggerEvent = Instance()

    private class Instance : ActiveTriggerEvent {

        private companion object {
            const val DROP_INTERVAL_TICKS = 10
            const val TOTAL_DROPS = 20

            val POOL: List<Pair<Item, Int>> = listOf(
                Items.IRON_INGOT to 4, Items.GOLD_INGOT to 3, Items.COAL to 8,
                Items.COPPER_INGOT to 6, Items.REDSTONE to 8, Items.LAPIS_LAZULI to 6,
                Items.EMERALD to 2, Items.DIAMOND to 1, Items.EXPERIENCE_BOTTLE to 3,
            )
        }

        private var ticks = 0
        private var drops = 0

        override fun tick(context: TriggerEventContext): TriggerEventStatus {
            ticks++
            if (ticks % DROP_INTERVAL_TICKS != 0) return TriggerEventStatus.RUNNING

            val level = context.level()
            val (item, count) = POOL[level.random.nextInt(POOL.size)]
            Block.popResource(level, context.anchor().above(), ItemStack(item, count))
            drops++

            return if (drops >= TOTAL_DROPS) TriggerEventStatus.SUCCESS else TriggerEventStatus.RUNNING
        }

        override fun cleanup(context: TriggerEventContext) {
            // Dropped items belong to the players — nothing to clean up.
        }
    }
}
