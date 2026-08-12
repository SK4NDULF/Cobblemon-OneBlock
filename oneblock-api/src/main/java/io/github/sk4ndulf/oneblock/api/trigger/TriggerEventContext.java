package io.github.sk4ndulf.oneblock.api.trigger;

import io.github.sk4ndulf.oneblock.api.island.Island;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Everything an event implementation needs about the island it runs on.
 * Passed to {@link TriggerEventType#start} and every {@link ActiveTriggerEvent#tick}.
 */
public interface TriggerEventContext {

    Island island();

    /** The OneBlock world. */
    ServerLevel level();

    /**
     * Difficulty of this event = the island's border level (1-8) captured at event start.
     * Per project rule, difficulty scales with border level — rewards do NOT.
     */
    int difficulty();

    /** The island's OneBlock position. */
    BlockPos anchor();

    /**
     * Online island players (owner + members) currently in the OneBlock dimension.
     * Recomputed on every call. The core fails the event when this becomes empty.
     */
    List<ServerPlayer> participants();
}
