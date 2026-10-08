package com.novacorp.inmonode.inmonodebackend.financial.domain.repositories;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Lot;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.LotStatistics;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    long countByProjectId(Long projectId);

    /** Lots of the project ordered by code. */
    List<Lot> findByProjectId(Long projectId);

    /**
     * Inventory figures per project, computed in the database. Projects without lots are absent from the map.
     */
    Map<Long, LotStatistics> summarizeByProjectIds(Collection<Long> projectIds);
}
