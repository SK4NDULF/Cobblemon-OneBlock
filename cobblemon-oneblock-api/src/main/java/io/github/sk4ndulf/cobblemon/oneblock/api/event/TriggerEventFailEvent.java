package io.github.sk4ndulf.cobblemon.oneblock.api.event;

import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island;
import net.minecraft.resources.ResourceLocation;

/**
 * Fired on the server thread when a trigger event fails — the event reported failure,
 * timed out, threw an exception, or every participant went offline.
 */
public final class TriggerEventFailEvent extends OneBlockEvent {

    private final Island island;
    private final ResourceLocation eventTypeId;

    public TriggerEventFailEvent(Island island, ResourceLocation eventTypeId) {
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
