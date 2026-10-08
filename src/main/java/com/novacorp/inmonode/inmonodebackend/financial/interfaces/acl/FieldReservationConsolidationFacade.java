package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entry point the field context uses to consolidate the reservations its agents made offline (Customer/Supplier
 * in the Context Map: this context decides availability, the field context turns the answer into what the device
 * shows). It exposes plain values only, never this context's aggregates.
 */
public interface FieldReservationConsolidationFacade {

    /**
     * Consolidates one reservation in its own transaction, so a conflict never undoes the others of the same sync.
     *
     * @param reservationId id the device generated for the reservation; a re-send carries the same one
     * @param prospectId    id the device generated for the prospect
     * @param initialAmount positive amount in soles
     * @throws IllegalArgumentException when the amount is not positive
     */
    FieldReservationConsolidation consolidate(UUID reservationId, Long lotId, Long agentId, UUID prospectId,
                                              BigDecimal initialAmount, Instant reservedAt);
}
