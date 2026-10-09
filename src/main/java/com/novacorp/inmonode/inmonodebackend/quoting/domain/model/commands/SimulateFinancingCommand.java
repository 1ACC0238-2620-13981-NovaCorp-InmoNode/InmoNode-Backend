package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.commands;

import java.math.BigDecimal;

/**
 * Simulates the financing of a lot for the calling buyer (US-17).
 *
 * @param initialPayment down payment the buyer enters, in the currency of the lot price
 * @param termMonths     number of monthly installments
 */
public record SimulateFinancingCommand(Long lotId, BigDecimal initialPayment, int termMonths) {
}
