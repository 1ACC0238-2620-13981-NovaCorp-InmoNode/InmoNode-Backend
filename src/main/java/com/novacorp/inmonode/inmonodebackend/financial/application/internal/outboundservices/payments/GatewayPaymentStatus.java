package com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.payments;

/**
 * Where a charge is in the payment gateway.
 */
public enum GatewayPaymentStatus {
    /** Waiting for the buyer: a payment method, a confirmation or an authentication. */
    PENDING,
    /** Paid, and the gateway is still settling it; it cannot be canceled. */
    PROCESSING,
    /** Charged: the money moved. */
    SUCCEEDED,
    /** Canceled: it can no longer be paid. */
    CANCELED
}
