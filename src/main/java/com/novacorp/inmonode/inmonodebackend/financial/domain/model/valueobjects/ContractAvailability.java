package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Whether the buyer can read the contract of their reservation (US-21).
 */
public enum ContractAvailability {
    /** Issued by the back office: it can be read and downloaded (Scenario 1). */
    ISSUED,
    /** The payment was received or verified but the contract is not issued yet: "Contrato en elaboración" (Scenario 2). */
    IN_PREPARATION,
    /** The reservation still waits for its payment, or no longer holds the lot: there is no contract to expect. */
    NOT_AVAILABLE
}
