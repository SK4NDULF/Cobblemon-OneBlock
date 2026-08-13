package io.github.sk4ndulf.cobblemon.oneblock.api.event;

import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island;
import net.minecraft.resources.ResourceLocation;

/** Fired on the server thread when a trigger event ends successfully. */
public final class TriggerEventEndEvent extends OneBlockEvent {

    private final Island island;
    private final ResourceLocation eventTypeId;

    public TriggerEventEndEvent(Island island, ResourceLocation eventTypeId) {
        this.island = island;
        this.eventTypeId = eventTypeId;
    }

    public Island island() {
        return island;
    }

    public ResourceLocation eventTypeId() {
        return eventTypeId;
    }
}
