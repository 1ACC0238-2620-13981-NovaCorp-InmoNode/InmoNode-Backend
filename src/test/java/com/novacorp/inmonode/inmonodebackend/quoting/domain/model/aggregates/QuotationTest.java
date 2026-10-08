package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.FinancingRules;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.InitialPayment;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.LotSnapshot;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class QuotationTest {

    /** 2026-10-08 at 21:00 in Lima, already the 9th in UTC. */
    private static final Instant NOW = Instant.parse("2026-10-09T02:00:00Z");
    private static final LotSnapshot LOT = new LotSnapshot(5L, 2L, "A-01", new BigDecimal("120"), pen("45000"), true,
            new FinancingRules(new BigDecimal("20"), new BigDecimal("12"), 120));

    @Test
    void aSimulationKeepsTheLotPriceTheRateAndItsSchedule() {
        var quotation = Quotation.simulate(41L, LOT, new InitialPayment(pen("9000")), 12, NOW, Duration.ofDays(7));

        assertNull(quotation.getId());
        assertEquals(41L, quotation.getBuyerId());
        assertEquals(5L, quotation.getLotId());
        assertEquals(2L, quotation.getProjectId());
        assertEquals("A-01", quotation.getLotCode());
        assertEquals(pen("45000"), quotation.getLotPrice());
        assertEquals(pen("9000"), quotation.getInitialPayment());
        assertEquals(new BigDecimal("20.00"), quotation.initialPercentage());
        assertEquals(pen("36000"), quotation.financedAmount());
        assertEquals(new BigDecimal("12"), quotation.getAnnualInterestRate());
        assertEquals(12, quotation.getTermMonths());
        assertEquals(12, quotation.getInstallments().size());
        assertEquals(pen("3198.56"), quotation.monthlyInstallment());
        assertEquals(pen("2382.66"), quotation.totalInterest());
        assertEquals(pen("47382.66"), quotation.totalToPay());
        assertEquals(LocalDate.parse("2026-11-08"), quotation.getInstallments().getFirst().dueDate(),
                "the first installment falls a month after the day of the simulation in Lima");
        assertEquals(NOW, quotation.getGeneratedAt());
        assertEquals(NOW.plus(Duration.ofDays(7)), quotation.getValidUntil());
    }

    @Test
    void itIsValidUntilItsDeadlineAndBelongsToItsBuyer() {
        var quotation = Quotation.simulate(41L, LOT, new InitialPayment(pen("9000")), 12, NOW, Duration.ofDays(7));

        assertTrue(quotation.isValid(NOW.plus(Duration.ofDays(7)).minusSeconds(1)));
        assertFalse(quotation.isValid(NOW.plus(Duration.ofDays(7))));
        assertTrue(quotation.belongsTo(41L));
        assertFalse(quotation.belongsTo(42L));
    }

    @Test
    void theProjectRulesAreEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> Quotation.simulate(41L, LOT, new InitialPayment(pen("8000")), 12, NOW, Duration.ofDays(7)));
        assertThrows(IllegalArgumentException.class,
                () -> Quotation.simulate(41L, LOT, new InitialPayment(pen("9000")), 121, NOW, Duration.ofDays(7)));
    }

    private static Money pen(String amount) {
        return new Money(new BigDecimal(amount), "PEN");
    }
}
