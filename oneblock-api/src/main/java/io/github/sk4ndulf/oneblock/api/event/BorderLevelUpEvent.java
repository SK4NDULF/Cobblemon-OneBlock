package io.github.sk4ndulf.oneblock.api.event;

import io.github.sk4ndulf.oneblock.api.island.Island;

/**
 * Fired on the server thread after an island reached a new border level.
 * The island's border area has already grown when this fires.
 */
public final class BorderLevelUpEvent extends OneBlockEvent {

    private final Island island;
    private final int oldLevel;
    private final int newLevel;
    private final int newBorderSize;

    public BorderLevelUpEvent(Island island, int oldLevel, int newLevel, int newBorderSize) {
        this.island = island;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.newBorderSize = newBorderSize;
    }

    public Island island() {
        return island;
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }

    /** New border side length in blocks (the island is newBorderSize x newBorderSize). */
    public int newBorderSize() {
        return newBorderSize;
    }
}
