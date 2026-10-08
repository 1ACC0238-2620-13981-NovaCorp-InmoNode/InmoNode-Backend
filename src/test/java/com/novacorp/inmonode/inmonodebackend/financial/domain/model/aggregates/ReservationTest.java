package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    private static final Instant RESERVED_AT = Instant.parse("2026-10-08T09:30:00Z");
    private static final Money AMOUNT = Money.of(new BigDecimal("1500"));
    private static final UUID PROSPECT = UUID.randomUUID();
    private static final UUID SOURCE = UUID.randomUUID();

    @Test
    void fieldReservationHoldsTheLotAndKeepsTheDeviceIds() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);

        assertEquals(ReservationStatus.BLOCKED, reservation.getStatus());
        assertEquals(ReservationChannel.FIELD, reservation.getChannel());
        assertEquals(3L, reservation.getLotId());
        assertEquals(7L, reservation.getRequesterId());
        assertEquals(PROSPECT, reservation.getProspectId());
        assertEquals(SOURCE, reservation.getSourceEventId());
        assertEquals(RESERVED_AT, reservation.getReservedAt());
        assertFalse(reservation.isCancelledByConflict());
    }

    @Test
    void conflictedReservationIsKeptAsCancelled() {
        var reservation = Reservation.cancelledByConflict(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);

        assertEquals(ReservationStatus.CANCELLED_BY_CONFLICT, reservation.getStatus());
        assertTrue(reservation.isCancelledByConflict());
        assertFalse(reservation.expire(), "only a reservation holding the lot can expire");
    }

    @Test
    void blockedReservationExpiresOnce() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);

        assertTrue(reservation.expire());
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
        assertFalse(reservation.expire());
    }

    @Test
    void fieldReservationNeedsEveryIdentifier() {
        assertThrows(IllegalArgumentException.class,
                () -> Reservation.fromFieldSync(3L, 7L, null, SOURCE, AMOUNT, RESERVED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Reservation.fromFieldSync(3L, 7L, PROSPECT, null, AMOUNT, RESERVED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Reservation.fromFieldSync(null, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT));
    }

    @Test
    void channelsHoldTheLotForTheirOwnTime() {
        assertEquals(Duration.ofHours(24), ReservationChannel.FIELD.blockValidity());
        assertEquals(Duration.ofHours(1), ReservationChannel.WEB.blockValidity());
    }
}
