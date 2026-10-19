package io.github.sk4ndulf.cobblemon.oneblock.spike;

/**
 * Phase 0B spike of the slot grid from spec 04 §7. Pure arithmetic, no Minecraft types.
 *
 * <p>Cell {@code g} on one axis covers {@code [g*stride - stride/2, g*stride + stride/2 - 1]}.
 */
public final class GridMath {
    public static final int REGION_SIZE = 512;
    /** Vanilla hard world border limit (blocks from origin). */
    public static final long WORLD_LIMIT = 29_999_984L;

    private GridMath() {
    }

    public static int stride(int maxSize, int buffer) {
        if (maxSize < 1 || (maxSize & 1) == 0) {
            throw new IllegalArgumentException("maxSize must be odd and positive: " + maxSize);
        }
        if (buffer < 0) {
            throw new IllegalArgumentException("buffer must be >= 0: " + buffer);
        }
        long raw = (long) maxSize + buffer;
        long stride = ((raw + 1023) / 1024) * 1024;
        return Math.toIntExact(stride);
    }

    public static int cellOf(int coord, int stride) {
        return Math.floorDiv(coord + stride / 2, stride);
    }

    public static long cellMin(int cell, int stride) {
        return (long) cell * stride - stride / 2;
    }

    public static long cellMax(int cell, int stride) {
        return (long) cell * stride + stride / 2 - 1;
    }

    public static long anchor(int cell, int stride) {
        return Math.multiplyExact((long) cell, (long) stride);
    }

    public static int regionOf(long coord) {
        return Math.toIntExact(Math.floorDiv(coord, REGION_SIZE));
    }

    /** True if the full build area (anchor +- radius) of the cell lies inside the world limit. */
    public static boolean fitsWorld(int cell, int stride, int maxRadius) {
        long a = anchor(cell, stride);
        return Math.abs(a) + maxRadius <= WORLD_LIMIT;
    }

    /** Square spiral: index 0 is (0,0), the hub. Ring k >= 1 holds indices (2k-1)^2 .. (2k+1)^2 - 1. */
    public static int[] spiral(long index) {
        if (index < 0) {
            throw new IllegalArgumentException("negative index");
        }
        if (index == 0) {
            return new int[] {0, 0};
        }
        long k = (long) Math.ceil((Math.sqrt(index + 1.0) - 1) / 2);
        while ((2 * k - 1) * (2 * k - 1) > index) {
            k--;
        }
        while ((2 * k + 1) * (2 * k + 1) <= index) {
            k++;
        }
        long start = (2 * k - 1) * (2 * k - 1);
        long offset = index - start;
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
        return new int[] {Math.toIntExact(x), Math.toIntExact(z)};
    }

    public static long spiralIndex(int x, int z) {
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
}
