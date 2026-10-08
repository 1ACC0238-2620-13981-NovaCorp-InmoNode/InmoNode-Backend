package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Reservation;

import java.util.List;
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

    /** The reservation generated on a field device with this id, to recognize a re-send (idempotency). */
    Optional<Reservation> findBySourceEventId(UUID sourceEventId);

    /** The reservation one of whose payment evidences has this id. */
    Optional<Reservation> findByEvidenceId(Long evidenceId);

    /** Reservations with at least one evidence waiting for the back office's decision. */
    List<Reservation> findWithPendingEvidence();
}
