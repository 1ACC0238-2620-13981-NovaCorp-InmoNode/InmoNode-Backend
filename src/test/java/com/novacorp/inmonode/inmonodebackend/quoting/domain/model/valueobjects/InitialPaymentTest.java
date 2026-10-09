package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InitialPaymentTest {

    private static final Money PRICE = pen("45000");
    private static final FinancingRules RULES = new FinancingRules(new BigDecimal("20"), BigDecimal.TEN, 60);

    @Test
    void theMinimumIsTheRuleShareOfThePriceRoundedUpToTheCent() {
        assertEquals(pen("9000.00"), InitialPayment.minimumFor(PRICE, RULES));
        var odd = new FinancingRules(new BigDecimal("12.5"), BigDecimal.TEN, 60);
        assertEquals(pen("4166.67"), InitialPayment.minimumFor(pen("33333.33"), odd), "4166.66625 rounds up");
    }

    @Test
    void aDownPaymentMeetsTheMinimumFromItsExactAmountOn() {
        assertTrue(new InitialPayment(pen("9000")).meetsMinimum(PRICE, RULES));
        assertTrue(new InitialPayment(pen("20000")).meetsMinimum(PRICE, RULES));
        assertFalse(new InitialPayment(pen("8999.99")).meetsMinimum(PRICE, RULES));
    }

    @Test
    void itKnowsTheShareOfThePriceItCovers() {
        assertEquals(new BigDecimal("20.00"), new InitialPayment(pen("9000")).percentageOf(PRICE));
        assertEquals(new BigDecimal("33.33"), new InitialPayment(pen("15000")).percentageOf(PRICE));
    }

    @Test
    void itMustBePositive() {
        var error = assertThrows(IllegalArgumentException.class, () -> new InitialPayment(pen("0")));
        assertTrue(error.getMessage().startsWith("initialPayment"), error.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new InitialPayment(null));
    }

    private static Money pen(String amount) {
        return new Money(new BigDecimal(amount), "PEN");
    }
}
