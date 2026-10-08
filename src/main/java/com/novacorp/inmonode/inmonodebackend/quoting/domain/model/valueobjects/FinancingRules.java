package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * The project's conditions for simulating a financing, as Control Financiero y Documental publishes them.
 * Percentages are expressed as percentages (20 means 20 %).
 *
 * @param minimumInitialPercentage smallest down payment accepted, as a share of the lot price
 * @param annualInterestRate       annual interest rate charged on the financed balance
 * @param maxTermMonths            longest term offered
 */
public record FinancingRules(BigDecimal minimumInitialPercentage, BigDecimal annualInterestRate, int maxTermMonths) {

    public FinancingRules {
        if (minimumInitialPercentage == null || annualInterestRate == null || maxTermMonths < 1) {
            throw new IllegalArgumentException("financing rules need a minimum down payment, a rate and a term");
        }
    }
}
