package com.novacorp.inmonode.inmonodebackend.financial.interfaces.rest.resources;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The payment of an installment through the gateway (US-55).
 *
 * @param status         OPEN while it can be paid; SUCCEEDED once the gateway confirmed it and the installment was
 *                       credited; UNAPPLIED when it was charged but the installment no longer owed it, and finance
 *                       refunds it; CANCELED or EXPIRED when it was closed without a charge
 * @param amount         what is charged: the installment plus its late fee when it was opened
 * @param expiresAt      after it, starting the payment again opens another one
 * @param confirmedAt    when the gateway confirmed the charge
 * @param failureReason  why the latest attempt was declined, while it is still open
 * @param clientSecret   what the gateway's form confirms the charge with; only while it can be paid, and only when
 *                       it is started
 * @param publishableKey the gateway's public key for its form
 */
public record InstallmentPaymentIntentResource(UUID id, Long accountStatementId, int installmentNumber, String status,
                                               BigDecimal amount, String currency, Instant createdAt,
                                               Instant expiresAt, Instant confirmedAt, String failureReason,
                                               String clientSecret, String publishableKey) {
}
