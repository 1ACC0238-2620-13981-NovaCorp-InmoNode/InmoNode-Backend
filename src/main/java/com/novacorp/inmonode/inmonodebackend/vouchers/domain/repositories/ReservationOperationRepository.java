package com.novacorp.inmonode.inmonodebackend.vouchers.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.vouchers.domain.model.aggregates.ReservationOperation;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence abstraction for the {@link ReservationOperation} aggregate.
 */
public interface ReservationOperationRepository {

    ReservationOperation save(ReservationOperation operation);

    Optional<ReservationOperation> findByReservationId(UUID reservationId);

    boolean existsByReservationId(UUID reservationId);
}
