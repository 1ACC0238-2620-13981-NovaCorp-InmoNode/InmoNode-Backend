package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Status of the charge of an installment opened in the payment gateway (US-55).
 */
public enum InstallmentPaymentIntentStatus {
    /** Waiting for the buyer to pay it in the gateway; the balance is untouched. */
    OPEN,
    /** The gateway confirmed the charge and the installment was credited with it. */
    SUCCEEDED,
    /**
     * The gateway confirmed the charge, but it no longer pays the installment: it was paid some other way, or its
     * late fee changed meanwhile. The finance back office is told, to refund it.
     */
    UNAPPLIED,
    /** Canceled in the gateway, or never opened there because the gateway failed. */
    CANCELED,
    /** Not paid within its time to live; a new start opens another one. */
    EXPIRED
}
