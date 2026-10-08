package com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.Money;

import java.time.Instant;
import java.util.UUID;

/**
 * Blocks a lot for a buyer's separation request from the web portal (US-19).
 *
 * @param sourceEventId the request's transaction id, shared by every context; a retry carries the same one
 * @param initialAmount the down payment of the quotation the buyer accepted
 */
public record RequestWebReservationCommand(UUID sourceEventId, Long lotId, Long buyerId, Money initialAmount,
                                           Instant requestedAt) {
}
