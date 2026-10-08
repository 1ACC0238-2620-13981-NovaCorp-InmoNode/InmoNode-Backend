package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.InitialPayment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.LotSnapshot;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.SeparationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeparationRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-08T15:00:00Z");
    private static final Instant IN_AN_HOUR = NOW.plus(Duration.ofHours(1));
    private static final UUID TRANSACTION = UUID.fromString("5f2b8e0a-3c4d-4e5f-8a9b-0c1d2e3f4a5b");

    @Test
    void aBlockedRequestTakesTheLotBuyerAndDownPaymentOfItsQuotation() {
        var request = SeparationRequest.blocked(TRANSACTION, savedQuotation(), NOW, IN_AN_HOUR);

        assertNull(request.getId());
        assertEquals(TRANSACTION, request.getTransactionId());
        assertEquals(5L, request.getLotId());
        assertEquals(41L, request.getBuyerId());
        assertEquals(90L, request.getQuotationId());
        assertEquals(pen("9000"), request.getInitialAmount());
        assertEquals(SeparationStatus.BLOCKED, request.getStatus());
        assertEquals(NOW, request.getRequestedAt());
        assertEquals(IN_AN_HOUR, request.getLockExpiresAt());
        assertNull(request.getRejectionReason());
    }

    @Test
    void itIsActiveWhileTheLockLasts() {
        var request = SeparationRequest.blocked(TRANSACTION, savedQuotation(), NOW, IN_AN_HOUR);

        assertTrue(request.isActive(IN_AN_HOUR.minusSeconds(1)));
        assertFalse(request.isActive(IN_AN_HOUR));
    }

    @Test
    void aRejectedRequestHoldsNothingAndSaysWhy() {
        var request = SeparationRequest.rejectedUnavailable(TRANSACTION, savedQuotation(), NOW);

        assertEquals(SeparationStatus.REJECTED_UNAVAILABLE, request.getStatus());
        assertEquals(SeparationRequest.LOT_UNAVAILABLE, request.getRejectionReason());
        assertNull(request.getLockExpiresAt());
        assertFalse(request.isActive(NOW));
    }

    @Test
    void aRequestNeedsASavedQuotationAndALockThatEndsLater() {
        var unsaved = Quotation.simulate(41L, lot(), new InitialPayment(pen("9000")), 12, NOW, Duration.ofDays(7));

        assertThrows(IllegalArgumentException.class,
                () -> SeparationRequest.blocked(TRANSACTION, unsaved, NOW, IN_AN_HOUR));
        assertThrows(IllegalArgumentException.class,
                () -> SeparationRequest.blocked(TRANSACTION, savedQuotation(), NOW, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> SeparationRequest.blocked(null, savedQuotation(), NOW, IN_AN_HOUR));
    }

    private static Quotation savedQuotation() {
        var simulated = Quotation.simulate(41L, lot(), new InitialPayment(pen("9000")), 12, NOW, Duration.ofDays(7));
        return Quotation.restore(90L, simulated.getBuyerId(), simulated.getLotId(), simulated.getProjectId(),
                simulated.getLotCode(), simulated.getLotPrice(), simulated.getInitialPayment(),
                simulated.getTermMonths(), simulated.getAnnualInterestRate(), simulated.getInstallments(),
                simulated.getGeneratedAt(), simulated.getValidUntil());
    }

    private static LotSnapshot lot() {
        return new LotSnapshot(5L, 2L, "A-01", new BigDecimal("120"), pen("45000"), true,
                new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120));
    }

    private static Money pen(String amount) {
        return new Money(new BigDecimal(amount), "PEN");
    }
}
