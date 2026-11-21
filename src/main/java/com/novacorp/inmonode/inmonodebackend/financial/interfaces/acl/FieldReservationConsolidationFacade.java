package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entry point the field context uses to consolidate the reservations its agents made offline (Customer/Supplier
 * in the Context Map: this context decides availability, the field context turns the answer into what the device
 * shows). It exposes plain values only, never this context's aggregates.
 */
public interface FieldReservationConsolidationFacade {

    /**
     * Price in soles of each existing lot among {@code ids}, so the field context can check a sync before storing
     * anything. A lot that does not exist is left out.
     */
    Map<Long, BigDecimal> lotPrices(Set<Long> ids);

    /**
     * Consolidates a reservation, joining the sync transaction. Availability conflicts are returned as data.
     *
     * @param reservationId    id the device generated for the reservation; a re-send carries the same one
     * @param prospectId       id the device generated for the prospect
     * @param initialAmount    positive amount in soles
     * @param discountAmount   optional discount on the lot price agreed in the field (US-58), in soles
     * @param bonus            optional bonus agreed in the field
     * @param negotiationNotes optional notes of the negotiation
     * @throws IllegalArgumentException when the amount is not positive or the terms are out of their limits
     */
    FieldReservationConsolidation consolidate(UUID reservationId, Long lotId, Long agentId, UUID prospectId,
                                              BigDecimal initialAmount, Instant reservedAt,
                                              @Nullable BigDecimal discountAmount, @Nullable String bonus,
                                              @Nullable String negotiationNotes);

    /** A reservation without negotiation terms. */
    default FieldReservationConsolidation consolidate(UUID reservationId, Long lotId, Long agentId, UUID prospectId,
                                                      BigDecimal initialAmount, Instant reservedAt) {
        return consolidate(reservationId, lotId, agentId, prospectId, initialAmount, reservedAt, null, null, null);
    }
}
