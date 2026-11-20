package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** The special conditions of a field reservation (US-58), with the same limits the field app checks. */
class NegotiationTermsTest {

    @Test
    void everyPartIsOptionalAndBlankTextsAreAbsent() {
        var terms = new NegotiationTerms(null, "  ", "\n");

        assertEquals(NegotiationTerms.NONE, terms);
        assertEquals("Cochera", new NegotiationTerms(null, "  Cochera ", null).bonus());
    }

    @Test
    void theDiscountIsZeroOrPositiveWithAtMostTwoDecimals() {
        assertEquals(new BigDecimal("0"), new NegotiationTerms(new BigDecimal("0"), null, null).discountAmount());
        assertEquals(new BigDecimal("2500.50"), new NegotiationTerms(new BigDecimal("2500.50"), null, null).discountAmount());
        assertThrows(IllegalArgumentException.class, () -> new NegotiationTerms(new BigDecimal("-1"), null, null));
        assertThrows(IllegalArgumentException.class, () -> new NegotiationTerms(new BigDecimal("10.005"), null, null));
    }

    @Test
    void bonusAndNotesHaveTheLengthsOfTheFieldApp() {
        assertNotNull(new NegotiationTerms(null, "b".repeat(150), "n".repeat(500)));
        assertThrows(IllegalArgumentException.class, () -> new NegotiationTerms(null, "b".repeat(151), null));
        assertThrows(IllegalArgumentException.class, () -> new NegotiationTerms(null, null, "n".repeat(501)));
    }

    @Test
    void aValidDiscountStaysBelowTheLotPrice() {
        var price = new BigDecimal("45000");

        assertFalse(new NegotiationTerms(new BigDecimal("44999.99"), null, null).discountReaches(price));
        assertTrue(new NegotiationTerms(new BigDecimal("45000.00"), null, null).discountReaches(price));
        assertFalse(NegotiationTerms.NONE.discountReaches(price));
    }
}
