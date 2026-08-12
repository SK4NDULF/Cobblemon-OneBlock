package io.github.sk4ndulf.oneblock.example;

import io.github.sk4ndulf.oneblock.api.island.Island;
import io.github.sk4ndulf.oneblock.api.loot.OneBlockLootProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Turns every 500th break into an emerald block and defers on every other break.
 *
 * <p>This is the recommended shape for a loot provider: handle the cases you care
 * about, return {@link Optional#empty()} for everything else so the server's own
 * loot table keeps working.</p>
 */
public class EmeraldMilestoneLootProvider implements OneBlockLootProvider {

    private static final int MILESTONE_INTERVAL = 500;

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(ExampleAddon.MOD_ID, "emerald_milestone");
    }

    @Override
    public Optional<BlockState> nextBlock(Island island, ServerLevel level) {
        // breakCount was already incremented for this break when providers are asked.
        if (island.breakCount() % MILESTONE_INTERVAL == 0) {
            return Optional.of(Blocks.EMERALD_BLOCK.defaultBlockState());
        }
        return Optional.empty();
    }
}
