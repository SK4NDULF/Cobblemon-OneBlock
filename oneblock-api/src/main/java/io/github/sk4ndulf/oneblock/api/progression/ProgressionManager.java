package io.github.sk4ndulf.oneblock.api.progression;

import io.github.sk4ndulf.oneblock.api.island.Island;

/**
 * Border-level progression queries. Points are earned by mining the OneBlock —
 * every party member contributes (with configurable diminishing returns for larger
 * parties). All methods must be called on the server thread.
 */
public interface ProgressionManager {

    /** Current accumulated points of the island. */
    double points(Island island);

    /**
     * Cumulative points required to reach the given border level (2-8).
     * Level 1 is the starting level and requires 0 points.
     */
    double pointsRequiredFor(int level);

    /** Border side length in blocks at the given level (1-8). */
    int borderSizeAt(int level);
}
