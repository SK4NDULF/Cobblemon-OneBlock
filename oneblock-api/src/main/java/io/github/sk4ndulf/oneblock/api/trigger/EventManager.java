package io.github.sk4ndulf.oneblock.api.trigger;

import io.github.sk4ndulf.oneblock.api.island.Island;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;

/**
 * Trigger event registry and state queries. All methods must be called on the
 * server thread. Register custom types during your mod's initialization.
 */
public interface EventManager {

    /**
     * Registers a custom event type. Types registered under an already-taken id
     * replace the previous registration (log-visible), so addons can override
     * the built-in events deliberately.
     */
    void registerEventType(TriggerEventType type);

    /** Ids of all registered event types (built-ins included). */
    Set<ResourceLocation> registeredEventTypes();

    /** The event currently running on the island, if any. */
    Optional<ResourceLocation> activeEventOn(Island island);
}
