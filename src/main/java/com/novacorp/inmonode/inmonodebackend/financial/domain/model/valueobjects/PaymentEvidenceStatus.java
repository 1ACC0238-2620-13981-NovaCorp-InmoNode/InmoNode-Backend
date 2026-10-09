package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Verification of a payment evidence by the back office. Every evidence arrives {@code PENDING}; the back office
 * approves it, or rejects it with a reason so the requester can send a substitute (US-25).
 */
public enum PaymentEvidenceStatus {
    PENDING,
    APPROVED,
    REJECTED
}
