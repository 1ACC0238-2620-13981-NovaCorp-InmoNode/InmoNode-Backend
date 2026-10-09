package com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * What the device does with one synchronized reservation (US-11, US-12): mark it synchronized, or revert the lot
 * and alert the agent about the conflict, keeping the prospect to offer another lot.
 *
 * @param reservationId     id the device generated for the reservation
 * @param reservationStatus status of the reservation on the server, e.g. {@code BLOCKED}
 * @param blockedUntil      until when the lot is held; {@code null} when the reservation does not hold it
 * @param conflictReason    {@code LOT_UNAVAILABLE} or {@code LOT_NOT_FOUND}; {@code null} unless it is a conflict
 * @param originalResult    for a re-send, what the first synchronization answered
 */
public record ReservationSyncOutcome(UUID reservationId, Result result, @Nullable String reservationStatus,
                                     @Nullable Instant blockedUntil, @Nullable String conflictReason,
                                     @Nullable Result originalResult) {

    public enum Result {
        SYNCED,
        CONFLICT,
        DUPLICATE
    }
}
