package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceResubmissionTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    private PaymentEvidence evidence(Instant submitted) {
        return PaymentEvidence.fromVoucher(UUID.randomUUID(), Money.of(new BigDecimal("9000")), LocalDate.parse("2026-10-08"),
                "operation", false, "vouchers/a.jpg", submitted);
    }
    private Reservation rejected() {
        var reservation = Reservation.fromFieldSync(3L, 7L, UUID.randomUUID(), UUID.randomUUID(), Money.of(new BigDecimal("9000")), NOW);
        var first = evidence(NOW);
        reservation.attachEvidence(first);
        assertTrue(reservation.rejectEvidence(first.getReference(), 77L, "Unreadable", NOW));
        return reservation;
    }

    @Test void rejectionHoldsASeparateDeadlineAndTimelySubstitutionKeepsHistory() {
        var reservation = rejected();
        assertEquals(ReservationStatus.REJECTED, reservation.getStatus());
        assertEquals(NOW.plus(Duration.ofHours(24)), reservation.getResubmissionDeadline());
        assertTrue(reservation.attachEvidence(evidence(NOW.plusSeconds(86399))));
        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservation.getStatus());
        assertNull(reservation.getResubmissionDeadline());
        assertEquals(2, reservation.getEvidences().size());
        assertEquals(PaymentEvidenceStatus.REJECTED, reservation.getEvidences().getFirst().getStatus());
    }

    @Test void evidenceAtOrAfterTheDeadlineIsLateAndDoesNotReopenTheReservation() {
        var reservation = rejected();
        var replacement = evidence(NOW.plus(Duration.ofHours(24)));
        assertFalse(reservation.attachEvidence(replacement));
        assertTrue(reservation.findEvidence(replacement.getReference()).orElseThrow().isLate());
        assertEquals(ReservationStatus.REJECTED, reservation.getStatus());
        assertTrue(reservation.expire());
        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
    }
}
