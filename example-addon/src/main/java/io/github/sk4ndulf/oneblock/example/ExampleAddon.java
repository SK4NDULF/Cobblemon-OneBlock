package io.github.sk4ndulf.oneblock.example;

import io.github.sk4ndulf.oneblock.api.OneBlockAPI;
import io.github.sk4ndulf.oneblock.api.event.BorderLevelUpEvent;
import io.github.sk4ndulf.oneblock.api.event.IslandCreatedEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference addon for the Cobblemon OneBlock API. It demonstrates the three things
 * an addon typically does, and nothing else:
 *
 * <ol>
 *   <li>listen to core events on the event bus</li>
 *   <li>register a custom loot provider (what the OneBlock turns into)</li>
 *   <li>register a custom trigger event type</li>
 * </ol>
 *
 * <p>This module depends on {@code oneblock-api} only — not on the core mod and not on
 * Cobblemon. That is the whole point of the two-module split.</p>
 */
public class ExampleAddon implements ModInitializer {

    public static final String MOD_ID = "oneblock_example_addon";
    public static final Logger LOGGER = LoggerFactory.getLogger("OneBlockExampleAddon");

    @Override
    public void onInitialize() {
        // The core sets up the API during its own initialization. Registering from
        // SERVER_STARTING is the simple, always-safe moment: the API is guaranteed to
        // exist and the server is not running yet.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (!OneBlockAPI.isAvailable()) {
                LOGGER.warn("Cobblemon OneBlock is not installed — example addon stays idle.");
                return;
            }
            OneBlockAPI api = OneBlockAPI.get();
            LOGGER.info("Hooking into Cobblemon OneBlock API v{}", api.apiVersion());

            registerEventListeners(api);
            api.lootRegistry().register(new EmeraldMilestoneLootProvider());
            api.eventManager().registerEventType(new FireworkCelebrationEvent());
        });
    }

    /** Reacting to what happens on islands. */
    private void registerEventListeners(OneBlockAPI api) {
        // Greet players when their island is created.
        api.eventBus().subscribe(IslandCreatedEvent.class, event ->
                event.owner().sendSystemMessage(
                        Component.literal("[ExampleAddon] Welcome to island #" + event.island().id() + "!")));

        // Log every border level up. A real addon might hand out rewards here.
        api.eventBus().subscribe(BorderLevelUpEvent.class, event ->
                LOGGER.info("Island {} grew to level {} ({} blocks wide).",
                        event.island().id(), event.newLevel(), event.newBorderSize()));
    }
}
