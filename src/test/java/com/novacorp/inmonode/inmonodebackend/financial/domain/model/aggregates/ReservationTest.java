package com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.FinancingPlan;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceSource;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.PaymentEvidenceStatus;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationChannel;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    private static final Instant RESERVED_AT = Instant.parse("2026-10-08T09:30:00Z");
    private static final Money AMOUNT = Money.of(new BigDecimal("1500"));
    private static final UUID PROSPECT = UUID.randomUUID();
    private static final UUID SOURCE = UUID.randomUUID();
    private static final FinancingPlan PLAN = new FinancingPlan(Money.of(new BigDecimal("45000")), 12,
            new BigDecimal("12"));

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
    void webRequestHoldsTheLotForTheBuyerWithoutAProspect() {
        var reservation = Reservation.fromWebRequest(3L, 41L, SOURCE, AMOUNT, PLAN, RESERVED_AT);

        assertEquals(ReservationStatus.BLOCKED, reservation.getStatus());
        assertEquals(ReservationChannel.WEB, reservation.getChannel());
        assertEquals(3L, reservation.getLotId());
        assertEquals(41L, reservation.getRequesterId());
        assertNull(reservation.getProspectId());
        assertEquals(SOURCE, reservation.getSourceEventId());
        assertEquals(AMOUNT, reservation.getInitialAmount());
        assertEquals(RESERVED_AT, reservation.getReservedAt());
        assertTrue(reservation.getEvidences().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> Reservation.fromWebRequest(3L, 41L, null, AMOUNT, PLAN, RESERVED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> Reservation.fromWebRequest(3L, null, SOURCE, AMOUNT, PLAN, RESERVED_AT));
    }

    @Test
    void aWebRequestKeepsTheFinancingPlanAndAFieldReservationHasNone() {
        var web = Reservation.fromWebRequest(3L, 41L, SOURCE, AMOUNT, PLAN, RESERVED_AT);

        assertEquals(PLAN, web.getFinancingPlan());
        assertEquals(new BigDecimal("12.000"), web.getFinancingPlan().annualInterestRate());
        assertNull(Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT).getFinancingPlan());
        assertThrows(IllegalArgumentException.class,
                () -> Reservation.fromWebRequest(3L, 41L, SOURCE, Money.of(new BigDecimal("45000")), PLAN, RESERVED_AT),
                "the down payment cannot cover the whole price");
        assertThrows(IllegalArgumentException.class,
                () -> new FinancingPlan(Money.of(new BigDecimal("45000")), 0, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> new FinancingPlan(Money.of(new BigDecimal("45000")), 12, new BigDecimal("-1")));
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
    void evidenceOnTimeMovesTheReservationToPendingVerification() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        var evidence = voucherEvidence();

        assertTrue(reservation.attachEvidence(evidence));

        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservation.getStatus());
        assertEquals(List.of(evidence), reservation.getEvidences());
        var attached = reservation.findEvidence(evidence.getReference()).orElseThrow();
        assertFalse(attached.isLate());
        assertEquals(PaymentEvidenceSource.VOUCHER, attached.getSource());
        assertEquals(PaymentEvidenceStatus.PENDING, attached.getStatus());
        assertFalse(reservation.expire(), "a reservation waiting for verification no longer expires");
    }

    @Test
    void evidenceOfAReservationThatNoLongerHoldsItsLotIsKeptAsLate() {
        var expired = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        expired.expire();
        var conflicted = Reservation.cancelledByConflict(3L, 7L, PROSPECT, UUID.randomUUID(), AMOUNT, RESERVED_AT);

        for (var reservation : List.of(expired, conflicted)) {
            var status = reservation.getStatus();
            var evidence = voucherEvidence();

            assertFalse(reservation.attachEvidence(evidence), status.name());

            assertEquals(status, reservation.getStatus(), "the reservation stays as it is");
            assertTrue(reservation.findEvidence(evidence.getReference()).orElseThrow().isLate(), status.name());
        }
    }

    @Test
    void anotherEvidenceWhileWaitingForVerificationIsKeptWithoutChanges() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        reservation.attachEvidence(voucherEvidence());
        var second = voucherEvidence();

        assertFalse(reservation.attachEvidence(second));

        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservation.getStatus());
        assertEquals(2, reservation.getEvidences().size());
        assertFalse(reservation.findEvidence(second.getReference()).orElseThrow().isLate());
    }

    @Test
    void theSameEvidenceIsNeverAttachedTwice() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        var evidence = voucherEvidence();
        reservation.attachEvidence(evidence);

        assertThrows(IllegalStateException.class, () -> reservation.attachEvidence(evidence));
        assertEquals(1, reservation.getEvidences().size());
    }

    @Test
    void approvingTheEvidenceUnderReviewVerifiesTheReservation() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        var evidence = voucherEvidence();
        reservation.attachEvidence(evidence);
        var now = RESERVED_AT.plusSeconds(7200);

        reservation.verify(evidence.getReference(), 77L, "conciliado", now);

        assertEquals(ReservationStatus.VERIFIED, reservation.getStatus());
        assertEquals(now, reservation.getVerifiedAt());
        var approved = reservation.findEvidence(evidence.getReference()).orElseThrow();
        assertEquals(PaymentEvidenceStatus.APPROVED, approved.getStatus());
        assertEquals(77L, approved.getReviewerId());
        assertEquals("conciliado", approved.getReviewerNote());
        assertThrows(IllegalStateException.class,
                () -> reservation.verify(evidence.getReference(), 77L, null, now), "decided only once");
    }

    @Test
    void onlyAReservationWaitingForVerificationIsVerifiedWithAnEvidenceOnTime() {
        var blocked = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        assertThrows(IllegalStateException.class,
                () -> blocked.verify(UUID.randomUUID(), 77L, null, RESERVED_AT), "no such evidence");

        var expired = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        expired.expire();
        var late = voucherEvidence();
        expired.attachEvidence(late);
        assertThrows(IllegalStateException.class,
                () -> expired.verify(late.getReference(), 77L, null, RESERVED_AT));
        assertEquals(ReservationStatus.EXPIRED, expired.getStatus());
    }

    @Test
    void rejectingTheOnlyEvidenceUnderReviewWaitsForASubstituteAgain() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        var evidence = voucherEvidence();
        reservation.attachEvidence(evidence);

        assertTrue(reservation.rejectEvidence(evidence.getReference(), 77L, "Voucher ilegible", RESERVED_AT));

        assertEquals(ReservationStatus.BLOCKED, reservation.getStatus());
        var rejected = reservation.findEvidence(evidence.getReference()).orElseThrow();
        assertEquals(PaymentEvidenceStatus.REJECTED, rejected.getStatus());
        assertEquals("Voucher ilegible", rejected.getReviewerNote());
        var substitute = voucherEvidence();
        assertTrue(reservation.attachEvidence(substitute), "the substitute arrives on time");
        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservation.getStatus());
    }

    @Test
    void rejectingOneEvidenceKeepsTheReviewWhileAnotherIsPending() {
        var reservation = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        var first = voucherEvidence();
        var second = voucherEvidence();
        reservation.attachEvidence(first);
        reservation.attachEvidence(second);

        assertFalse(reservation.rejectEvidence(first.getReference(), 77L, "Duplicado", RESERVED_AT));

        assertEquals(ReservationStatus.PENDING_VERIFICATION, reservation.getStatus());
    }

    @Test
    void rejectingALateEvidenceLeavesTheReservationAsItIs() {
        var expired = Reservation.fromFieldSync(3L, 7L, PROSPECT, SOURCE, AMOUNT, RESERVED_AT);
        expired.expire();
        var late = voucherEvidence();
        expired.attachEvidence(late);

        assertFalse(expired.rejectEvidence(late.getReference(), 77L, "Llegó tarde", RESERVED_AT));

        assertEquals(ReservationStatus.EXPIRED, expired.getStatus());
        assertEquals(PaymentEvidenceStatus.REJECTED,
                expired.findEvidence(late.getReference()).orElseThrow().getStatus());
    }

    @Test
    void aVoucherEvidenceNeedsItsDataAndItsFile() {
        var day = LocalDate.parse("2026-10-08");
        assertThrows(IllegalArgumentException.class, () -> PaymentEvidence.fromVoucher(null, AMOUNT, day, "OP-1",
                false, "vouchers/a.jpg", RESERVED_AT));
        assertThrows(IllegalArgumentException.class, () -> PaymentEvidence.fromVoucher(UUID.randomUUID(), AMOUNT,
                day, " ", false, "vouchers/a.jpg", RESERVED_AT));
        assertThrows(IllegalArgumentException.class, () -> PaymentEvidence.fromVoucher(UUID.randomUUID(), AMOUNT,
                day, "OP-1", false, null, RESERVED_AT));
    }

    private static PaymentEvidence voucherEvidence() {
        var voucher = UUID.randomUUID();
        return PaymentEvidence.fromVoucher(voucher, AMOUNT, LocalDate.parse("2026-10-08"), "00123456", false,
                "vouchers/%s/%s.jpg".formatted(SOURCE, voucher), RESERVED_AT.plusSeconds(600));
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
