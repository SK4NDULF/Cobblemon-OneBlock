package io.github.sk4ndulf.cobblemon.oneblock.api;

import io.github.sk4ndulf.cobblemon.oneblock.api.event.OneBlockEventBus;
import io.github.sk4ndulf.cobblemon.oneblock.api.internal.OneBlockAPIHolder;
import io.github.sk4ndulf.cobblemon.oneblock.api.island.IslandManager;
import io.github.sk4ndulf.cobblemon.oneblock.api.loot.LootRegistry;
import io.github.sk4ndulf.cobblemon.oneblock.api.party.PartyManager;
import io.github.sk4ndulf.cobblemon.oneblock.api.permission.PermissionManager;
import io.github.sk4ndulf.cobblemon.oneblock.api.progression.ProgressionManager;

/**
 * Static access point for the Cobblemon OneBlock API.
 *
 * <p>Addon mods should only ever depend on the {@code cobblemon-oneblock-api} module and access
 * everything through {@link #get()}. The implementation is provided by the
 * {@code cobblemon_oneblock} core mod at runtime.</p>
 *
 * <p>The API instance becomes available during server initialization, before any world
 * is loaded. Addons should therefore not call {@link #get()} from their static
 * initializers; the earliest safe point is their own {@code onInitialize}.</p>
 */
public interface OneBlockAPI {

    /**
     * Returns the API singleton.
     *
     * @return the active API instance
     * @throws IllegalStateException if the core mod has not been initialized yet
     */
    static OneBlockAPI get() {
        return OneBlockAPIHolder.get();
    }

    /**
     * Returns whether the API is already available. Useful for soft-dependencies.
     */
    static boolean isAvailable() {
        return OneBlockAPIHolder.isPresent();
    }

    /**
     * The OneBlock event bus. Addons subscribe here to react to island lifecycle,
     * progression, party and moderation activity.
     */
    OneBlockEventBus eventBus();

    /**
     * Read access to active islands. Only call on the server thread.
     */
    IslandManager islandManager();

    /**
     * Protection queries (roles, modify checks). Only call on the server thread.
     */
    PermissionManager permissionManager();

    /**
     * Party/co-op queries (invites, size limits). Only call on the server thread.
     */
    PartyManager partyManager();

    /**
     * Border-level progression queries. Only call on the server thread.
     */
    ProgressionManager progressionManager();

    /**
     * Registry for custom OneBlock loot providers. Register during your mod's init.
     */
    LootRegistry lootRegistry();

    /**
     * Semantic version of the API implementation (e.g. {@code "0.1.0"}).
     */
    String apiVersion();
}
