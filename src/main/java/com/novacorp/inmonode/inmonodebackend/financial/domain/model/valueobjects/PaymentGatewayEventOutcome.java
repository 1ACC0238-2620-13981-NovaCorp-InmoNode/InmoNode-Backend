package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * What was done with an authentic notification of the payment gateway (US-55, Scenario 2).
 */
public enum PaymentGatewayEventOutcome {
    /** The charge was confirmed and the installment credited with it. */
    CREDITED,
    /** The charge had already been confirmed by an earlier notification: nothing was credited again. */
    DUPLICATE,
    /** The charge was confirmed, but the installment was no longer owed that amount: finance was told to refund it. */
    UNAPPLIED,
    /** The gateway does not show the charge as the notification says (status, amount or currency): nothing changed. */
    REJECTED,
    /** Not about a charge of an installment of this environment: another app, another type or an unknown charge. */
    IGNORED,
    /** An attempt was declined; the charge stays open for the buyer to try again. */
    FAILED,
    /** The charge was canceled in the gateway. */
    CANCELED
}
