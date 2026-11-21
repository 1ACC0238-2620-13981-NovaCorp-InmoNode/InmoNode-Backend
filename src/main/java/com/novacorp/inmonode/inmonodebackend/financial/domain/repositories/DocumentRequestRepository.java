package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence abstraction for the {@link DocumentRequest} aggregate.
 */
public interface DocumentRequestRepository {

    /** Inserts a new request, or writes the outcome of an existing one. */
    void save(DocumentRequest request);

    Optional<DocumentRequest> findById(UUID id);
}
