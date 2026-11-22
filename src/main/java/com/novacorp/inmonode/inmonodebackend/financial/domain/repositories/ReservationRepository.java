package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.ReservationEvidence;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageRequest;
import com.novacorp.inmonode.inmonodebackend.shared.domain.model.valueobjects.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence abstraction for the {@link Reservation} aggregate.
 */
public interface ReservationRepository {

    /**
     * @return the persisted reservation, with its generated id
     */
    Reservation save(Reservation reservation);

    Optional<Reservation> findById(Long id);

    /** Refreshes and locks a reservation after its lot lock was acquired. */
    Optional<Reservation> findByIdForUpdate(Long id);

    /** The reservation generated on a field device with this id, to recognize a re-send (idempotency). */
    Optional<Reservation> findBySourceEventId(UUID sourceEventId);

    /** The reservation one of whose payment evidences has this id. */
    Optional<Reservation> findByEvidenceId(Long evidenceId);

    /**
     * One page of the evidences waiting for the back office's decision, oldest first with an id tie-breaker, each
     * with its reservation (US-46). The database reads only that page of evidences, the reservations they belong to
     * and the count of every pending evidence.
     */
    PageResult<ReservationEvidence> findPendingEvidences(PageRequest pagination);
}
