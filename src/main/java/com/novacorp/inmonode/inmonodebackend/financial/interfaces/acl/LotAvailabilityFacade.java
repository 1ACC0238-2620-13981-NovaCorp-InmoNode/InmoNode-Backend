package com.novacorp.inmonode.inmonodebackend.financial.interfaces.acl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Open Host Service ("LotAvailabilityPort" in 2.6.4): the only door Cotización y Separación Digital has to the lot
 * availability this context is the authority on. It exposes plain values only, never this context's aggregates.
 */
public interface LotAvailabilityFacade {

    /**
     * A lot buyers can evaluate, with its project's financing rules. Lots of draft projects are never offered.
     *
     * @return empty when there is no such lot or its project is not published
     */
    Optional<LotOffer> findLotOffer(Long lotId);

    /**
     * Blocks the lot for a buyer's web separation request for one hour (US-19), joining the caller's transaction so
     * the request and the block are stored together. Idempotent by {@code transactionId}.
     *
     * @param transactionId id of the separation request, shared by every context
     * @param initialAmount the down payment of the quotation the buyer accepted
     * @param currency      ISO 4217 code of the amount, the one of the lot price
     * @param termMonths    the term of the accepted quotation; with the rate, it becomes the reservation financing
     *                      plan, on the lot price at the moment of the block
     * @param annualInterestRate the rate of that quotation, as a percentage
     * @throws IllegalArgumentException when the amount is not positive, the currency is not an ISO code, or the term
     *                                  or the rate are out of range
     */
    LotBlock blockLot(UUID transactionId, Long lotId, Long buyerId, BigDecimal initialAmount, String currency,
                      int termMonths, BigDecimal annualInterestRate, Instant requestedAt,
                      BigDecimal agreedPrice, Long quotationId);

    LotBlock blockLot(UUID transactionId, Long lotId, Long buyerId, BigDecimal initialAmount, String currency,
                      int termMonths, BigDecimal annualInterestRate, Instant requestedAt);
}
