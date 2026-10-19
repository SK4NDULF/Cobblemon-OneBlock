package io.github.sk4ndulf.cobblemon.oneblock.domain.grid;

/**
 * Slot grid from spec 04 §7. One axis is described here; X and Z use the same formulas.
 *
 * <p>Cell {@code g} covers {@code [g*stride - stride/2, g*stride + stride/2 - 1]}. Because {@code stride} is a multiple
 * of 1024, every cell boundary is a region-file boundary (512 blocks), so no two cells share a region file.
 *
 * @param maxIslandSize odd edge length of a level-8 island
 * @param bufferBlocks  extra space between neighbouring islands
 * @param stride        distance between cell anchors, {@code ceil((maxIslandSize + bufferBlocks) / 1024) * 1024}
 */
public record GridGeometry(int maxIslandSize, int bufferBlocks, int stride) {
    public static final int REGION_SIZE = 512;
    public static final int STRIDE_MULTIPLE = 1024;
    /** Vanilla hard world border (blocks from origin). */
    public static final long WORLD_LIMIT = 29_999_984L;
    /** Spec 12: hub radius must stay this far away from the edge of the hub cell. */
    public static final int HUB_SAFETY_MARGIN = 500;

    public GridGeometry {
        if (maxIslandSize < 1 || (maxIslandSize & 1) == 0) {
            throw new IllegalArgumentException("maxIslandSize must be odd and positive: " + maxIslandSize);
        }
        if (bufferBlocks < 0) {
            throw new IllegalArgumentException("bufferBlocks must be >= 0: " + bufferBlocks);
        }
        if (stride != strideFor(maxIslandSize, bufferBlocks)) {
            throw new IllegalArgumentException("stride " + stride + " does not match " + maxIslandSize + "/" + bufferBlocks);
        }
    }

    public static GridGeometry of(int maxIslandSize, int bufferBlocks) {
        return new GridGeometry(maxIslandSize, bufferBlocks, strideFor(maxIslandSize, bufferBlocks));
    }

    public static int strideFor(int maxIslandSize, int bufferBlocks) {
        long raw = (long) maxIslandSize + bufferBlocks;
        long stride = ((raw + STRIDE_MULTIPLE - 1) / STRIDE_MULTIPLE) * STRIDE_MULTIPLE;
        return Math.toIntExact(stride);
    }

    public int maxRadius() {
        return maxIslandSize / 2;
    }

    /** Largest allowed hub protection radius. */
    public int maxHubRadius() {
        return stride / 2 - HUB_SAFETY_MARGIN;
    }

    public int cellOf(int coord) {
        return Math.floorDiv(coord + stride / 2, stride);
    }

    public long cellMin(int cell) {
        return (long) cell * stride - stride / 2;
    }

    public long cellMax(int cell) {
        return (long) cell * stride + stride / 2 - 1;
    }

    public long anchor(int cell) {
        return (long) cell * stride;
    }

    public static int regionOf(long coord) {
        return Math.toIntExact(Math.floorDiv(coord, REGION_SIZE));
    }

    /** True if the full build area of the cell (anchor +- maxRadius) lies inside the vanilla world limit. */
    public boolean fitsWorld(int cell) {
        return Math.abs(anchor(cell)) + maxRadius() <= WORLD_LIMIT;
    }

    /** Largest cell index n so that cells -n..n fit inside the world on one axis. */
    public int maxCellIndex() {
        return Math.toIntExact((WORLD_LIMIT - maxRadius()) / stride);
    }
}
