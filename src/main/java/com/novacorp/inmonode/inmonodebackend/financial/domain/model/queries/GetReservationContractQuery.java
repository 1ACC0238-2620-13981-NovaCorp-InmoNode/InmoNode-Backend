package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import java.util.UUID;

/**
 * The contract of one of the calling buyer's reservations (US-21), or why there is none yet.
 *
 * @param transactionId id of the reservation shared by every context
 */
public record GetReservationContractQuery(UUID transactionId) {
}
