package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

/**
 * Life cycle of a reservation. {@code PENDING_SYNC} exists only on the field device; on the server a reservation
 * starts {@code BLOCKED} (it holds the lot) or {@code CANCELLED_BY_CONFLICT} (the lot was already taken).
 */
public enum ReservationStatus {
    PENDING_SYNC,
    BLOCKED,
    PENDING_VERIFICATION,
    VERIFIED,
    REJECTED,
    EXPIRED,
    CANCELLED_BY_CONFLICT
}
