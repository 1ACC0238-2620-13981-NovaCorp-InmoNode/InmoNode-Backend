package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotTest {

    private static final LotBoundary BOUNDARY = LotBoundary.fromPolygonRings(List.of(List.of(
            List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.001), List.of(0.0, 0.0))));
    private static final LotDimensions DIMENSIONS = new LotDimensions(new BigDecimal("120"), null, null);

    @Test
    void registeredLotIsAvailableWithANormalizedCode() {
        var lot = Lot.register(5L, "  a-01 ", DIMENSIONS, Money.of(new BigDecimal("45000")), BOUNDARY);

        assertEquals("A-01", lot.getCode());
        assertEquals(5L, lot.getProjectId());
        assertEquals(LotStatus.AVAILABLE, lot.getStatus());
        assertTrue(lot.isAvailable());
        assertNull(lot.getId());
    }

    @Test
    void codeIsRequiredAndBounded() {
        assertThrows(IllegalArgumentException.class, () -> Lot.normalizeCode(" "));
        assertThrows(IllegalArgumentException.class, () -> Lot.normalizeCode(null));
        assertThrows(IllegalArgumentException.class, () -> Lot.normalizeCode("X".repeat(Lot.MAX_CODE_LENGTH + 1)));
        assertEquals("X".repeat(Lot.MAX_CODE_LENGTH), Lot.normalizeCode("x".repeat(Lot.MAX_CODE_LENGTH)));
    }

    @Test
    void dimensionsNeedAPositiveAreaAndOptionalPositiveSides() {
        var regular = new LotDimensions(new BigDecimal("120.555"), new BigDecimal("8"), new BigDecimal("15"));

        assertEquals(new BigDecimal("120.56"), regular.area());
        assertEquals(new BigDecimal("8.00"), regular.front());
        assertThrows(IllegalArgumentException.class, () -> new LotDimensions(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LotDimensions(BigDecimal.ZERO, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LotDimensions(BigDecimal.TEN, new BigDecimal("-1"), null));
    }
}
