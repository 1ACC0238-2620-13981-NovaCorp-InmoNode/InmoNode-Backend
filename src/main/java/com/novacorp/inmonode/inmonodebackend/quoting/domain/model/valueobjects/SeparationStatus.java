package com.novacorp.inmonode.inmonodebackend.quoting.domain.model.valueobjects;

/**
 * Outcome of a separation request from this context's point of view. The request is decided at once, so it is never
 * stored as merely requested; what happens to the reservation afterwards (payment, verification, expiry) belongs to
 * Control Financiero y Documental.
 */
public enum SeparationStatus {
    /** The lot is held for the buyer for one hour while the payment evidence arrives. */
    BLOCKED,
    /** Another operation took the lot first (US-19, Scenario 2). */
    REJECTED_UNAVAILABLE
}
