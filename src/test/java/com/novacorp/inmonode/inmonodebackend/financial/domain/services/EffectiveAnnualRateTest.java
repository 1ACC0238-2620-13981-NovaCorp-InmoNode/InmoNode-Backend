package com.novacorp.inmonode.inmonodebackend.financial.domain.services;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.services.FinancingSimulationService;
import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects.Money;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class EffectiveAnnualRateTest {
    @Test void annualGrowthMatchesTEAInsteadOfANominalRateDividedByTwelve() {
        var monthly = AmortizationSchedule.effectiveMonthlyRate(new BigDecimal("12.3456"));
        var growth = BigDecimal.ONE.add(monthly).pow(12, java.math.MathContext.DECIMAL128);
        assertTrue(growth.subtract(new BigDecimal("1.123456")).abs().compareTo(new BigDecimal("1E-28")) < 0);
        assertNotEquals(0, monthly.compareTo(new BigDecimal("12.3456").divide(BigDecimal.valueOf(1200), java.math.MathContext.DECIMAL128)));
    }

    @Test void halfEvenTiesAndFinalAdjustmentMatchInBothContexts() {
        var date = LocalDate.parse("2026-10-09");
        var real = AmortizationSchedule.french(new BigDecimal("1.01"), BigDecimal.ZERO, 2, date);
        var quote = FinancingSimulationService.frenchSchedule(new Money(new BigDecimal("1.01"), "PEN"), BigDecimal.ZERO, 2, date);
        assertEquals(new BigDecimal("0.50"), real.getFirst().getAmount());
        assertEquals(new BigDecimal("0.51"), real.getLast().getAmount());
        for (int n = 0; n < 2; n++) assertEquals(real.get(n).getAmount(), quote.get(n).amount().amount());
    }

    @Test void invalidRatesCannotCreateNegativeInterestSchedules() {
        assertThrows(IllegalArgumentException.class, () -> AmortizationSchedule.effectiveMonthlyRate(new BigDecimal("-0.01")));
        assertThrows(IllegalArgumentException.class, () -> AmortizationSchedule.effectiveMonthlyRate(new BigDecimal("100.0001")));
    }
}
