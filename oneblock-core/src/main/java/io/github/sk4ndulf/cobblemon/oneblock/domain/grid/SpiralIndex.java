package io.github.sk4ndulf.cobblemon.oneblock.domain.grid;

/**
 * Deterministic, reversible mapping between a slot index and grid cell coordinates (square spiral).
 * Index 0 is cell (0,0), which belongs to the hub and is never handed out to players.
 * Ring {@code k >= 1} holds the indices {@code (2k-1)^2 .. (2k+1)^2 - 1}.
 */
public final class SpiralIndex {
    public static final long HUB_INDEX = 0;

    private SpiralIndex() {
    }

    public record Cell(int x, int z) {
    }

    public static Cell cellOf(long index) {
        if (index < 0) {
            throw new IllegalArgumentException("negative index: " + index);
        }
        if (index == 0) {
            return new Cell(0, 0);
        }
        long k = (long) Math.ceil((Math.sqrt(index + 1.0) - 1) / 2);
        // correct floating point drift at ring edges
        while ((2 * k - 1) * (2 * k - 1) > index) {
            k--;
        }
        while ((2 * k + 1) * (2 * k + 1) <= index) {
            k++;
        }
        long offset = index - (2 * k - 1) * (2 * k - 1);
        long side = 2 * k;
        long x;
        long z;
        if (offset < side) {
            x = k;
            z = -k + 1 + offset;
        } else if (offset < 2 * side) {
            x = k - 1 - (offset - side);
            z = k;
        } else if (offset < 3 * side) {
            x = -k;
            z = k - 1 - (offset - 2 * side);
        } else {
            x = -k + 1 + (offset - 3 * side);
            z = -k;
        }
        return new Cell(Math.toIntExact(x), Math.toIntExact(z));
    }

    public static long indexOf(int x, int z) {
        long k = Math.max(Math.abs((long) x), Math.abs((long) z));
        if (k == 0) {
            return 0;
        }
        long start = (2 * k - 1) * (2 * k - 1);
        long side = 2 * k;
        if (x == k && z > -k) {
            return start + (z + k - 1);
        }
        if (z == k) {
            return start + side + (k - 1 - x);
        }
        if (x == -k) {
            return start + 2 * side + (k - 1 - z);
        }
        return start + 3 * side + (x + k - 1);
    }

    /** Number of indices whose cells lie within {@code -maxCell..maxCell} on both axes. */
    public static long capacity(int maxCell) {
        long side = 2L * maxCell + 1;
        return side * side;
    }
}
