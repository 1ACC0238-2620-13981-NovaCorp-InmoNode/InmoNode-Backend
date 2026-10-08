package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.entities.PaymentEvidence;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FinancialVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    @Test
    void anEvidenceOnTimeCoveringTheDownPaymentCanBeApproved() {
        var reservation = reservation();
        var evidence = attach(reservation, new Money(new BigDecimal("1500"), "PEN"));

        assertTrue(FinancialVerificationService.approvalObstacle(reservation, evidence).isEmpty());
        var more = reservation();
        assertTrue(FinancialVerificationService.approvalObstacle(more,
                attach(more, new Money(new BigDecimal("2000"), "PEN"))).isEmpty(), "paying more is fine");
    }

    @Test
    void anAmountUnderTheDownPaymentIsAnObstacle() {
        var reservation = reservation();
        var evidence = attach(reservation, new Money(new BigDecimal("1499.99"), "PEN"));

        var obstacle = FinancialVerificationService.approvalObstacle(reservation, evidence).orElseThrow();
        assertTrue(obstacle.contains("1499.99") && obstacle.contains("1500.00"), obstacle);
    }

    @Test
    void anotherCurrencyIsAnObstacle() {
        var reservation = reservation();
        var evidence = attach(reservation, new Money(new BigDecimal("1500"), "USD"));

        var obstacle = FinancialVerificationService.approvalObstacle(reservation, evidence).orElseThrow();
        assertTrue(obstacle.contains("USD") && obstacle.contains("PEN"), obstacle);
    }

    @Test
    void aLateEvidenceOrAReservationNotWaitingIsAnObstacle() {
        var expired = reservation();
        expired.expire();
        var late = attach(expired, new Money(new BigDecimal("1500"), "PEN"));
        assertTrue(FinancialVerificationService.approvalObstacle(expired, late).orElseThrow().contains("late"));

        var verified = reservation();
        var first = attach(verified, new Money(new BigDecimal("1500"), "PEN"));
        var second = attach(verified, new Money(new BigDecimal("1500"), "PEN"));
        verified.verify(first.getReference(), 77L, null, NOW);
        assertTrue(FinancialVerificationService.approvalObstacle(verified, second).orElseThrow()
                .contains("VERIFIED"));
    }

    private static Reservation reservation() {
        return Reservation.fromFieldSync(3L, 7L, UUID.randomUUID(), UUID.randomUUID(),
                Money.of(new BigDecimal("1500")), NOW.minusSeconds(3600));
    }

    private static PaymentEvidence attach(Reservation reservation, Money amount) {
        var voucherId = UUID.randomUUID();
        reservation.attachEvidence(PaymentEvidence.fromVoucher(voucherId, amount, LocalDate.parse("2026-10-07"),
                "00123456", false, "vouchers/r/%s.jpg".formatted(voucherId), NOW));
        return reservation.findEvidence(voucherId).orElseThrow();
    }
}
