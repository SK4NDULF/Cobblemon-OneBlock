package io.github.sk4ndulf.cobblemon.oneblock.spike;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.stream.Collectors;

import static io.github.sk4ndulf.cobblemon.oneblock.spike.SpikeMod.LOG;

/** Phase 0B spike: logs entity load/unload during server shutdown to find who mutates entity sections. */
final class ShutdownProbe {
    private static volatile boolean stopping;

    private ShutdownProbe() {
    }

    static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            stopping = true;
            if (Boolean.getBoolean("oneblock.spike.recallOnStop")) {
                server.getAllLevels().forEach(level -> {
                    var owned = new java.util.ArrayList<PokemonEntity>();
                    level.getAllEntities().forEach(e -> {
                        if (e instanceof PokemonEntity p && !p.getPokemon().isWild() && p.getOwnerUUID() != null) {
                            owned.add(p);
                        }
                    });
                    owned.forEach(p -> {
                        LOG.info("SPIKE-STOP recalling owned {} owner={}", p.getPokemon().getSpecies().getName(), p.getOwnerUUID());
                        p.getPokemon().recall();
                    });
                });
            }
            server.getAllLevels().forEach(level -> level.getAllEntities().forEach(e -> {
                if (e instanceof PokemonEntity p) {
                    LOG.info("SPIKE-STOP pokemon {} wild={} owner={} battling={} tethered={} removed={} pos={}",
                            p.getPokemon().getSpecies().getName(), p.getPokemon().isWild(), p.getOwnerUUID(),
                            p.isBattling(), p.getTethering() != null, p.isRemoved(), p.blockPosition().toShortString());
                }
            }));
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> log("unload", entity));
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> log("load", entity));
    }

    private static void log(String what, Entity entity) {
        if (!stopping) {
            return;
        }
        String trace = Arrays.stream(new Throwable().getStackTrace())
                .skip(2)
                .map(f -> f.getClassName().replace("net.minecraft.", "mc.") + "." + f.getMethodName() + ":" + f.getLineNumber())
                .filter(s -> !s.startsWith("java.") && !s.startsWith("jdk."))
                .limit(14)
                .collect(Collectors.joining(" <- "));
        LOG.info("SPIKE-STOP {} type={} removal={} uuid={} trace={}", what, entity.getType().builtInRegistryHolder().key().location(),
                entity.getRemovalReason(), entity.getUUID(), trace);
    }
}
