package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import java.util.UUID;

/**
 * The payment evidences of one of the caller's reservations (US-25): what was approved, what was rejected and why.
 *
 * @param transactionId id of the reservation shared by every context (its {@code sourceEventId})
 */
public record GetReservationPaymentsQuery(UUID transactionId) {
}
