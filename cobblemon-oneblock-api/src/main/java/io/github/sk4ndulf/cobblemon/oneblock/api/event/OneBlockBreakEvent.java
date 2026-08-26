package io.github.sk4ndulf.cobblemon.oneblock.api.event;

import io.github.sk4ndulf.cobblemon.oneblock.api.island.Island;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fired on the server thread after a player broke an island's OneBlock and it regenerated.
 * Observational: the break already happened; the drop is handled by vanilla logic.
 */
public final class OneBlockBreakEvent extends OneBlockEvent {

    private final Island island;
    private final ServerPlayer player;
    private final BlockState brokenState;
    private final BlockState nextState;

    public OneBlockBreakEvent(Island island, ServerPlayer player, BlockState brokenState, BlockState nextState) {
        this.island = island;
        this.player = player;
        this.brokenState = brokenState;
        this.nextState = nextState;
    }

    public Island island() {
        return island;
    }

    public ServerPlayer player() {
        return player;
    }

    /** The block state that was just broken. */
    public BlockState brokenState() {
        return brokenState;
    }

    /** The block state the OneBlock regenerated into. */
    public BlockState nextState() {
        return nextState;
    }
}
