package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @param amount exactly what is due (amountDue in the statement: the installment plus its late fee)
 * @param paidAt when the buyer paid, not in the future; now when absent
 */
public record RegisterInstallmentPaymentResource(@NotNull @Positive @Digits(integer = 12, fraction = 2)
                                                 BigDecimal amount,
                                                 Instant paidAt) {
}
