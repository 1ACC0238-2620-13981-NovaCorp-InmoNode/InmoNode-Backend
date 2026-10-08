package com.novacorp.inmonode.inmonodebackend.catalog.domain.model.valueobjects;

import java.util.List;

/**
 * Answer to a synchronization (US-32): the prospects stored or updated and one outcome per reservation, in the order
 * they were sent.
 */
public record FieldSyncResult(int prospectsSynced, List<ReservationSyncOutcome> reservations) {

    public FieldSyncResult {
        reservations = List.copyOf(reservations);
    }
}
