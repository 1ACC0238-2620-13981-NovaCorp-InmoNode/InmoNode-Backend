package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotBoundaryTest {

    private static final List<List<Double>> SQUARE = List.of(
            List.of(-76.7370, -12.5210), List.of(-76.7369, -12.5210), List.of(-76.7369, -12.5209),
            List.of(-76.7370, -12.5209), List.of(-76.7370, -12.5210));

    @Test
    void validPolygonRoundTripsThroughWkt() {
        var boundary = LotBoundary.fromPolygonRings(List.of(SQUARE));

        var restored = LotBoundary.fromWkt(boundary.toWkt());

        assertTrue(boundary.toWkt().startsWith("POLYGON"));
        assertEquals(boundary, restored);
        assertEquals(SQUARE, restored.coordinates());
    }

    @Test
    void rejectsMissingGeometryAndHoles() {
        assertMessage("a Polygon geometry is required", null);
        assertMessage("a Polygon geometry is required", List.of());
        assertMessage("the lot boundary cannot have holes", List.of(SQUARE, SQUARE));
    }

    @Test
    void rejectsRingsThatAreTooShortOrNotClosed() {
        assertMessage("at least 4 positions", List.of(SQUARE.subList(0, 3)));
        assertMessage("must be closed", List.of(SQUARE.subList(0, 4)));
    }

    @Test
    void rejectsSelfIntersectingOrFlatPolygons() {
        var bowTie = List.of(List.of(0.0, 0.0), List.of(1.0, 1.0), List.of(1.0, 0.0), List.of(0.0, 1.0), List.of(0.0, 0.0));
        var flat = List.of(List.of(0.0, 0.0), List.of(1.0, 0.0), List.of(2.0, 0.0), List.of(0.0, 0.0));

        assertMessage("crosses itself or has no area", List.of(bowTie));
        assertMessage("crosses itself or has no area", List.of(flat));
    }

    @Test
    void rejectsPositionsThatAreMalformedOrOutOfRange() {
        var outOfRange = List.of(List.of(0.0, 0.0), List.of(200.0, 0.0), List.of(0.0, 1.0), List.of(0.0, 0.0));
        var malformed = Arrays.asList(List.of(0.0, 0.0), List.of(1.0), List.of(0.0, 1.0), List.of(0.0, 0.0));
        var withNull = Arrays.asList(List.of(0.0, 0.0), null, List.of(0.0, 1.0), List.of(0.0, 0.0));

        assertMessage("within range", List.of(outOfRange));
        assertMessage("[longitude, latitude]", List.of(malformed));
        assertMessage("[longitude, latitude]", List.of(withNull));
    }

    private static void assertMessage(String expected, List<List<List<Double>>> rings) {
        var error = assertThrows(IllegalArgumentException.class, () -> LotBoundary.fromPolygonRings(rings));
        assertTrue(error.getMessage().contains(expected), error.getMessage());
    }
}
