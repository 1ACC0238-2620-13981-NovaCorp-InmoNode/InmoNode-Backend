package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount with its currency, kept at two decimals. This context's own copy: no domain code is shared between
 * contexts (Context Map, no Shared Kernel). Zero is allowed, since a quotation without interest totals zero interest.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be zero or positive");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an ISO 4217 code such as PEN");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount), currency);
    }

    public boolean isLessThan(Money other) {
        return amount.compareTo(other.amount) < 0;
    }
}
