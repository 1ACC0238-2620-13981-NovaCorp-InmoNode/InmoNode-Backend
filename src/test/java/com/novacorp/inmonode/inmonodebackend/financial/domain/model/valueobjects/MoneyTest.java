package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

    @Test
    void amountsAreInSolesWithTwoDecimals() {
        var price = Money.of(new BigDecimal("45000.5"));

        assertEquals(new BigDecimal("45000.50"), price.amount());
        assertEquals("PEN", price.currency());
        assertEquals(Money.of(new BigDecimal("45000.50")), price);
    }

    @Test
    void rejectsMissingZeroOrNegativeAmountsAndUnknownCurrencyFormats() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(null));
        assertThrows(IllegalArgumentException.class, () -> Money.of(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Money.of(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.TEN, "soles"));
        assertThrows(IllegalArgumentException.class, () -> new Money(BigDecimal.TEN, null));
    }
}
