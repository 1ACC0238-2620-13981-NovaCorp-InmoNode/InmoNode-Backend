package com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries;

import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * The payment evidences of one of the caller's reservations (US-25): what was approved, what was rejected and why.
 *
 * @param transactionId id of the reservation shared by every context (its {@code sourceEventId})
 * @param pagination    the page of evidences to return (US-46)
 */
public record GetReservationPaymentsQuery(UUID transactionId, PageRequest pagination) {
    public GetReservationPaymentsQuery {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(pagination, "pagination");
    }
}
