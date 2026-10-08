package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Positive amount with its currency, kept at two decimals so prices, payments and balances compare exactly.
 */
public record Money(BigDecimal amount, String currency) {

    public static final String DEFAULT_CURRENCY = "PEN";

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code such as PEN");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** An amount in soles, the currency of every price in the catalog. */
    public static Money of(BigDecimal amount) {
        return new Money(amount, DEFAULT_CURRENCY);
    }
}
