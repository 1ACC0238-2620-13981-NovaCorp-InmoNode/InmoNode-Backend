package com.novacorp.inmonode.inmonodebackend.catalog.interfaces.rest.resources;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param prospectsSynced prospects stored or updated
 * @param reservations    one result per reservation, in the order they were sent
 */
public record FieldSyncResultResource(int prospectsSynced, List<ReservationResultResource> reservations) {

    /**
     * @param result         SYNCED (mark as synchronized), CONFLICT (revert the lot and alert the agent) or
     *                       DUPLICATE (already processed; see {@code originalResult})
     * @param blockedUntil   until when the lot is held for this reservation
     * @param conflictReason LOT_UNAVAILABLE or LOT_NOT_FOUND
     */
    public record ReservationResultResource(UUID id, String result, String reservationStatus, Instant blockedUntil,
                                            String conflictReason, String originalResult) {
    }
}
