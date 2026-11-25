package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import java.util.UUID;

/**
 * The buyer checks how the payment of an installment went (US-55), after paying it in the gateway's form.
 */
public record GetInstallmentPaymentIntentQuery(Long accountStatementId, int installmentNumber, UUID paymentIntentId) {

    public GetInstallmentPaymentIntentQuery {
        if (accountStatementId == null || installmentNumber < 1 || paymentIntentId == null) {
            throw new IllegalArgumentException("installmentNumber must be 1 or more");
        }
    }
}
