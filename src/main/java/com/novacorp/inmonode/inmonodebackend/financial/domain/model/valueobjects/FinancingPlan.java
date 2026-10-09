package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The financing a buyer agreed to when separating a lot from the web: the quotation's term and rate, applied to the
 * lot price at that moment. It is the base of the account statement (2.6.4.1).
 *
 * @param lotPrice           price of the lot when it was blocked
 * @param termMonths         number of monthly installments
 * @param annualInterestRate annual rate as a percentage (12.5 means 12.5 %)
 */
public record FinancingPlan(Money lotPrice, int termMonths, BigDecimal annualInterestRate, Long quotationId) {
    public FinancingPlan(Money lotPrice, int termMonths, BigDecimal annualInterestRate) {
        this(lotPrice, termMonths, annualInterestRate, null);
    }


    public FinancingPlan {
        if (lotPrice == null || annualInterestRate == null) {
            throw new IllegalArgumentException("a financing plan needs the lot price and the rate");
        }
        if (termMonths < 1 || termMonths > FinancingRules.MAX_TERM_MONTHS) {
            throw new IllegalArgumentException(
                    "termMonths must be between 1 and %d".formatted(FinancingRules.MAX_TERM_MONTHS));
        }
        if (annualInterestRate.signum() < 0 || annualInterestRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("annualInterestRate must be between 0 and 100");
        }
        annualInterestRate = annualInterestRate.setScale(4, RoundingMode.HALF_EVEN);
    }
}
