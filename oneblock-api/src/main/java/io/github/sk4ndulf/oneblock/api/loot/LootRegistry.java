package io.github.sk4ndulf.oneblock.api.loot;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Registry for {@link OneBlockLootProvider}s. Register during your mod's initialization.
 */
public interface LootRegistry {

    /**
     * Adds a provider. Registering an id twice replaces the earlier provider
     * (logged by the core), so addons can deliberately override each other.
     */
    void register(OneBlockLootProvider provider);

    /** Removes a provider again. Returns true when one was actually removed. */
    boolean unregister(ResourceLocation id);

    /** Ids of all registered providers, in the order they are consulted. */
    Set<ResourceLocation> registered();
}
