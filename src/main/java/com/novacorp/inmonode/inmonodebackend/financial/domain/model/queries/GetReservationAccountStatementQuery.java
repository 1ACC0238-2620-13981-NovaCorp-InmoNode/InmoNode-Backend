package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import java.util.UUID;

/**
 * The account statement of one of the calling buyer's reservations (US-23).
 *
 * @param transactionId id of the reservation shared by every context
 */
public record GetReservationAccountStatementQuery(UUID transactionId) {
}
