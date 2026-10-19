package io.github.sk4ndulf.cobblemon.oneblock.bootstrap;

import io.github.sk4ndulf.cobblemon.oneblock.command.ObCommand;
import io.github.sk4ndulf.cobblemon.oneblock.localization.Messages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Fabric entrypoint. Creates one {@link ModRuntime} per server run. */
public final class OneBlockMod implements ModInitializer {
    public static final String MOD_ID = "cobblemon_oneblock";
    /** Spec 12: config lives in {@code config/oneblock/}. */
    public static final String CONFIG_DIR = "oneblock";

    private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    private volatile ModRuntime runtime;

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        Messages messages = Messages.loadBundled();
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> runtime = ModRuntime.start(server, configDir, version));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ModRuntime current = runtime;
            if (current != null) {
                current.stop();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> runtime = null);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ObCommand.register(dispatcher, () -> runtime, messages));
        LOG.info("Cobblemon OneBlock {} initialised", version);
    }
}
