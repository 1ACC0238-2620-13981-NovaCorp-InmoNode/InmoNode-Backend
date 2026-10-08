package com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects;

import java.time.Duration;

/**
 * Where a reservation comes from. Each channel holds the lot for a different time while the payment
 * evidence arrives (Lot Block): 24 hours from the field, 1 hour from the web portal.
 */
public enum ReservationChannel {
    FIELD(Duration.ofHours(24)),
    WEB(Duration.ofHours(1));

    private final Duration blockValidity;

    ReservationChannel(Duration blockValidity) {
        this.blockValidity = blockValidity;
    }

    public Duration blockValidity() {
        return blockValidity;
    }
}
