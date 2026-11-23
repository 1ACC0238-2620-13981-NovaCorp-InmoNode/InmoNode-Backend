package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * An authentic notification of the payment gateway and what was done with it (US-55, Scenario 2). The gateway's
 * event id identifies it, so a redelivered one is answered with the outcome it already had.
 *
 * @param providerPaymentIntentId the gateway's id of the charge it is about, when it is about one
 * @param detail                  why it had that outcome, when it is worth telling
 */
public record PaymentGatewayEventRecord(String eventId, String type, @Nullable String providerPaymentIntentId,
                                        PaymentGatewayEventOutcome outcome, @Nullable String detail,
                                        Instant receivedAt) {

    public PaymentGatewayEventRecord {
        if (eventId == null || eventId.isBlank() || type == null || outcome == null || receivedAt == null) {
            throw new IllegalArgumentException("an event of the gateway needs its id, type, outcome and time");
        }
    }
}
