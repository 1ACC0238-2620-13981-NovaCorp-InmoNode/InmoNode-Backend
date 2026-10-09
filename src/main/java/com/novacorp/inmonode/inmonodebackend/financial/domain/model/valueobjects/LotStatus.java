package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Availability of a lot. A lot is loaded as {@code AVAILABLE}; the other states are reached through
 * reservations (block, payment verification) and the final sale.
 */
public enum LotStatus {
    DRAFT,
    AVAILABLE,
    BLOCKED,
    PENDING_VERIFICATION,
    RESERVED,
    SOLD
}
