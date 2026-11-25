package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/** Where the request to sign a contract is on its way to the provider (US-56, Scenario 2). */
public enum SignatureDeliveryStatus {
    /** Waiting for its next attempt. */
    PENDING,
    /** The provider has it. */
    SENT,
    /** Every attempt failed; the back office was told and can send it again. */
    FAILED
}
