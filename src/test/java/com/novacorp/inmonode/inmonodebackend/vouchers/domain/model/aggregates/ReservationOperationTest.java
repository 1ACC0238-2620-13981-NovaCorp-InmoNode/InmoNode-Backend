package com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.valueobjects.OperationChannel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservationOperationTest {

    private static final UUID RESERVATION = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("1500.00");
    private static final Instant RESERVED_AT = Instant.parse("2026-10-08T09:30:00Z");
    private static final Instant DUE_AT = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void aFieldReservationIsOwnedByTheAgentWhoMadeIt() {
        var operation = ReservationOperation.fromFieldReservation(RESERVATION, 7L, 31L, AMOUNT, RESERVED_AT, DUE_AT);

        assertNull(operation.getId());
        assertEquals(RESERVATION, operation.getReservationId());
        assertEquals(OperationChannel.FIELD, operation.getChannel());
        assertEquals(7L, operation.getOwnerId());
        assertEquals(31L, operation.getLotId());
        assertEquals(AMOUNT, operation.getInitialAmount());
        assertEquals(RESERVED_AT, operation.getReservedAt());
        assertEquals(DUE_AT, operation.getEvidenceDueAt());
        assertTrue(operation.isOwnedBy(7L));
        assertFalse(operation.isOwnedBy(8L));
    }

    @Test
    void theEvidenceDeadlineMayBeUnknown() {
        var operation = ReservationOperation.fromFieldReservation(RESERVATION, 7L, 31L, AMOUNT, RESERVED_AT, null);

        assertNull(operation.getEvidenceDueAt());
    }

    @Test
    void aFieldReservationNeedsItsIdAgentLotAmountAndDate() {
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOperation.fromFieldReservation(null, 7L, 31L, AMOUNT, RESERVED_AT, DUE_AT));
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOperation.fromFieldReservation(RESERVATION, null, 31L, AMOUNT, RESERVED_AT, DUE_AT));
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOperation.fromFieldReservation(RESERVATION, 7L, null, AMOUNT, RESERVED_AT, DUE_AT));
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOperation.fromFieldReservation(RESERVATION, 7L, 31L, null, RESERVED_AT, DUE_AT));
        assertThrows(IllegalArgumentException.class,
                () -> ReservationOperation.fromFieldReservation(RESERVATION, 7L, 31L, AMOUNT, null, DUE_AT));
    }
}
