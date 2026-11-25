package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments;

import org.jspecify.annotations.Nullable;

/**
 * Checks that a notification comes from the payment gateway (US-55, Scenario 2) before anything is done with it.
 * It needs no network: the gateway signs each notification with a secret shared with this service.
 */
public interface PaymentGatewayWebhookVerifier {

    /** Without the shared secret no notification can be trusted, so none is accepted. */
    boolean isEnabled();

    /**
     * Verifies the signature of {@code payload} and reads it.
     *
     * @throws InvalidGatewaySignatureException when the signature is missing, wrong or too old, or the payload is
     *                                          not a notification
     */
    PaymentGatewayEvent verify(String payload, @Nullable String signature);
}
