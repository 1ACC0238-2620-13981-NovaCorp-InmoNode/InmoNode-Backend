package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;

import java.util.List;
import java.util.Set;

/**
 * Persistence abstraction for the {@link Lot} aggregate.
 */
public interface LotRepository {

    /**
     * @return the persisted lots, with their generated ids
     */
    List<Lot> saveAll(List<Lot> lots);

    /** Codes already used in the project, to reject duplicates before saving. */
    Set<String> findCodesByProjectId(Long projectId);
}
