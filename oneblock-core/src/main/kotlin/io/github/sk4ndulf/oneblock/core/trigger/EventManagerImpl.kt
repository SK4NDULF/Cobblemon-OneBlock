package io.github.sk4ndulf.oneblock.core.trigger

import io.github.sk4ndulf.oneblock.api.island.Island
import io.github.sk4ndulf.oneblock.api.trigger.EventManager
import io.github.sk4ndulf.oneblock.api.trigger.TriggerEventType
import net.minecraft.resources.ResourceLocation
import java.util.Optional

class EventManagerImpl : EventManager {

    override fun registerEventType(type: TriggerEventType) = TriggerEventService.registerType(type)

    override fun registeredEventTypes(): Set<ResourceLocation> = TriggerEventService.registeredTypes()

    override fun activeEventOn(island: Island): Optional<ResourceLocation> =
        Optional.ofNullable(TriggerEventService.activeTypeOn(island.id()))
}
