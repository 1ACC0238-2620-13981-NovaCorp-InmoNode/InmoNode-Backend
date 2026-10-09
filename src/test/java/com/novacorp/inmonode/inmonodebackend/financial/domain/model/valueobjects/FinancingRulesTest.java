package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class FinancingRulesTest {

    @Test
    void normalizesPercentagesToTheStoredScale() {
        var rules = new FinancingRules(new BigDecimal("10"), new BigDecimal("12.5"), 120, new BigDecimal("2.0004"));

        assertEquals(new BigDecimal("10.00"), rules.minDownPaymentPercentage());
        assertEquals(new BigDecimal("12.5000"), rules.annualInterestRate());
        assertEquals(new BigDecimal("2.000"), rules.lateFeeRate());
        assertEquals(120, rules.maxTermMonths());
    }

    @Test
    void acceptsTheLimitsOfEachRange() {
        assertDoesNotThrow(() -> new FinancingRules(BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO));
        assertDoesNotThrow(() -> new FinancingRules(BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                FinancingRules.MAX_TERM_MONTHS, BigDecimal.valueOf(100)));
    }

    @Test
    void rejectsValuesOutsideTheirRange() {
        assertThrows(IllegalArgumentException.class, () -> rules("-0.01", "12", 120, "2"));
        assertThrows(IllegalArgumentException.class, () -> rules("10", "100.01", 120, "2"));
        assertThrows(IllegalArgumentException.class, () -> rules("10", "12", 0, "2"));
        assertThrows(IllegalArgumentException.class, () -> rules("10", "12", FinancingRules.MAX_TERM_MONTHS + 1, "2"));
        assertThrows(IllegalArgumentException.class, () -> new FinancingRules(null, BigDecimal.ONE, 12, BigDecimal.ONE));
    }

    private static FinancingRules rules(String downPayment, String interest, int term, String lateFee) {
        return new FinancingRules(new BigDecimal(downPayment), new BigDecimal(interest), term, new BigDecimal(lateFee));
    }
}
