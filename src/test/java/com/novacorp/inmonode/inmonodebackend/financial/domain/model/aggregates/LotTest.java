package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotBoundary;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotDimensions;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LotTest {

    private static final LotBoundary BOUNDARY = LotBoundary.fromPolygonRings(List.of(List.of(
            List.of(0.0, 0.0), List.of(0.001, 0.0), List.of(0.001, 0.001), List.of(0.0, 0.001), List.of(0.0, 0.0))));
    private static final LotDimensions DIMENSIONS = new LotDimensions(new BigDecimal("120"), null, null);
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);

    @Test
    void registeredLotIsAvailableWithANormalizedCode() {
        var lot = newLot();

        assertEquals("A-01", lot.getCode());
        assertEquals(5L, lot.getProjectId());
        assertEquals(LotStatus.AVAILABLE, lot.getStatus());
        assertTrue(lot.isAvailable(NOW));
        assertNull(lot.getId());
        assertNull(lot.getCurrentReservationId());
        assertNull(lot.getBlockedUntil());
    }

    @Test
    void blockHoldsTheLotForTheReservationUntilItRunsOut() {
        var lot = newLot();

        assertTrue(lot.block(10L, NOW, DAY));

        assertEquals(LotStatus.BLOCKED, lot.getStatus());
        assertEquals(10L, lot.getCurrentReservationId());
        assertEquals(NOW.plus(DAY), lot.getBlockedUntil());
        assertFalse(lot.isAvailable(NOW.plus(DAY).minusSeconds(1)));
        assertFalse(lot.block(11L, NOW.plusSeconds(60), DAY), "a second reservation is an availability conflict");
        assertEquals(10L, lot.getCurrentReservationId());
    }

    @Test
    void expiredBlockCountsAsAvailableAndIsReleased() {
        var lot = newLot();
        lot.block(10L, NOW, DAY);
        var expiry = NOW.plus(DAY);

        assertTrue(lot.hasExpiredBlock(expiry));
        assertTrue(lot.isAvailable(expiry));
        assertEquals(Optional.empty(), lot.releaseExpiredBlock(expiry.minusSeconds(1)));
        assertEquals(Optional.of(10L), lot.releaseExpiredBlock(expiry));
        assertEquals(LotStatus.AVAILABLE, lot.getStatus());
        assertNull(lot.getCurrentReservationId());
        assertNull(lot.getBlockedUntil());
    }

    @Test
    void anExpiredBlockCanBeTakenByAnotherReservation() {
        var lot = newLot();
        lot.block(10L, NOW, DAY);

        assertTrue(lot.block(11L, NOW.plus(DAY), DAY));
        assertEquals(11L, lot.getCurrentReservationId());
        assertEquals(NOW.plus(DAY).plus(DAY), lot.getBlockedUntil());
    }

    @Test
    void reservedOrSoldLotsAreNotAvailable() {
        var reserved = Lot.restore(1L, 5L, "A-01", DIMENSIONS, Money.of(BigDecimal.TEN), BOUNDARY, LotStatus.RESERVED,
                10L, null);
        var sold = Lot.restore(2L, 5L, "A-02", DIMENSIONS, Money.of(BigDecimal.TEN), BOUNDARY, LotStatus.SOLD,
                null, null);

        assertFalse(reserved.isAvailable(NOW));
        assertFalse(sold.block(11L, NOW, DAY));
        assertEquals(Optional.empty(), reserved.releaseExpiredBlock(NOW));
    }

    private static Lot newLot() {
        return Lot.register(5L, "  a-01 ", DIMENSIONS, Money.of(new BigDecimal("45000")), BOUNDARY);
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
