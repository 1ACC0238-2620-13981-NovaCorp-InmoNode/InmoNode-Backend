package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Commercial conditions of a project (US-53): the base for simulating financing and validating a reservation.
 * Percentages and rates are expressed as percentages (12.5 means 12.5 %).
 *
 * @param minDownPaymentPercentage minimum down payment, as a share of the lot price
 * @param annualInterestRate       annual interest rate charged on the financed balance
 * @param maxTermMonths            longest financing term offered
 * @param lateFeeRate              penalty rate applied to overdue installments
 */
public record FinancingRules(BigDecimal minDownPaymentPercentage, BigDecimal annualInterestRate,
                             int maxTermMonths, BigDecimal lateFeeRate) {

    public static final int MAX_TERM_MONTHS = 360;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public FinancingRules {
        minDownPaymentPercentage = percentage("minDownPaymentPercentage", minDownPaymentPercentage, 2);
        annualInterestRate = percentage("annualInterestRate", annualInterestRate, 3);
        lateFeeRate = percentage("lateFeeRate", lateFeeRate, 3);
        if (maxTermMonths < 1 || maxTermMonths > MAX_TERM_MONTHS) {
            throw new IllegalArgumentException("maxTermMonths must be between 1 and %d".formatted(MAX_TERM_MONTHS));
        }
    }

    private static BigDecimal percentage(String name, BigDecimal value, int scale) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("%s must be between 0 and 100".formatted(name));
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
