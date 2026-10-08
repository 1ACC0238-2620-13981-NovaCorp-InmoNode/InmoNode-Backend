package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Verification of a payment evidence by the back office. Every evidence arrives {@code PENDING}; approving or
 * rejecting it belongs to the verification queue (US-55, US-56).
 */
public enum PaymentEvidenceStatus {
    PENDING
}
