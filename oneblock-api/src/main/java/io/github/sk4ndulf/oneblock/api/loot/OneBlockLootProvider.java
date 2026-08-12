package io.github.sk4ndulf.oneblock.api.loot;

import io.github.sk4ndulf.oneblock.api.island.Island;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Extension point for what the OneBlock turns into when it is broken.
 *
 * <p>Providers are asked in registration order; the first one returning a block wins.
 * Returning {@link Optional#empty()} passes the decision on — to the next provider, and
 * finally to the server's configured loot table. That makes it easy to implement partial
 * behaviour such as "every 500th break is a bonus block" without reimplementing the pool.</p>
 *
 * <p>Called on the server thread, once per OneBlock break. Keep it fast, and never block.</p>
 */
public interface OneBlockLootProvider {

    /** Unique id, e.g. {@code yourmod:nether_phase}. */
    ResourceLocation id();

    /**
     * The block the OneBlock should become, or empty to defer.
     *
     * <p>{@code island.breakCount()} already includes the break being decided here, so
     * {@code breakCount() % 500 == 0} fires on exactly every 500th break.</p>
     *
     * @param island the island whose OneBlock was broken (level, break count, party, ...)
     * @param level  the OneBlock world
     */
    Optional<BlockState> nextBlock(Island island, ServerLevel level);
}
