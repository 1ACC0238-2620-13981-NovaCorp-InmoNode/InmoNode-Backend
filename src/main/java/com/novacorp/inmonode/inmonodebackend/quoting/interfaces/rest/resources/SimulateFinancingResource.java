package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * @param initialPayment down payment, in the currency of the lot price; at least the project's minimum share of it
 * @param termMonths     number of monthly installments, up to the project's maximum term
 */
public record SimulateFinancingResource(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal initialPayment,
        @NotNull @Positive Integer termMonths) {
}
