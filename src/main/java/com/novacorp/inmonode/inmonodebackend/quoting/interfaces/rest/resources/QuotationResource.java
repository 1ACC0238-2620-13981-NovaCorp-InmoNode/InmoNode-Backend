package com.novacorp.inmonode.inmonodebackend.quoting.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A simulated financing (US-17). Amounts are in {@code currency}; rates and shares are percentages (12.5 = 12.5 %).
 *
 * @param initialPercentage  share of the lot price the down payment covers
 * @param financedAmount     lot price minus the down payment
 * @param monthlyInstallment fixed installment; only the last one may differ by a few cents of rounding
 * @param totalToPay         down payment plus every installment
 * @param validUntil         until when it can back a separation request
 */
public record QuotationResource(Long id, Long lotId, Long projectId, String lotCode, String currency,
                                BigDecimal lotPrice, BigDecimal initialPayment, BigDecimal initialPercentage,
                                BigDecimal financedAmount, int termMonths, BigDecimal annualInterestRate,
                                BigDecimal monthlyInstallment, BigDecimal totalInterest, BigDecimal totalToPay,
                                Instant generatedAt, Instant validUntil, List<InstallmentResource> installments) {

    /**
     * @param amount  principal plus interest
     * @param balance financed balance left after paying it
     */
    public record InstallmentResource(int number, LocalDate dueDate, BigDecimal amount, BigDecimal principal,
                                      BigDecimal interest, BigDecimal balance) {
    }
}
