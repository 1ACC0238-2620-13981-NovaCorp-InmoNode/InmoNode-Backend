package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The down payment ("cuota inicial") a buyer enters to evaluate a financing (US-17).
 */
public record InitialPayment(Money amount) {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public InitialPayment {
        if (amount == null || amount.amount().signum() <= 0) {
            throw new IllegalArgumentException("initialPayment must be greater than zero");
        }
    }

    /** The smallest down payment the rules accept for this price, rounded up to the cent. */
    public static Money minimumFor(Money price, FinancingRules rules) {
        var minimum = price.amount().multiply(rules.minimumInitialPercentage())
                .divide(ONE_HUNDRED, 2, RoundingMode.CEILING);
        return new Money(minimum, price.currency());
    }

    /** US-17, Scenario 2: whether this down payment reaches the minimum share of the price. */
    public boolean meetsMinimum(Money price, FinancingRules rules) {
        return !amount.isLessThan(minimumFor(price, rules));
    }

    /** The share of the price this down payment covers, as a percentage with two decimals. */
    public BigDecimal percentageOf(Money price) {
        return amount.amount().multiply(ONE_HUNDRED).divide(price.amount(), 2, RoundingMode.HALF_UP);
    }
}
