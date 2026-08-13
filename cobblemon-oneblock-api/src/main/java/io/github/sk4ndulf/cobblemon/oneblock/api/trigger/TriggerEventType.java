package io.github.sk4ndulf.cobblemon.oneblock.api.trigger;

import net.minecraft.resources.ResourceLocation;

/**
 * THE extension point for trigger events. Addons implement this and register it via
 * {@code OneBlockAPI.get().eventManager().registerEventType(...)} — the core then picks
 * randomly among all registered types whenever an island's break threshold fires.
 */
public interface TriggerEventType {

    /** Unique id, e.g. {@code yourmod:meteor_shower}. */
    ResourceLocation id();

    /** Human-readable name used in announcements. Defaults to the id path. */
    default String displayName() {
        return id().getPath();
    }

    /**
     * Starts a new instance on an island. Runs on the server thread.
     * Spawn your mobs/state here or lazily in the first tick.
     */
    ActiveTriggerEvent start(TriggerEventContext context);
}
