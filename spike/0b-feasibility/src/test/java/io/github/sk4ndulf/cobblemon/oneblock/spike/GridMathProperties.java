package io.github.sk4ndulf.cobblemon.oneblock.spike;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridMathProperties {
    private static int oddSize(int radius) {
        return 2 * radius + 1;
    }

    @Example
    void defaultStrideMatchesSpec() {
        assertEquals(3072, GridMath.stride(2049, 1000));
    }

    @Property
    void strideIsMultipleOf1024AndLeavesBuffer(@ForAll @IntRange(min = 0, max = 100_000) int radius,
                                               @ForAll @IntRange(min = 0, max = 10_000) int buffer) {
        int size = oddSize(radius);
        int stride = GridMath.stride(size, buffer);
        assertEquals(0, stride % 1024);
        assertTrue(stride >= size + buffer);
    }

    /** Cell edges sit exactly on region-file edges, so no region file is shared by two cells. */
    @Property
    void cellsAreUnionsOfWholeRegions(@ForAll @IntRange(min = 0, max = 20_000) int radius,
                                      @ForAll @IntRange(min = 0, max = 5_000) int buffer,
                                      @ForAll @IntRange(min = -5_000, max = 5_000) int cell) {
        int stride = GridMath.stride(oddSize(radius), buffer);
        long min = GridMath.cellMin(cell, stride);
        long max = GridMath.cellMax(cell, stride);
        assertEquals(0, Math.floorMod(min, GridMath.REGION_SIZE));
        assertEquals(0, Math.floorMod(max + 1, GridMath.REGION_SIZE));
        // the neighbour starts in the next region
        assertEquals(GridMath.regionOf(max) + 1, GridMath.regionOf(GridMath.cellMin(cell + 1, stride)));
    }

    @Property
    void cellOfIsInverseOfCellRange(@ForAll @IntRange(min = 0, max = 20_000) int radius,
                                    @ForAll @IntRange(min = -29_999_000, max = 29_999_000) int coord) {
        int stride = GridMath.stride(oddSize(radius), 1000);
        int cell = GridMath.cellOf(coord, stride);
        assertTrue(GridMath.cellMin(cell, stride) <= coord && coord <= GridMath.cellMax(cell, stride),
                () -> "coord " + coord + " not in cell " + cell);
    }

    /** The whole max-size island plus half the buffer on each side stays inside its own cell. */
    @Property
    void islandFitsInsideItsCell(@ForAll @IntRange(min = 0, max = 20_000) int radius,
                                 @ForAll @IntRange(min = 2, max = 5_000) int buffer,
                                 @ForAll @IntRange(min = -2_000, max = 2_000) int cell) {
        int stride = GridMath.stride(oddSize(radius), buffer);
        long a = GridMath.anchor(cell, stride);
        assertTrue(a - radius - buffer / 2 >= GridMath.cellMin(cell, stride));
        assertTrue(a + radius + buffer / 2 <= GridMath.cellMax(cell, stride));
        assertEquals(cell, GridMath.cellOf(Math.toIntExact(a - radius), stride));
        assertEquals(cell, GridMath.cellOf(Math.toIntExact(a + radius), stride));
    }

    @Property
    void spiralRoundTrip(@ForAll @LongRange(min = 0, max = 4_000_000) long index) {
        int[] xz = GridMath.spiral(index);
        assertEquals(index, GridMath.spiralIndex(xz[0], xz[1]));
    }

    @Example
    void spiralIsBijectiveOverFirstRingsAndOnlyIndexZeroIsHub() {
        Set<Long> seen = new HashSet<>();
        for (long i = 0; i < 101 * 101; i++) {
            int[] xz = GridMath.spiral(i);
            assertTrue(Math.max(Math.abs(xz[0]), Math.abs(xz[1])) <= 50);
            assertTrue(seen.add(((long) xz[0] << 32) ^ (xz[1] & 0xffffffffL)), "duplicate at " + i);
            if (i > 0) {
                assertFalse(xz[0] == 0 && xz[1] == 0, "hub handed out at " + i);
            }
        }
        assertArrayEquals(new int[] {0, 0}, GridMath.spiral(0));
        assertEquals(101 * 101, seen.size());
    }

    /** No player slot's cell intersects the hub cell (0,0). */
    @Property
    void playerSlotsNeverTouchHubCell(@ForAll @LongRange(min = 1, max = 1_000_000) long index) {
        int stride = GridMath.stride(2049, 1000);
        int[] xz = GridMath.spiral(index);
        boolean disjointX = GridMath.cellMax(xz[0], stride) < GridMath.cellMin(0, stride)
                || GridMath.cellMin(xz[0], stride) > GridMath.cellMax(0, stride);
        boolean disjointZ = GridMath.cellMax(xz[1], stride) < GridMath.cellMin(0, stride)
                || GridMath.cellMin(xz[1], stride) > GridMath.cellMax(0, stride);
        assertTrue(disjointX || disjointZ);
    }

    @Example
    void worldLimitCapsDefaultGrid() {
        int stride = GridMath.stride(2049, 1000);
        int last = 0;
        while (GridMath.fitsWorld(last + 1, stride, 1024)) {
            last++;
        }
        // 9765 * 3072 + 1024 = 29,999,104 <= 29,999,984 < 9766 * 3072 + 1024 = 30,002,176
        assertEquals(9765, last);
        assertTrue(GridMath.fitsWorld(-last, stride, 1024));
        assertFalse(GridMath.fitsWorld(-(last + 1), stride, 1024));
    }

    @Example
    void extremeCellsAreRejectedWithoutOverflow() {
        int stride = GridMath.stride(2049, 1000);
        assertFalse(GridMath.fitsWorld(Integer.MAX_VALUE, stride, 1024));
        assertFalse(GridMath.fitsWorld(Integer.MIN_VALUE + 1, stride, 1024));
    }
}
