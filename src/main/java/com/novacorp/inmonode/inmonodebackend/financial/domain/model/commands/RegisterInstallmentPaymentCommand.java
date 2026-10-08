package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The back office records the payment of an installment of an account statement (US-23).
 *
 * @param amount exactly what is due: the installment plus its late fee, if any
 * @param paidAt when the buyer paid; now when absent
 */
public record RegisterInstallmentPaymentCommand(Long accountStatementId, int installmentNumber, BigDecimal amount,
                                                @Nullable Instant paidAt) {

    public RegisterInstallmentPaymentCommand {
        if (accountStatementId == null || installmentNumber < 1) {
            throw new IllegalArgumentException("installmentNumber must be 1 or more");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
