package io.github.sk4ndulf.cobblemon.oneblock.domain.grid;

import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 04 §7 "Mathematische Nachweise": INV-GRID-1..5 in docs/TEST_MATRIX.md. */
class GridGeometryProperties {

    private static GridGeometry grid(int radius, int buffer) {
        return GridGeometry.of(2 * radius + 1, buffer);
    }

    @Example
    void defaultsMatchSpec() {
        GridGeometry g = GridGeometry.of(2049, 1000);
        assertEquals(3072, g.stride());
        assertEquals(1024, g.maxRadius());
        assertEquals(1036, g.maxHubRadius());
    }

    @Example
    void evenOrInconsistentGeometryIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.of(2048, 1000));
        assertThrows(IllegalArgumentException.class, () -> new GridGeometry(2049, 1000, 2048));
        assertThrows(IllegalArgumentException.class, () -> GridGeometry.of(2049, -1));
    }

    @Property
    void strideIsMultipleOf1024AndLeavesBuffer(@ForAll @IntRange(min = 0, max = 100_000) int radius,
                                               @ForAll @IntRange(min = 0, max = 10_000) int buffer) {
        GridGeometry g = grid(radius, buffer);
        assertEquals(0, g.stride() % GridGeometry.STRIDE_MULTIPLE);
        assertTrue(g.stride() >= g.maxIslandSize() + buffer);
    }

    /** INV-GRID-1: cell edges are region edges, so no region file belongs to two cells. */
    @Property
    void cellsAreUnionsOfWholeRegions(@ForAll @IntRange(min = 0, max = 20_000) int radius,
                                      @ForAll @IntRange(min = 0, max = 5_000) int buffer,
                                      @ForAll @IntRange(min = -5_000, max = 5_000) int cell) {
        GridGeometry g = grid(radius, buffer);
        assertEquals(0, Math.floorMod(g.cellMin(cell), GridGeometry.REGION_SIZE));
        assertEquals(0, Math.floorMod(g.cellMax(cell) + 1, GridGeometry.REGION_SIZE));
        assertEquals(GridGeometry.regionOf(g.cellMax(cell)) + 1, GridGeometry.regionOf(g.cellMin(cell + 1)));
    }

    /** INV-GRID-2: cellOf is exact for positive and negative coordinates. */
    @Property
    void cellOfIsInverseOfCellRange(@ForAll @IntRange(min = 0, max = 20_000) int radius,
                                    @ForAll @IntRange(min = -29_999_984, max = 29_999_984) int coord) {
        GridGeometry g = grid(radius, 1000);
        int cell = g.cellOf(coord);
        assertTrue(g.cellMin(cell) <= coord && coord <= g.cellMax(cell), () -> coord + " not in cell " + cell);
    }

    /** INV-GRID-3: a max-size island plus half the buffer stays inside its own cell. */
    @Property
    void islandFitsInsideItsCell(@ForAll @IntRange(min = 0, max = 20_000) int radius,
                                 @ForAll @IntRange(min = 2, max = 5_000) int buffer,
                                 @ForAll @IntRange(min = -2_000, max = 2_000) int cell) {
        GridGeometry g = grid(radius, buffer);
        long a = g.anchor(cell);
        assertTrue(a - radius - buffer / 2 >= g.cellMin(cell));
        assertTrue(a + radius + buffer / 2 <= g.cellMax(cell));
    }

    /** INV-GRID-4: the spiral is reversible. */
    @Property
    void spiralRoundTrip(@ForAll @LongRange(min = 0, max = 4_000_000) long index) {
        SpiralIndex.Cell c = SpiralIndex.cellOf(index);
        assertEquals(index, SpiralIndex.indexOf(c.x(), c.z()));
    }

    @Example
    void spiralIsBijectiveAndOnlyIndexZeroIsHub() {
        int maxCell = 50;
        Set<Long> seen = new HashSet<>();
        for (long i = 0; i < SpiralIndex.capacity(maxCell); i++) {
            SpiralIndex.Cell c = SpiralIndex.cellOf(i);
            assertTrue(Math.max(Math.abs(c.x()), Math.abs(c.z())) <= maxCell);
            assertTrue(seen.add(((long) c.x() << 32) ^ (c.z() & 0xffffffffL)), "duplicate at " + i);
            if (i != SpiralIndex.HUB_INDEX) {
                assertFalse(c.x() == 0 && c.z() == 0, "hub handed out at " + i);
            }
        }
        assertEquals(SpiralIndex.capacity(maxCell), seen.size());
    }

    /** INV-GRID-5: no player slot's cell touches the hub cell. */
    @Property
    void playerSlotsNeverTouchHubCell(@ForAll @LongRange(min = 1, max = 1_000_000) long index) {
        GridGeometry g = GridGeometry.of(2049, 1000);
        SpiralIndex.Cell c = SpiralIndex.cellOf(index);
        boolean disjointX = g.cellMax(c.x()) < g.cellMin(0) || g.cellMin(c.x()) > g.cellMax(0);
        boolean disjointZ = g.cellMax(c.z()) < g.cellMin(0) || g.cellMin(c.z()) > g.cellMax(0);
        assertTrue(disjointX || disjointZ);
    }

    @Property
    void hubRadiusLimitKeepsHubInsideHubCell(@ForAll @IntRange(min = 250, max = 5000) int radius,
                                             @ForAll @IntRange(min = 512, max = 10_000) int buffer) {
        GridGeometry g = grid(radius, buffer);
        // spec 12: max hub radius = stride/2 - margin; blocks -r..r stay inside the hub cell [-stride/2, stride/2 - 1]
        assertEquals(g.stride() / 2 - GridGeometry.HUB_SAFETY_MARGIN, g.maxHubRadius());
        assertTrue(g.maxHubRadius() <= g.cellMax(0) && -g.maxHubRadius() >= g.cellMin(0));
        // and never reaches the neighbouring island's maximum area plus half the buffer
        assertTrue(g.maxHubRadius() < g.anchor(1) - g.maxRadius() - buffer / 2);
    }

    @Example
    void worldLimit() {
        GridGeometry g = GridGeometry.of(2049, 1000);
        // 9765 * 3072 + 1024 = 29,999,104 <= 29,999,984 < 9766 * 3072 + 1024
        assertEquals(9765, g.maxCellIndex());
        assertTrue(g.fitsWorld(9765));
        assertTrue(g.fitsWorld(-9765));
        assertFalse(g.fitsWorld(9766));
        assertFalse(g.fitsWorld(Integer.MAX_VALUE));
        assertFalse(g.fitsWorld(Integer.MIN_VALUE + 1));
    }

    @Property
    void maxCellIndexIsTight(@ForAll @IntRange(min = 250, max = 5000) int radius,
                             @ForAll @IntRange(min = 512, max = 10_000) int buffer) {
        GridGeometry g = grid(radius, buffer);
        assertTrue(g.fitsWorld(g.maxCellIndex()));
        assertFalse(g.fitsWorld(g.maxCellIndex() + 1));
    }
}
