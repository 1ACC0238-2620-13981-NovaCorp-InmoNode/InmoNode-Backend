package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class LotFiltersTest {
    @Test
    void rejectsNegativeAndInvertedRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new LotFilters(new BigDecimal("150"), new BigDecimal("120"), null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LotFilters(null, null, new BigDecimal("-1"), null, null, null));
    }

    @Test
    void acceptsAbsentAndEqualBoundsWithoutRoundingThem() {
        var filters = new LotFilters(new BigDecimal("120.001"), new BigDecimal("120.001"), null, null, null, null);
        assertEquals(new BigDecimal("120.001"), filters.minArea());
        assertNull(LotFilters.NONE.minArea());
    }

    @Test
    void rejectsInvalidViewportsIncludingNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new MapBounds(20, 0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new MapBounds(-181, 0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new MapBounds(0, 0, Double.NaN, 1));
    }

    @Test
    void locationUsesPolygonIntersectionRatherThanOnlyItsBoundingBox() {
        var boundary = LotBoundary.fromWkt("POLYGON ((0 0, 2 0, 0 2, 0 0))");
        assertTrue(boundary.intersects(new MapBounds(0.5, 0.5, 1, 1)));
        assertTrue(boundary.intersects(new MapBounds(2, 0, 3, 1))); // touching counts
        assertFalse(boundary.intersects(new MapBounds(1.5, 1.5, 2, 2)));
    }
}
