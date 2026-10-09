package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoPointTest {

    @Test
    void acceptsWgs84Coordinates() {
        var point = new GeoPoint(-12.5, -76.7);

        assertEquals(-12.5, point.latitude());
        assertEquals(-76.7, point.longitude());
        assertDoesNotThrow(() -> new GeoPoint(90, 180));
        assertDoesNotThrow(() -> new GeoPoint(-90, -180));
    }

    @Test
    void rejectsCoordinatesOutOfRangeOrNotANumber() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(90.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, -180.1));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0));
    }
}
