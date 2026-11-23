package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments;

import org.jspecify.annotations.Nullable;

/**
 * A verified notification of the payment gateway.
 *
 * @param type            the gateway's own name of the event, kept for the log
 * @param providerId      the gateway's id of the charge it is about; absent when it is not about a charge
 * @param app             the app that opened the charge, from its metadata
 * @param intentReference the id of the intent that opened the charge, from its metadata
 * @param failureMessage  why the attempt was declined, for {@link Kind#PAYMENT_FAILED}
 */
public record PaymentGatewayEvent(String id, String type, Kind kind, @Nullable String providerId,
                                  @Nullable String app, @Nullable String intentReference,
                                  @Nullable String failureMessage) {

    public enum Kind {
        /** The charge was paid. */
        PAYMENT_SUCCEEDED,
        /** An attempt to pay the charge was declined. */
        PAYMENT_FAILED,
        /** The charge was canceled. */
        PAYMENT_CANCELED,
        /** Anything else: it is logged and ignored. */
        OTHER
    }

    public PaymentGatewayEvent {
        if (id == null || id.isBlank() || type == null || kind == null) {
            throw new IllegalArgumentException("a notification needs its id, type and kind");
        }
    }

    /** Whether the charge was opened by InmoNode, and not by another app sharing the gateway account. */
    public boolean isOfThisApp() {
        return PaymentGateway.APP.equals(app);
    }
}
