package io.github.sk4ndulf.cobblemon.oneblock.cobblemon;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * ADR 005: before Minecraft closes the levels, recall every player-owned Pokémon that is still sent out. In the
 * phase 0B spike, owned Pokémon left in the world after a battle made the shutdown fail with a
 * ConcurrentModificationException while saving entity sections (4/4 runs); recalling them first avoided it (2/2).
 * {@code Pokemon.recall()} is public Cobblemon API; the Pokémon stays in its party/PC.
 */
public final class ShutdownRecall {
    private ShutdownRecall() {
    }

    /** Must run on the server thread. Returns the number of recalled Pokémon. */
    public static int recallOwnedPokemon(MinecraftServer server) {
        List<PokemonEntity> owned = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof PokemonEntity pokemon && !pokemon.isRemoved()
                        && !pokemon.getPokemon().isWild() && pokemon.getOwnerUUID() != null) {
                    owned.add(pokemon);
                }
            }
        }
        // Recall outside the iteration: recalling removes the entity from the level.
        for (PokemonEntity pokemon : owned) {
            pokemon.getPokemon().recall();
        }
        return owned.size();
    }
}
