package io.github.sk4ndulf.oneblock.api.event;

import io.github.sk4ndulf.oneblock.api.island.Island;
import net.minecraft.resources.ResourceLocation;

/** Fired on the server thread when a trigger event starts on an island. */
public final class TriggerEventStartEvent extends OneBlockEvent {

    private final Island island;
    private final ResourceLocation eventTypeId;
    private final int difficulty;

    public TriggerEventStartEvent(Island island, ResourceLocation eventTypeId, int difficulty) {
        this.island = island;
        this.eventTypeId = eventTypeId;
        this.difficulty = difficulty;
    }

    public Island island() {
        return island;
    }

    public ResourceLocation eventTypeId() {
        return eventTypeId;
    }

    public int difficulty() {
        return difficulty;
    }
}
