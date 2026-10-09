package com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestTest {
    @Test void clampsOversizedPages() { assertEquals(100, new PageRequest(1, 5_000).limit()); }
    @Test void rejectsInvalidPagesAndLimits() {
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 20));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(1, -1));
    }
    @Test void rejectsOffsetsThatThePersistenceLayerCannotRepresent() {
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(Integer.MAX_VALUE, 100));
    }
}
