package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import org.jspecify.annotations.Nullable;

/**
 * The buyer starts paying an installment of their account statement through the payment gateway (US-55).
 *
 * @param idempotencyKey chosen by the app for one attempt to pay: starting again with it returns the same payment
 *                       (Scenario 3)
 */
public record StartInstallmentPaymentCommand(Long accountStatementId, int installmentNumber,
                                             @Nullable String idempotencyKey) {

    /** As long as the column that keeps it. */
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    public StartInstallmentPaymentCommand {
        if (accountStatementId == null || installmentNumber < 1) {
            throw new IllegalArgumentException("installmentNumber must be 1 or more");
        }
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.strip();
        if (idempotencyKey != null && idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key must be at most %d characters"
                    .formatted(MAX_IDEMPOTENCY_KEY_LENGTH));
        }
    }
}
