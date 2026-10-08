package com.novacorp.inmonode.inmonodebackend.quoting.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.quoting.domain.model.aggregates.SeparationRequest;

import java.util.Optional;

/**
 * Persistence abstraction for the {@link SeparationRequest} aggregate.
 */
public interface SeparationRequestRepository {

    /**
     * @return the persisted request, with its generated id
     */
    SeparationRequest save(SeparationRequest request);

    /** The buyer's most recent blocked request for the lot, active or not. */
    Optional<SeparationRequest> findLatestBlocked(Long buyerId, Long lotId);
}
