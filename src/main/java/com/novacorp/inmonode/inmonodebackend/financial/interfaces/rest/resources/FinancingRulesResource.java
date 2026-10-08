package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Financing rules of a project; percentages and rates are expressed as percentages (12.5 = 12.5 %).
 */
public record FinancingRulesResource(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minDownPaymentPercentage,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal annualInterestRate,
        @NotNull @Min(1) @Max(360) Integer maxTermMonths,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal lateFeeRate) {
}
